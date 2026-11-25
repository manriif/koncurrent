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
package koncurrent.internal

import kotlin.concurrent.atomics.AtomicInt

private const val DEFAULT_INITIAL_CAPACITY = 11

// The largest array that every platform can allocate
private const val MAX_ARRAY_SIZE = Int.MAX_VALUE - 8

/**
 * A blocking queue that hands over its smallest element first, a port of the
 * `PriorityBlockingQueue` of JSR-166. It backs the public queue where the platform has none.
 *
 * The elements are in a binary heap, in an array that grows: the element at an index is not
 * greater than the two at twice that index plus one and plus two. So the head is the smallest
 * one. One lock guards the queue, with a condition for the threads that wait for an element.
 * The queue has no capacity, so no thread waits to add.
 *
 * The array grows without the lock. A thread that needs a larger array gives the lock back
 * while it allocates one, so that the threads that take go on meanwhile. A spin lock keeps two
 * threads from allocating at once.
 *
 * [comparator] is `null` for the natural order of the elements, which must then be
 * [Comparable]. As in the original, an element that is not fails its insertion with
 * [ClassCastException].
 *
 * One thing differs from the original. `removeAll` and `retainAll` remove through the
 * iterator, one element at a time. The original removes them all, then orders the heap again.
 */
internal class PortablePriorityBlockingQueue<E : Any>(
    initialCapacity: Int,
    val comparator: Comparator<in E>?,
) : PortableBlockingQueue<E>() {

    private val order: Comparator<in E> = comparator ?: NaturalOrder

    private var queue = arrayOfNulls<Any>(checkedInitialCapacity(initialCapacity))
    private var count = 0

    private val lock = InternalLock()
    private val notEmpty = lock.newCondition()

    // 1 while a thread allocates a larger array
    private val allocating = AtomicInt(0)

    override val size: Int
        get() = lock.locked { count }

    constructor() : this(DEFAULT_INITIAL_CAPACITY, null)

    constructor(elements: Collection<E>, comparator: Comparator<in E>?) : this(
        initialCapacity = maxOf(1, elements.size),
        comparator = comparator,
    ) {
        for (element in elements) {
            checkComparable(element)

            if (count == queue.size) {
                queue = queue.copyOf(newCapacity(count))
            }

            queue[count++] = element
        }

        // Orders the heap from its last parent up to the root
        for (index in (count ushr 1) - 1 downTo 0) {
            siftDown(index, itemAt(queue, index), queue, count)
        }
    }

    override fun remainingCapacity(): Int = Int.MAX_VALUE

    ///////////////////////////////////////////////////////////////////////////
    // The heap, to change with the lock held
    ///////////////////////////////////////////////////////////////////////////

    @Suppress("UNCHECKED_CAST")
    private fun itemAt(heap: Array<Any?>, index: Int): E = heap[index] as E

    // The smallest element, which is at the root of the heap, or null when the queue is empty
    @Suppress("UNCHECKED_CAST")
    private fun first(): E? = queue[0] as E?

    // The natural order fails for the first element too, which nothing is compared with
    private fun checkComparable(element: E) {
        if (comparator == null && element !is Comparable<*>) {
            throw ClassCastException("${element::class} is not Comparable")
        }
    }

    // Puts the element at the index, then moves it up while it is smaller than its parent
    private fun siftUp(start: Int, element: E, heap: Array<Any?>) {
        var index = start

        while (index > 0) {
            val parent = (index - 1) ushr 1
            val other = itemAt(heap, parent)

            if (order.compare(element, other) >= 0) {
                break
            }

            heap[index] = other
            index = parent
        }

        heap[index] = element
    }

    // Puts the element at the index, then moves it down while it is greater than a child.
    // [length] is the number of elements of the heap.
    private fun siftDown(start: Int, element: E, heap: Array<Any?>, length: Int) {
        var index = start
        val half = length ushr 1

        // While the index is the one of a parent
        while (index < half) {
            var child = (index shl 1) + 1
            var smallest = itemAt(heap, child)
            val right = child + 1

            if (right < length && order.compare(smallest, itemAt(heap, right)) > 0) {
                child = right
                smallest = itemAt(heap, right)
            }

            if (order.compare(element, smallest) <= 0) {
                break
            }

            heap[index] = smallest
            index = child
        }

        heap[index] = element
    }

    private fun dequeue(): E? {
        val result = first() ?: return null
        val heap = queue
        val last = --count
        val moved = itemAt(heap, last)

        heap[last] = null

        if (last > 0) {
            siftDown(0, moved, heap, last)
        }

        return result
    }

    private fun removeAt(index: Int) {
        val heap = queue
        val last = count - 1

        if (last == index) {
            heap[index] = null
        } else {
            val moved = itemAt(heap, last)

            heap[last] = null
            siftDown(index, moved, heap, last)

            // It did not go down, so it may have to go up
            if (heap[index] === moved) {
                siftUp(index, moved, heap)
            }
        }

        count = last
    }

    // Returns the index of the first element for which the predicate holds, or -1
    private inline fun indexOfFirst(predicate: (Any?) -> Boolean): Int {
        val heap = queue

        for (index in 0..<count) {
            if (predicate(heap[index])) {
                return index
            }
        }

        return -1
    }

    // Removes the first element for which the predicate holds. Returns whether there was one.
    private inline fun removeFirst(predicate: (Any?) -> Boolean): Boolean {
        val index = indexOfFirst(predicate)

        if (index < 0) {
            return false
        }

        removeAt(index)
        return true
    }

    /**
     * Tries to replace [heap], which is full, with a larger array. Called with the lock held,
     * which it gives back while it allocates and holds again when it returns. It may return
     * without a larger array, when another thread allocates one. The caller tries again.
     */
    private fun tryGrow(heap: Array<Any?>) {
        lock.unlock()

        var larger: Array<Any?>? = null

        if (allocating.load() == 0 && allocating.compareAndSet(0, 1)) {
            try {
                if (queue === heap) {
                    larger = arrayOfNulls(newCapacity(heap.size))
                }
            } finally {
                allocating.store(0)
            }
        }

        if (larger == null) {
            yieldThread()
        }

        lock.lock()

        if (larger != null && queue === heap) {
            heap.copyInto(larger)
            queue = larger
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Adding
    ///////////////////////////////////////////////////////////////////////////

    override fun offer(element: E): Boolean {
        checkComparable(element)
        lock.lock()

        // Out of the try: a growth that fails has given the lock back
        var heap = queue

        while (count >= heap.size) {
            tryGrow(heap)
            heap = queue
        }

        try {
            siftUp(count, element, heap)
            count++
            notEmpty.signal()
        } finally {
            lock.unlock()
        }

        return true
    }

    override fun put(element: E) {
        val _ = offer(element)
    }

    override fun offer(element: E, nanos: Long): Boolean = offer(element)

    override fun add(element: E): Boolean = offer(element)

    ///////////////////////////////////////////////////////////////////////////
    // Taking
    ///////////////////////////////////////////////////////////////////////////

    override fun poll(): E? = lock.locked { dequeue() }

    override fun take(): E {
        lock.lockedInterruptibly {
            while (true) {
                val element = dequeue()

                if (element != null) {
                    return element
                }

                notEmpty.await()
            }
        }
    }

    override fun poll(nanos: Long): E? {
        var left = nanos

        lock.lockedInterruptibly {
            while (true) {
                val element = dequeue()

                if (element != null || left <= 0L) {
                    return element
                }

                left = notEmpty.awaitRemaining(left)
            }
        }
    }

    override fun peek(): E? = lock.locked { first() }

    override fun drainTo(collection: MutableCollection<in E>, maxElements: Int): Int {
        require(collection !== this) { DRAINED_TO_ITSELF }

        if (maxElements <= 0) {
            return 0
        }

        return lock.locked {
            val drained = minOf(maxElements, count)

            repeat(drained) {
                // Adds before it removes, so that the element stays here when the addition fails
                collection.add(itemAt(queue, 0))

                val _ = dequeue()
            }

            drained
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // The whole queue
    ///////////////////////////////////////////////////////////////////////////

    override fun contains(element: E): Boolean {
        return lock.locked { indexOfFirst { element == it } >= 0 }
    }

    override fun remove(element: E): Boolean = lock.locked { removeFirst { element == it } }

    override fun clear() {
        lock.locked {
            queue.fill(null, 0, count)
            count = 0
        }
    }

    // In the order of the array, which is not the order of the elements
    override fun iterator(): MutableIterator<E> {
        return CopyIterator(lock.locked { queue.copyOf(count) }) { element ->
            val _ = lock.locked { removeFirst { it === element } }
        }
    }
}

/**
 * The order of elements that are [Comparable], as the JDK defines it for a queue without a
 * comparator.
 */
@Suppress("UNCHECKED_CAST")
private val NaturalOrder = Comparator<Any> { first, second ->
    (first as Comparable<Any>).compareTo(second)
}

private fun checkedInitialCapacity(initialCapacity: Int): Int {
    require(initialCapacity >= 1) { "initialCapacity must be positive, was $initialCapacity" }
    return initialCapacity
}

// Grows faster while the array is small
private fun newCapacity(oldCapacity: Int): Int {
    val grown = oldCapacity + if (oldCapacity < 64) oldCapacity + 2 else oldCapacity shr 1

    if (grown in 0..MAX_ARRAY_SIZE) {
        return grown
    }

    // The original throws OutOfMemoryError, which common Kotlin does not have
    if (oldCapacity >= MAX_ARRAY_SIZE) {
        throw Error("The queue cannot grow any more")
    }

    return MAX_ARRAY_SIZE
}
