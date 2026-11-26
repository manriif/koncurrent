/**
 * Copyright (C) 2026 Maanrifa Bacar Ali
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
/*
 * Part of the port of the ConcurrentHashMap of JSR-166, which was written by Doug Lea with
 * assistance from members of JCP JSR-166 Expert Group and released to the public domain, as
 * explained at http://creativecommons.org/publicdomain/zero/1.0/
 */
package koncurrent.internal

import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicInt

// The bin count under which a bin split by a resize goes back to a list
private const val UNTREEIFY_THRESHOLD = 6

// Values of the lock state
private const val WRITER = 1 // Set while holding the write lock
private const val WAITER = 2 // Set while waiting for the write lock
private const val READER = 4 // Increment for each read lock

/**
 * Heads a bin whose nodes are arranged in a red-black tree, ordered by hash then by `compareTo`
 * when the keys allow it. The nodes also stay linked as a list.
 *
 * Writers hold the lock of the bin. It does not stop lookups, so a read-write lock guards the
 * tree on top of it. A writer waits for the running lookups before it restructures the tree.
 * Lookups never wait. They follow the list while a writer holds or awaits the write lock.
 */
internal class TreeBin<K : Any, V : Any>(first: TreeNode<K, V>?) : Bin<K, V> {

    @Volatile
    var root: TreeNode<K, V>? = treeify(first)
        private set

    @Volatile
    var first: TreeNode<K, V>? = first
        private set

    private val lockState = AtomicInt(0)

    /**
     * Returns the node holding [key], or `null`. Searches the tree, or the list while a writer
     * holds or awaits the write lock.
     */
    fun find(hash: Int, key: K): TreeNode<K, V>? {
        var node = first

        while (node != null) {
            val state = lockState.load()

            if ((state and (WAITER or WRITER)) != 0) {
                if (node.matches(hash, key)) {
                    return node
                }

                node = node.next
            } else if (lockState.compareAndSet(state, state + READER)) {
                try {
                    return root?.findTreeNode(hash, key)
                } finally {
                    val _ = lockState.addAndFetch(-READER)
                }
            }
        }

        return null
    }

    /**
     * Returns the node holding [key], or `null`. The caller holds the lock of the bin.
     */
    fun findLocked(hash: Int, key: K): TreeNode<K, V>? = root?.findTreeNode(hash, key)

    /**
     * Finds the node holding [key], or adds one. Returns `null` when it added the node.
     */
    @IgnorableReturnValue
    fun putTreeVal(hash: Int, key: K, value: V): TreeNode<K, V>? {
        val root = root

        if (root == null) {
            val node = TreeNode(hash, key, value, null, null)

            this.root = node
            first = node

            return null
        }

        var searched = false
        var parent: TreeNode<K, V> = root

        while (true) {
            if (parent.matches(hash, key)) {
                return parent
            }

            val direction = direction(hash, key, parent)

            // Tied and not ordered. The key may be on either side.
            if (direction == 0 && !searched) {
                searched = true

                val found = parent.left?.findTreeNode(hash, key)
                    ?: parent.right?.findTreeNode(hash, key)

                if (found != null) {
                    return found
                }
            }

            val child = parent.child(left = direction <= 0)

            if (child == null) {
                addLeaf(root, parent, TreeNode(hash, key, value, first, parent), direction <= 0)
                return null
            }

            parent = child
        }
    }

    /**
     * Removes [node], which must be in the bin. Returns `true` when the bin is now too small for
     * a tree and should go back to a list.
     */
    fun removeTreeNode(node: TreeNode<K, V>): Boolean {
        val next = node.next
        val pred = node.prev

        // Unlinks the traversal pointers
        if (pred == null) {
            first = next
        } else {
            pred.next = next
        }

        next?.prev = pred

        if (first == null) {
            root = null
            return true
        }

        val root = root

        if (root?.right == null || root.left?.left == null) {
            return true
        }

        restructure { this.root = unlink(root, node) }
        return false
    }

    /**
     * Returns the nodes of this bin as a list.
     */
    fun untreeify(): ListNode<K, V>? = untreeify(first)

    private fun addLeaf(
        root: TreeNode<K, V>,
        parent: TreeNode<K, V>,
        leaf: TreeNode<K, V>,
        left: Boolean,
    ) {
        val first = first

        this.first = leaf
        first?.prev = leaf
        parent.setChild(left, leaf)

        if (!parent.red) {
            leaf.red = true
        } else {
            restructure { this.root = balanceInsertion(root, leaf) }
        }
    }

    /**
     * Runs [block] with the write lock held, once the running lookups ended.
     */
    private inline fun restructure(block: () -> Unit) {
        if (!lockState.compareAndSet(0, WRITER)) {
            contendedLock()
        }

        try {
            block()
        } finally {
            lockState.store(0)
        }
    }

    private fun contendedLock() {
        while (true) {
            val state = lockState.load()

            if ((state and WAITER.inv()) == 0) {
                if (lockState.compareAndSet(state, WRITER)) {
                    return
                }
            } else if ((state and WAITER) == 0) {
                // Sends the next lookups to the list. Then spins until the running ones end.
                val _ = lockState.compareAndSet(state, state or WAITER)
            }
        }
    }
}

/**
 * A mapping of a bin arranged as a tree.
 */
internal class TreeNode<K : Any, V : Any>(
    hash: Int,
    key: K,
    value: V,
    @Volatile override var next: TreeNode<K, V>?,
    var parent: TreeNode<K, V>?,
) : Node<K, V>(hash, key, value) {

    // Volatile because a writer links a new leaf while lookups search the tree
    @Volatile
    var left: TreeNode<K, V>? = null

    @Volatile
    var right: TreeNode<K, V>? = null

    var prev: TreeNode<K, V>? = null // Needed to unlink next upon deletion
    var red = false

    fun child(left: Boolean): TreeNode<K, V>? = if (left) this.left else right

    fun setChild(left: Boolean, child: TreeNode<K, V>?) {
        if (left) {
            this.left = child
        } else {
            right = child
        }
    }

    /**
     * Puts [child] in the place of [old], which is a child of this node.
     */
    fun replaceChild(old: TreeNode<K, V>, child: TreeNode<K, V>?): Unit =
        setChild(left === old, child)

    /**
     * Returns the node holding [key] in the tree rooted at this node, or `null`.
     */
    fun findTreeNode(hash: Int, key: K): TreeNode<K, V>? {
        var node: TreeNode<K, V>? = this

        while (node != null) {
            val left = node.left
            val right = node.right

            @Suppress("SuspiciousEqualsCombination")
            when {
                node.hash > hash -> node = left
                node.hash < hash -> node = right
                node.key === key || key == node.key -> return node
                left == null -> node = right
                right == null -> node = left

                else -> {
                    val order = compareComparables(key, node.key)

                    if (order != 0) {
                        node = if (order < 0) left else right
                    } else {
                        // Tied and not ordered. Searches both sides.
                        val found = right.findTreeNode(hash, key)

                        if (found != null) {
                            return found
                        }

                        node = left
                    }
                }
            }
        }

        return null
    }
}

/**
 * Copies of nodes linked as a list of tree nodes, in the making of a bin.
 */
internal class TreeNodeList<K : Any, V : Any> {

    private var first: TreeNode<K, V>? = null
    private var last: TreeNode<K, V>? = null

    var size = 0
        private set

    fun add(node: Node<K, V>) {
        val copy = TreeNode(node.hash, node.key, node.value, null, null)
        val last = last

        copy.prev = last

        if (last == null) {
            first = copy
        } else {
            last.next = copy
        }

        this.last = copy
        size++
    }

    fun toTreeBin(): TreeBin<K, V> = TreeBin(first)

    /**
     * Returns the bin of these nodes, which a resize split from [source] along with [other].
     * It is a list when they are too few for a tree. It is [source] itself when they are all
     * its nodes.
     */
    fun toBin(source: TreeBin<K, V>, other: TreeNodeList<K, V>): Bin<K, V>? = when {
        size <= UNTREEIFY_THRESHOLD -> untreeify(first)
        other.size == 0 -> source
        else -> toTreeBin()
    }
}

///////////////////////////////////////////////////////////////////////////
// Ordering
///////////////////////////////////////////////////////////////////////////

/**
 * Returns the side of [node] for a mapping of [hash] and [key]. Negative or zero is left, so
 * the ties that cannot be ordered go left. Any consistent rule works for them, since lookups
 * search both sides of a tie.
 */
private fun <K : Any, V : Any> direction(hash: Int, key: K, node: TreeNode<K, V>): Int = when {
    node.hash > hash -> -1
    node.hash < hash -> 1
    else -> compareComparables(key, node.key)
}

/**
 * Returns the order of [key] against [other] when both are of the same comparable class, else
 * zero. Unlike the original, it cannot check that the class is comparable to itself.
 */
@Suppress("UNCHECKED_CAST")
private fun compareComparables(key: Any, other: Any): Int {
    if (key !is Comparable<*> || key::class != other::class) {
        return 0
    }

    return (key as Comparable<Any>).compareTo(other)
}

///////////////////////////////////////////////////////////////////////////
// Conversion
///////////////////////////////////////////////////////////////////////////

/**
 * Arranges the nodes starting at [first] as a tree and returns its root.
 */
private fun <K : Any, V : Any> treeify(first: TreeNode<K, V>?): TreeNode<K, V>? {
    var root: TreeNode<K, V>? = null
    var node = first

    while (node != null) {
        val next = node.next

        root = insert(root, node)
        node = next
    }

    return root
}

/**
 * Returns copies of the nodes starting at [first], linked as a list bin.
 */
private fun <K : Any, V : Any> untreeify(first: TreeNode<K, V>?): ListNode<K, V>? {
    var head: ListNode<K, V>? = null
    var tail: ListNode<K, V>? = null
    var node = first

    while (node != null) {
        val copy = ListNode(node.hash, node.key, node.value, null)

        if (tail == null) {
            head = copy
        } else {
            tail.next = copy
        }

        tail = copy
        node = node.next
    }

    return head
}

///////////////////////////////////////////////////////////////////////////
// Red-black tree, adapted from CLR
///////////////////////////////////////////////////////////////////////////

/**
 * Adds [node] to the tree of [root], which holds no key equal to its key. Returns the new root.
 */
private fun <K : Any, V : Any> insert(
    root: TreeNode<K, V>?,
    node: TreeNode<K, V>,
): TreeNode<K, V> {
    node.left = null
    node.right = null

    if (root == null) {
        node.parent = null
        node.red = false

        return node
    }

    var parent: TreeNode<K, V> = root

    while (true) {
        val left = direction(node.hash, node.key, parent) <= 0
        val child = parent.child(left)

        if (child == null) {
            node.parent = parent
            parent.setChild(left, node)

            return balanceInsertion(root, node)
        }

        parent = child
    }
}

/**
 * Rotates [node] down to the left or to the right. Its child of the other side takes its place.
 * Returns the new root.
 */
private fun <K : Any, V : Any> rotate(
    root: TreeNode<K, V>,
    node: TreeNode<K, V>,
    left: Boolean,
): TreeNode<K, V> {
    val child = node.child(!left) ?: return root
    val grandchild = child.child(left)
    val parent = node.parent
    var newRoot = root

    node.setChild(!left, grandchild)
    grandchild?.parent = node
    child.parent = parent

    if (parent == null) {
        newRoot = child
        child.red = false
    } else {
        parent.replaceChild(node, child)
    }

    child.setChild(left, node)
    node.parent = child

    return newRoot
}

private fun <K : Any, V : Any> balanceInsertion(
    root: TreeNode<K, V>,
    node: TreeNode<K, V>,
): TreeNode<K, V> {
    var newRoot = root
    var x = node

    x.red = true

    while (true) {
        var parent = x.parent

        if (parent == null) {
            x.red = false
            return x
        }

        var grandparent = parent.parent

        if (!parent.red || grandparent == null) {
            return newRoot
        }

        val left = parent === grandparent.left
        val uncle = grandparent.child(!left)

        if (uncle != null && uncle.red) {
            uncle.red = false
            parent.red = false
            grandparent.red = true
            x = grandparent
        } else {
            if (x === parent.child(!left)) {
                x = parent
                newRoot = rotate(newRoot, x, left)
                parent = x.parent
                grandparent = parent?.parent
            }

            if (parent != null) {
                parent.red = false

                if (grandparent != null) {
                    grandparent.red = true
                    newRoot = rotate(newRoot, grandparent, !left)
                }
            }
        }
    }
}

private fun <K : Any, V : Any> balanceDeletion(
    root: TreeNode<K, V>,
    node: TreeNode<K, V>,
): TreeNode<K, V> {
    var newRoot = root
    var x: TreeNode<K, V>? = node

    while (true) {
        if (x == null || x === newRoot) {
            return newRoot
        }

        var parent = x.parent

        if (parent == null) {
            x.red = false
            return x
        }

        if (x.red) {
            x.red = false
            return newRoot
        }

        val left = parent.left === x
        var sibling = parent.child(!left)

        if (sibling != null && sibling.red) {
            sibling.red = false
            parent.red = true
            newRoot = rotate(newRoot, parent, left)
            parent = x.parent
            sibling = parent?.child(!left)
        }

        if (sibling == null) {
            x = parent
            continue
        }

        val near = sibling.child(left)
        val far = sibling.child(!left)

        if ((far == null || !far.red) && (near == null || !near.red)) {
            sibling.red = true
            x = parent
            continue
        }

        if (far == null || !far.red) {
            near?.red = false
            sibling.red = true
            newRoot = rotate(newRoot, sibling, !left)
            parent = x.parent
            sibling = parent?.child(!left)
        }

        if (sibling != null) {
            sibling.red = parent != null && parent.red
            sibling.child(!left)?.red = false
        }

        if (parent != null) {
            parent.red = false
            newRoot = rotate(newRoot, parent, left)
        }

        x = newRoot
    }
}

/**
 * Unlinks [node] from the tree of [root] and rebalances it. Returns the new root.
 */
private fun <K : Any, V : Any> unlink(root: TreeNode<K, V>, node: TreeNode<K, V>): TreeNode<K, V> {
    var newRoot = root
    val left = node.left
    val right = node.right

    if (left != null && right != null) {
        newRoot = swapWithSuccessor(newRoot, node, left, right)
    }

    val replacement = node.left ?: node.right ?: node

    if (replacement !== node) {
        val parent = node.parent

        replacement.parent = parent

        if (parent == null) {
            newRoot = replacement
        } else {
            parent.replaceChild(node, replacement)
        }

        node.left = null
        node.right = null
        node.parent = null
    }

    if (!node.red) {
        newRoot = balanceDeletion(newRoot, replacement)
    }

    if (replacement === node) {
        // A leaf stays linked until the tree is balanced
        node.parent?.replaceChild(node, null)
        node.parent = null
    }

    return newRoot
}

/**
 * Swaps the places of [node] and its successor in the tree. Returns the new root.
 *
 * Swapping their contents is not an option. The next pointers pin the successor, and lookups
 * follow them without any lock.
 */
private fun <K : Any, V : Any> swapWithSuccessor(
    root: TreeNode<K, V>,
    node: TreeNode<K, V>,
    left: TreeNode<K, V>,
    right: TreeNode<K, V>,
): TreeNode<K, V> {
    var successor = right

    while (true) {
        successor = successor.left ?: break
    }

    val red = successor.red
    val successorRight = successor.right
    val parent = node.parent

    successor.red = node.red
    node.red = red

    if (successor === right) {
        node.parent = successor
        successor.right = node
    } else {
        val successorParent = successor.parent

        node.parent = successorParent
        successorParent?.replaceChild(successor, node)
        successor.right = right
        right.parent = successor
    }

    node.left = null
    node.right = successorRight
    successorRight?.parent = node
    successor.left = left
    left.parent = successor
    successor.parent = parent

    if (parent == null) {
        return successor
    }

    parent.replaceChild(node, successor)
    return root
}
