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


/**
 * A blocking queue on an array of fixed size, a port of the `ArrayBlockingQueue` of JSR-166. It
 * backs the public queue where the platform has none.
 *
 * The array is used as a ring. [putIndex] is where the next element goes, [takeIndex] where the
 * next one leaves, and both go back to the start after the end. One lock guards the queue,
 * with a condition for the threads that wait for an element and another one for those that
 * wait for room.
 *
 * Two things differ from the original.
 *
 * - **An iterator goes through a copy of the elements**, made when it is created. The original
 *   keeps every iterator in step with the array, which shifts its elements when one is removed
 *   from the middle. It tracks the iterators through weak references for that, which common
 *   Kotlin does not have. Both kinds are weakly consistent, as java.util.concurrent defines
 *   it, and the `PriorityBlockingQueue` of the JDK iterates on a copy too.
 * - **`clear` and `drainTo` wake up every thread that waits for room.** The original wakes up
 *   as many as it made room for, which takes a count of the waiting threads that the common
 *   lock does not give. A thread that finds no room waits again.
 */
internal class PortableArrayBlockingQueue<E : Any>(
    capacity: Int,
    fair: Boolean = false,
) : PortableBlockingQueue<E>() {

    private val items = arrayOfNulls<Any>(checkedCapacity(capacity))
    private var takeIndex = 0
    private var putIndex = 0
    private var count = 0

    // A fair lock serves the threads that wait to add or to take in the order of their arrival
    private val lock = InternalLock(fair)
    private val notEmpty = lock.newCondition()
    private val notFull = lock.newCondition()

    override val size: Int
        get() = lock.locked { count }

    constructor(capacity: Int, fair: Boolean, elements: Collection<E>) : this(capacity, fair) {
        // Never contended. The lock makes the elements visible to the other threads.
        lock.locked {
            for (element in elements) {
                require(count < items.size) { "capacity must hold the elements, was $capacity" }
                items[count++] = element
            }

            putIndex = if (count == items.size) 0 else count
        }
    }

    override fun remainingCapacity(): Int = lock.locked { items.size - count }

    ///////////////////////////////////////////////////////////////////////////
    // The ring, to change with the lock held
    ///////////////////////////////////////////////////////////////////////////

    @Suppress("UNCHECKED_CAST")
    private fun itemAt(index: Int): E? = items[index] as E?

    private fun following(index: Int): Int = if (index + 1 == items.size) 0 else index + 1

    private fun enqueue(element: E) {
        items[putIndex] = element
        putIndex = following(putIndex)
        count++
        notEmpty.signal()
    }

    private fun dequeue(): E {
        val element = checkNotNull(itemAt(takeIndex))

        items[takeIndex] = null
        takeIndex = following(takeIndex)
        count--
        notFull.signal()

        return element
    }

    private fun removeAt(removeIndex: Int) {
        if (removeIndex == takeIndex) {
            items[takeIndex] = null
            takeIndex = following(takeIndex)
        } else {
            // Slides the elements that follow one slot back, up to the tail
            var index = removeIndex

            while (true) {
                val next = following(index)

                if (next == putIndex) {
                    items[index] = null
                    putIndex = index
                    break
                }

                items[index] = items[next]
                index = next
            }
        }

        count--
        notFull.signal()
    }

    // Returns the index of the first element for which the predicate holds, or -1
    private inline fun indexOfFirst(predicate: (Any?) -> Boolean): Int {
        var index = takeIndex

        repeat(count) {
            if (predicate(items[index])) {
                return index
            }

            index = following(index)
        }

        return -1
    }

    ///////////////////////////////////////////////////////////////////////////
    // Adding
    ///////////////////////////////////////////////////////////////////////////

    override fun offer(element: E): Boolean {
        return lock.locked {
            if (count == items.size) {
                return false
            }

            enqueue(element)
            true
        }
    }

    override fun put(element: E) {
        lock.lockedInterruptibly {
            while (count == items.size) {
                notFull.await()
            }

            enqueue(element)
        }
    }

    override fun offer(element: E, nanos: Long): Boolean {
        var left = nanos

        return lock.lockedInterruptibly {
            while (count == items.size) {
                if (left <= 0L) {
                    return false
                }

                left = notFull.awaitRemaining(left)
            }

            enqueue(element)
            true
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Taking
    ///////////////////////////////////////////////////////////////////////////

    override fun poll(): E? = lock.locked { if (count == 0) null else dequeue() }

    override fun take(): E {
        return lock.lockedInterruptibly {
            while (count == 0) {
                notEmpty.await()
            }

            dequeue()
        }
    }

    override fun poll(nanos: Long): E? {
        var left = nanos

        return lock.lockedInterruptibly {
            while (count == 0) {
                if (left <= 0L) {
                    return null
                }

                left = notEmpty.awaitRemaining(left)
            }

            dequeue()
        }
    }

    // The slot of the head holds null when the queue is empty
    override fun peek(): E? = lock.locked { itemAt(takeIndex) }

    override fun drainTo(collection: MutableCollection<in E>, maxElements: Int): Int {
        require(collection !== this) { DRAINED_TO_ITSELF }

        if (maxElements <= 0) {
            return 0
        }

        return lock.locked {
            val drained = minOf(maxElements, count)
            var index = takeIndex
            var moved = 0

            try {
                while (moved < drained) {
                    collection.add(checkNotNull(itemAt(index)))
                    items[index] = null
                    index = following(index)
                    moved++
                }

                drained
            } finally {
                // Leaves the queue in order even when the collection threw
                if (moved > 0) {
                    count -= moved
                    takeIndex = index
                    notFull.signalAll()
                }
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // The whole queue
    ///////////////////////////////////////////////////////////////////////////

    override fun contains(element: E): Boolean {
        return lock.locked { indexOfFirst { element == it } >= 0 }
    }

    override fun remove(element: E): Boolean = lock.locked { removeFirst { element == it } }

    // Removes the first element for which the predicate holds. Returns whether there was one.
    private inline fun removeFirst(predicate: (Any?) -> Boolean): Boolean {
        val index = indexOfFirst(predicate)

        if (index < 0) {
            return false
        }

        removeAt(index)
        return true
    }

    override fun clear() {
        lock.locked {
            if (count > 0) {
                var index = takeIndex

                repeat(count) {
                    items[index] = null
                    index = following(index)
                }

                takeIndex = putIndex
                count = 0
                notFull.signalAll()
            }
        }
    }

    override fun iterator(): MutableIterator<E> {
        return CopyIterator(lock.locked { snapshot() }) { element ->
            val _ = lock.locked { removeFirst { it === element } }
        }
    }

    // Returns a copy of the elements, from the head to the tail
    private fun snapshot(): Array<Any?> {
        val elements = arrayOfNulls<Any>(count)
        var index = takeIndex

        for (position in elements.indices) {
            elements[position] = items[index]
            index = following(index)
        }

        return elements
    }
}

private fun checkedCapacity(capacity: Int): Int {
    require(capacity > 0) { "capacity must be positive, was $capacity" }
    return capacity
}
