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
 * Ported from the LinkedBlockingDeque of JSR-166, which was written by Doug Lea with assistance
 * from members of JCP JSR-166 Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package koncurrent.internal

/**
 * A blocking deque on doubly linked nodes, a port of the `LinkedBlockingDeque` of JSR-166. It
 * backs the public deque where the platform has none.
 *
 * One lock guards the whole deque, with a condition for the threads that wait for an element
 * and another one for those that wait for room.
 *
 * A node that leaves the deque by an end gets linked to itself on that side. An iterator that
 * stands on it then knows to start over from that end. A node removed from the middle keeps
 * its links, so that an iterator that stands on it can go on.
 *
 * As in the original, [putFirst], [putLast], [takeFirst] and [takeLast] only look at the
 * interrupt of their thread when they have to wait. The functions with a time limit look at it
 * first.
 *
 * One thing differs from the original. `removeAll` and `retainAll` remove through the
 * iterator, one element at a time. The original works on batches of elements.
 */
internal class PortableLinkedBlockingDeque<E : Any>(
    private val capacity: Int,
) : PortableBlockingQueue<E>() {

    private var first: Node<E>? = null
    private var last: Node<E>? = null
    private var count = 0

    private val lock = InternalLock()
    private val notEmpty = lock.newCondition()
    private val notFull = lock.newCondition()

    override val size: Int
        get() = lock.locked { count }

    init {
        require(capacity > 0) { "capacity must be positive, was $capacity" }
    }

    constructor() : this(Int.MAX_VALUE)

    constructor(elements: Collection<E>) : this() {
        val _ = addAll(elements)
    }

    override fun remainingCapacity(): Int = lock.locked { capacity - count }

    ///////////////////////////////////////////////////////////////////////////
    // Links, to change with the lock held
    ///////////////////////////////////////////////////////////////////////////

    private fun linkFirst(node: Node<E>): Boolean {
        if (count >= capacity) {
            return false
        }

        val oldFirst = first

        node.next = oldFirst
        first = node

        if (oldFirst == null) {
            last = node
        } else {
            oldFirst.prev = node
        }

        count++
        notEmpty.signal()

        return true
    }

    private fun linkLast(node: Node<E>): Boolean {
        if (count >= capacity) {
            return false
        }

        val oldLast = last

        node.prev = oldLast
        last = node

        if (oldLast == null) {
            first = node
        } else {
            oldLast.next = node
        }

        count++
        notEmpty.signal()

        return true
    }

    private fun unlinkFirst(): E? {
        val oldFirst = first ?: return null
        val next = oldFirst.next
        val item = oldFirst.item

        oldFirst.item = null

        // Tells an iterator that stands on it to start over from the head
        oldFirst.next = oldFirst
        first = next

        if (next == null) {
            last = null
        } else {
            next.prev = null
        }

        count--
        notFull.signal()

        return item
    }

    private fun unlinkLast(): E? {
        val oldLast = last ?: return null
        val prev = oldLast.prev
        val item = oldLast.item

        oldLast.item = null

        // Tells a descending iterator that stands on it to start over from the tail
        oldLast.prev = oldLast
        last = prev

        if (prev == null) {
            first = null
        } else {
            prev.next = null
        }

        count--
        notFull.signal()

        return item
    }

    private fun unlink(node: Node<E>) {
        val prev = node.prev
        val next = node.next

        if (prev == null) {
            val _ = unlinkFirst()
        } else if (next == null) {
            val _ = unlinkLast()
        } else {
            // The node keeps its links, for the iterators that stand on it
            prev.next = next
            next.prev = prev
            node.item = null
            count--
            notFull.signal()
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Adding
    ///////////////////////////////////////////////////////////////////////////

    fun addFirst(element: E) {
        check(offerFirst(element)) { DEQUE_FULL }
    }

    fun addLast(element: E) {
        check(offerLast(element)) { DEQUE_FULL }
    }

    fun offerFirst(element: E): Boolean = offerAt(element, ::linkFirst)

    fun offerLast(element: E): Boolean = offerAt(element, ::linkLast)

    fun putFirst(element: E): Unit = putAt(element, ::linkFirst)

    fun putLast(element: E): Unit = putAt(element, ::linkLast)

    fun offerFirst(element: E, nanos: Long): Boolean = offerAt(element, nanos, ::linkFirst)

    fun offerLast(element: E, nanos: Long): Boolean = offerAt(element, nanos, ::linkLast)

    fun push(element: E): Unit = addFirst(element)

    private inline fun offerAt(element: E, link: (Node<E>) -> Boolean): Boolean {
        val node = Node(element)

        return lock.locked { link(node) }
    }

    private inline fun putAt(element: E, link: (Node<E>) -> Boolean) {
        val node = Node(element)

        lock.locked {
            while (!link(node)) {
                notFull.await()
            }
        }
    }

    private inline fun offerAt(element: E, nanos: Long, link: (Node<E>) -> Boolean): Boolean {
        val node = Node(element)
        var left = nanos

        return lock.lockedInterruptibly {
            while (!link(node)) {
                if (left <= 0L) {
                    return false
                }

                left = notFull.awaitRemaining(left)
            }

            true
        }
    }

    override fun add(element: E): Boolean {
        addLast(element)
        return true
    }

    override fun offer(element: E): Boolean = offerLast(element)

    override fun put(element: E): Unit = putLast(element)

    override fun offer(element: E, nanos: Long): Boolean = offerLast(element, nanos)

    override fun addAll(elements: Collection<E>): Boolean {
        require(elements !== this) { ADDED_TO_ITSELF }

        // Links the elements to each other first, to append them all at once
        var begin: Node<E>? = null
        var end: Node<E>? = null
        var added = 0

        for (element in elements) {
            val node = Node(element)

            node.prev = end
            end?.next = node
            begin = begin ?: node
            end = node
            added++
        }

        if (begin == null) {
            return false
        }

        lock.locked {
            if (added <= capacity - count) {
                val oldLast = last

                begin.prev = oldLast

                if (oldLast == null) {
                    first = begin
                } else {
                    oldLast.next = begin
                }

                last = end
                count += added
                notEmpty.signalAll()

                return true
            }
        }

        // There is not room for all of them. Adds them one by one, until the deque is full.
        return super.addAll(elements)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Taking
    ///////////////////////////////////////////////////////////////////////////

    fun removeFirst(): E = pollFirst() ?: throw NoSuchElementException()

    fun removeLast(): E = pollLast() ?: throw NoSuchElementException()

    fun pollFirst(): E? = lock.locked { unlinkFirst() }

    fun pollLast(): E? = lock.locked { unlinkLast() }

    fun takeFirst(): E = takeAt(::unlinkFirst)

    fun takeLast(): E = takeAt(::unlinkLast)

    fun pollFirst(nanos: Long): E? = pollAt(nanos, ::unlinkFirst)

    fun pollLast(nanos: Long): E? = pollAt(nanos, ::unlinkLast)

    fun pop(): E = removeFirst()

    private inline fun takeAt(unlink: () -> E?): E {
        lock.locked {
            while (true) {
                val item = unlink()

                if (item != null) {
                    return item
                }

                notEmpty.await()
            }
        }
    }

    private inline fun pollAt(nanos: Long, unlink: () -> E?): E? {
        var left = nanos

        lock.lockedInterruptibly {
            while (true) {
                val item = unlink()

                if (item != null || left <= 0L) {
                    return item
                }

                left = notEmpty.awaitRemaining(left)
            }
        }
    }

    override fun poll(): E? = pollFirst()

    override fun take(): E = takeFirst()

    override fun poll(nanos: Long): E? = pollFirst(nanos)

    override fun drainTo(collection: MutableCollection<in E>, maxElements: Int): Int {
        require(collection !== this) { DRAINED_TO_ITSELF }

        if (maxElements <= 0) {
            return 0
        }

        return lock.locked {
            val drained = minOf(maxElements, count)

            repeat(drained) {
                // Adds before it unlinks, so that the element stays here when the addition fails
                collection.add(checkNotNull(first?.item))

                val _ = unlinkFirst()
            }

            drained
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Reading
    ///////////////////////////////////////////////////////////////////////////

    fun getFirst(): E = peekFirst() ?: throw NoSuchElementException()

    fun getLast(): E = peekLast() ?: throw NoSuchElementException()

    fun peekFirst(): E? = lock.locked { first?.item }

    fun peekLast(): E? = lock.locked { last?.item }

    override fun peek(): E? = peekFirst()

    override fun contains(element: E): Boolean = lock.locked { find(element) != null }

    // Returns the first node that holds the element. To call with the lock held.
    private fun find(element: E): Node<E>? {
        var node = first

        while (node != null && element != node.item) {
            node = node.next
        }

        return node
    }

    ///////////////////////////////////////////////////////////////////////////
    // Removing from the middle
    ///////////////////////////////////////////////////////////////////////////

    override fun remove(element: E): Boolean {
        return lock.locked {
            val node = find(element) ?: return false

            unlink(node)
            true
        }
    }

    override fun clear() {
        lock.locked {
            var node = first

            while (node != null) {
                val next = node.next

                node.item = null
                node.prev = null
                node.next = null
                node = next
            }

            first = null
            last = null
            count = 0
            notFull.signalAll()
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Iteration
    ///////////////////////////////////////////////////////////////////////////

    override fun iterator(): MutableIterator<E> = LinkIterator(descending = false)

    fun descendingIterator(): MutableIterator<E> = LinkIterator(descending = true)

    /**
     * Goes through the elements from one end to the other. It is weakly consistent: it never
     * fails when the deque changes, and it may or may not show what was added or removed since
     * it was created. It holds the element that it returns next, so [hasNext] never lies.
     */
    private inner class LinkIterator(private val descending: Boolean) : MutableIterator<E> {

        // The node of nextItem, null at the end
        private var next: Node<E>? = null
        private var nextItem: E? = null
        private var lastReturned: Node<E>? = null

        init {
            lock.locked {
                next = start()
                nextItem = next?.item
            }
        }

        private fun start(): Node<E>? = if (descending) last else first

        private fun following(node: Node<E>): Node<E>? = if (descending) node.prev else node.next

        // A node linked to itself left the deque by the end where this iteration started
        private fun successor(node: Node<E>): Node<E>? {
            val next = following(node)

            return if (next === node) start() else next
        }

        override fun hasNext(): Boolean = next != null

        override fun next(): E {
            val current = next ?: throw NoSuchElementException()
            val item = checkNotNull(nextItem)

            lastReturned = current

            lock.locked {
                // Skips the nodes that were removed in the meantime
                var node = following(current)
                var followingItem = node?.item

                while (node != null && followingItem == null) {
                    node = successor(node)
                    followingItem = node?.item
                }

                next = node
                nextItem = followingItem
            }

            return item
        }

        override fun remove() {
            val node = checkNotNull(lastReturned)

            lastReturned = null

            lock.locked {
                // Else another thread removed it first
                if (node.item != null) {
                    unlink(node)
                }
            }
        }
    }

    private class Node<E : Any>(var item: E?) {

        var prev: Node<E>? = null
        var next: Node<E>? = null
    }
}
