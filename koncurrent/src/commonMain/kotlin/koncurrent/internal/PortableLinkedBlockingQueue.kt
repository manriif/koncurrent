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
 * Ported from the LinkedBlockingQueue of JSR-166, which was written by Doug Lea with assistance
 * from members of JCP JSR-166 Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package koncurrent.internal

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.fetchAndDecrement
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.concurrent.atomics.plusAssign

/**
 * A blocking queue on linked nodes, a port of the `LinkedBlockingQueue` of JSR-166. It backs
 * the public queue where the platform has none.
 *
 * It is a "two lock queue". The threads that add take one lock, which guards the tail. The
 * threads that take use another one, which guards the head. So both ends work at the same
 * time. The two sides only share the count, which is atomic.
 *
 * A thread that adds wakes up a waiting taker only when the queue was empty. A taker that
 * leaves elements behind wakes up the next one. The same holds the other way, for the threads
 * that wait for room. So an addition seldom needs the lock of the takers.
 *
 * Removing an element that is not the head, and iterating, take both locks.
 *
 * A node that leaves the queue through its head gets linked to itself. An iterator that stands
 * on it then knows to go on from the head. A node removed from the middle keeps its link, so
 * that an iterator that stands on it can go on.
 *
 * One thing differs from the original. `removeAll` and `retainAll` remove through the
 * iterator, one element at a time. The original works on batches of elements.
 */
internal class PortableLinkedBlockingQueue<E : Any>(
    private val capacity: Int,
) : PortableBlockingQueue<E>() {

    private val count = AtomicInt(0)

    // Its item is always null. The first element is in the node after it.
    private var head = Node<E>(null)

    // Its next is always null
    private var last = head

    private val takeLock = InternalLock()
    private val notEmpty = takeLock.newCondition()

    private val putLock = InternalLock()
    private val notFull = putLock.newCondition()

    override val size: Int
        get() = count.load()

    init {
        require(capacity > 0) { "capacity must be positive, was $capacity" }
    }

    constructor() : this(Int.MAX_VALUE)

    constructor(elements: Collection<E>) : this() {
        // Never contended. The lock makes the nodes visible to the other threads.
        putLock.locked {
            for (element in elements) {
                enqueue(Node(element))
                count += 1
            }
        }
    }

    override fun remainingCapacity(): Int = capacity - count.load()

    ///////////////////////////////////////////////////////////////////////////
    // Adding
    ///////////////////////////////////////////////////////////////////////////

    override fun put(element: E) {
        val node = Node(element)

        val before = putLock.lockedInterruptibly {
            // The count is read without the lock of the takers. Only they change it now. They
            // lower it, then signal this thread.
            while (count.load() == capacity) {
                notFull.await()
            }

            enqueue(node)
            countAdded()
        }

        if (before == 0) {
            signalNotEmpty()
        }
    }

    override fun offer(element: E, nanos: Long): Boolean {
        var left = nanos

        val before = putLock.lockedInterruptibly {
            while (count.load() == capacity) {
                if (left <= 0L) {
                    return false
                }

                left = notFull.awaitRemaining(left)
            }

            enqueue(Node(element))
            countAdded()
        }

        if (before == 0) {
            signalNotEmpty()
        }

        return true
    }

    override fun offer(element: E): Boolean {
        if (count.load() == capacity) {
            return false
        }

        val node = Node(element)

        val before = putLock.locked {
            if (count.load() == capacity) {
                return false
            }

            enqueue(node)
            countAdded()
        }

        if (before == 0) {
            signalNotEmpty()
        }

        return true
    }

    // Links the node at the tail. To call with the put lock held.
    private fun enqueue(node: Node<E>) {
        last.next = node
        last = node
    }

    // Counts an added element, with the put lock held. Returns the count from before.
    private fun countAdded(): Int {
        val before = count.fetchAndIncrement()

        // There is room left, which the next thread that waits for some can take
        if (before + 1 < capacity) {
            notFull.signal()
        }

        return before
    }

    private fun signalNotEmpty() {
        takeLock.locked { notEmpty.signal() }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Taking
    ///////////////////////////////////////////////////////////////////////////

    override fun take(): E {
        var before = 0

        val item = takeLock.lockedInterruptibly {
            while (count.load() == 0) {
                notEmpty.await()
            }

            val item = dequeue()

            before = countTaken()
            item
        }

        if (before == capacity) {
            signalNotFull()
        }

        return item
    }

    override fun poll(nanos: Long): E? {
        var left = nanos
        var before = 0

        val item = takeLock.lockedInterruptibly {
            while (count.load() == 0) {
                if (left <= 0L) {
                    return null
                }

                left = notEmpty.awaitRemaining(left)
            }

            val item = dequeue()

            before = countTaken()
            item
        }

        if (before == capacity) {
            signalNotFull()
        }

        return item
    }

    override fun poll(): E? {
        if (count.load() == 0) {
            return null
        }

        var before = 0

        val item = takeLock.locked {
            if (count.load() == 0) {
                return null
            }

            val item = dequeue()

            before = countTaken()
            item
        }

        if (before == capacity) {
            signalNotFull()
        }

        return item
    }

    override fun peek(): E? {
        if (count.load() == 0) {
            return null
        }

        return takeLock.locked { if (count.load() > 0) head.next?.item else null }
    }

    // Unlinks the first element and returns it. To call with the take lock held, when the
    // count is not zero.
    private fun dequeue(): E {
        val old = head
        val first = checkNotNull(old.next)
        val item = checkNotNull(first.item)

        // Tells an iterator that stands on the old head to go on from the new one
        old.next = old
        head = first
        first.item = null

        return item
    }

    // Counts a taken element, with the take lock held. Returns the count from before.
    private fun countTaken(): Int {
        val before = count.fetchAndDecrement()

        // Elements are left, which the next thread that waits for one can take
        if (before > 1) {
            notEmpty.signal()
        }

        return before
    }

    private fun signalNotFull() {
        putLock.locked { notFull.signal() }
    }

    override fun drainTo(collection: MutableCollection<in E>, maxElements: Int): Int {
        require(collection !== this) { DRAINED_TO_ITSELF }

        if (maxElements <= 0) {
            return 0
        }

        var wasFull = false

        try {
            return takeLock.locked {
                // The count tells how many nodes this thread is sure to see
                val drained = minOf(maxElements, count.load())
                var node = head
                var moved = 0

                try {
                    while (moved < drained) {
                        val next = checkNotNull(node.next)

                        collection.add(checkNotNull(next.item))
                        next.item = null
                        node.next = node
                        node = next
                        moved++
                    }

                    drained
                } finally {
                    // Leaves the queue in order even when the collection threw
                    if (moved > 0) {
                        head = node
                        wasFull = count.fetchAndAdd(-moved) == capacity
                    }
                }
            }
        } finally {
            if (wasFull) {
                signalNotFull()
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Both ends
    ///////////////////////////////////////////////////////////////////////////

    override fun contains(element: E): Boolean {
        return fullyLocked {
            var node = head.next

            while (node != null) {
                if (element == node.item) {
                    return true
                }

                node = node.next
            }

            false
        }
    }

    override fun remove(element: E): Boolean {
        return fullyLocked {
            var pred = head
            var node = pred.next

            while (node != null) {
                if (element == node.item) {
                    unlink(node, pred)
                    return true
                }

                pred = node
                node = node.next
            }

            false
        }
    }

    override fun clear() {
        fullyLocked {
            var node = head
            var next = node.next

            while (next != null) {
                node.next = node
                next.item = null
                node = next
                next = node.next
            }

            head = last

            if (count.exchange(0) == capacity) {
                notFull.signal()
            }
        }
    }

    // Removes a node whose predecessor is known, with both locks held
    private fun unlink(node: Node<E>, pred: Node<E>) {
        // The node keeps its link, for the iterators that stand on it
        node.item = null
        pred.next = node.next

        if (last === node) {
            last = pred
        }

        if (count.fetchAndDecrement() == capacity) {
            notFull.signal()
        }
    }

    // Runs the action with both locks held, which keeps both ends still
    private inline fun <T> fullyLocked(action: () -> T): T {
        putLock.lock()
        takeLock.lock()

        try {
            return action()
        } finally {
            takeLock.unlock()
            putLock.unlock()
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Iteration
    ///////////////////////////////////////////////////////////////////////////

    override fun iterator(): MutableIterator<E> = LinkIterator()

    // Returns the node after the given one. A node linked to itself left the queue through its
    // head, and the first element is then the one to go on with.
    private fun successor(node: Node<E>): Node<E>? {
        val next = node.next

        return if (next === node) head.next else next
    }

    // Returns the predecessor of a node that still holds its item, searching from an earlier
    // node. The search starts over from the head when that node left the queue.
    private fun findPredecessor(node: Node<E>, earlier: Node<E>): Node<E> {
        var pred = if (earlier.item == null) head else earlier

        while (true) {
            val next = checkNotNull(pred.next)

            if (next === node) {
                return pred
            }

            pred = next
        }
    }

    /**
     * Goes through the elements from the head. It is weakly consistent: it never fails when
     * the queue changes, and it may or may not show what was added or removed since it was
     * created. It holds the element that it returns next, so [hasNext] never lies.
     */
    private inner class LinkIterator : MutableIterator<E> {

        // The node of nextItem, null at the end
        private var next: Node<E>? = null
        private var nextItem: E? = null
        private var lastReturned: Node<E>? = null

        // A node before lastReturned, where remove starts its search for the predecessor
        private var ancestor: Node<E>? = null

        init {
            fullyLocked {
                next = head.next
                nextItem = next?.item
            }
        }

        override fun hasNext(): Boolean = next != null

        override fun next(): E {
            val current = next ?: throw NoSuchElementException()
            val item = checkNotNull(nextItem)

            lastReturned = current

            fullyLocked {
                // Skips the nodes that were removed in the meantime
                var node = current.next
                var following = node?.item

                while (node != null && following == null) {
                    node = successor(node)
                    following = node?.item
                }

                next = node
                nextItem = following
            }

            return item
        }

        override fun remove() {
            val node = checkNotNull(lastReturned)

            lastReturned = null

            fullyLocked {
                // Else another thread removed it first
                if (node.item != null) {
                    val pred = findPredecessor(node, ancestor ?: head)

                    ancestor = pred
                    unlink(node, pred)
                }
            }
        }
    }

    private class Node<E : Any>(var item: E?) {

        var next: Node<E>? = null
    }
}
