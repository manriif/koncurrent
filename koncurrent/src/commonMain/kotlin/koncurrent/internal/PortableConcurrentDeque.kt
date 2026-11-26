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
// Kotlin/Native gives each function its own frame of GC roots, even where LLVM inlines it. The
// small functions of the hot paths are inlined by Kotlin, which merges their frames.
@file:Suppress("NOTHING_TO_INLINE")

package koncurrent.internal

private const val INITIAL_CAPACITY = 16

// A ring that grew beyond this is dropped once it is empty
private const val KEPT_CAPACITY = 1024

// The length of the ring is a power of two
private const val MAX_CAPACITY = 1 shl 30

/**
 * A deque on a ring of elements that one lock guards. It backs the public `ConcurrentLinkedQueue`
 * and `ConcurrentLinkedDeque` where the platform has none.
 *
 * It is not the algorithm of JSR-166, which is lock-free on linked nodes. That one updates the
 * fields of a node atomically, which Kotlin can only do on an object of its own for each field.
 * An element then takes three objects, and their allocation costs more than a lock does. On
 * macosArm64 that algorithm ran 2 times slower than this ring with one thread, and 5 to 6 times
 * slower with 2 to 8 threads.
 *
 * The ring grows by doubling, and never waits for room. A ring that a burst made large is
 * dropped once it is empty, so that its memory is freed as the nodes of a linked queue are.
 *
 * An iterator goes through a copy of the elements.
 *
 * Adding and taking at an end are each one function, with the work on the ring written in
 * place. Split in three functions that called each other, they ran at half the speed.
 */
internal class PortableConcurrentDeque<E : Any>() : PortableQueue<E>() {

    private val lock = InternalLock()

    // The lock guards the ring, the index of its first element and the number of elements
    private var items = arrayOfNulls<Any>(INITIAL_CAPACITY)
    private var head = 0
    private var count = 0

    override val size: Int
        get() = lock.locked { count }

    constructor(elements: Collection<E>) : this() {
        val _ = addAll(elements)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Both ends
    ///////////////////////////////////////////////////////////////////////////

    fun addFirst(element: E) {
        lock.locked {
            reserve()
            count++
            head = index(-1)
            items[head] = element
        }
    }

    fun addLast(element: E) {
        lock.locked { append(element) }
    }

    fun offerFirst(element: E): Boolean {
        addFirst(element)
        return true
    }

    fun offerLast(element: E): Boolean {
        addLast(element)
        return true
    }

    fun pollFirst(): E? = lock.locked {
        if (count == 0) {
            return null
        }

        val element = itemAt(0)

        items[head] = null
        head = index(1)
        count--
        releaseIfEmpty()

        element
    }

    fun pollLast(): E? = lock.locked {
        if (count == 0) {
            return null
        }

        val element = itemAt(count - 1)

        items[index(count - 1)] = null
        count--
        releaseIfEmpty()

        element
    }

    fun peekFirst(): E? = lock.locked { if (count == 0) null else itemAt(0) }

    fun peekLast(): E? = lock.locked { if (count == 0) null else itemAt(count - 1) }

    /**
     * @throws NoSuchElementException if the deque is empty
     */
    fun removeFirst(): E = pollFirst() ?: throw NoSuchElementException()

    /**
     * @throws NoSuchElementException if the deque is empty
     */
    fun removeLast(): E = pollLast() ?: throw NoSuchElementException()

    /**
     * @throws NoSuchElementException if the deque is empty
     */
    fun getFirst(): E = peekFirst() ?: throw NoSuchElementException()

    /**
     * @throws NoSuchElementException if the deque is empty
     */
    fun getLast(): E = peekLast() ?: throw NoSuchElementException()

    fun push(element: E): Unit = addFirst(element)

    /**
     * @throws NoSuchElementException if the deque is empty
     */
    fun pop(): E = removeFirst()

    ///////////////////////////////////////////////////////////////////////////
    // Queue
    ///////////////////////////////////////////////////////////////////////////

    override fun offer(element: E): Boolean = offerLast(element)

    override fun poll(): E? = pollFirst()

    override fun peek(): E? = peekFirst()

    ///////////////////////////////////////////////////////////////////////////
    // Collection
    ///////////////////////////////////////////////////////////////////////////

    // The elements are added together. They are copied first, so that the lock is not held
    // while another collection is read.
    override fun addAll(elements: Collection<E>): Boolean {
        require(elements !== this) { ADDED_TO_ITSELF }

        val added = ArrayList<E>()

        for (element in elements) {
            added += element
        }

        lock.locked { added.forEach { append(it) } }

        return added.isNotEmpty()
    }

    override fun contains(element: E): Boolean = lock.locked { indexOfFirst { element == it } >= 0 }

    override fun remove(element: E): Boolean = lock.locked { removeFirst { element == it } }

    override fun clear() {
        lock.locked {
            for (position in 0..<count) {
                items[index(position)] = null
            }

            head = 0
            count = 0
            releaseIfEmpty()
        }
    }

    override fun iterator(): MutableIterator<E> = iteratorOver(lock.locked { snapshot() })

    fun descendingIterator(): MutableIterator<E> {
        return iteratorOver(lock.locked { snapshot().apply { reverse() } })
    }

    private fun iteratorOver(elements: Array<Any?>): MutableIterator<E> {
        return CopyIterator(elements) { element ->
            val _ = lock.locked { removeFirst { it === element } }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // The ring, with the lock held
    ///////////////////////////////////////////////////////////////////////////

    // The index in the ring of the element at [position] from the head
    private inline fun index(position: Int): Int = (head + position) and (items.size - 1)

    @Suppress("UNCHECKED_CAST")
    private inline fun itemAt(position: Int): E = items[index(position)] as E

    private inline fun append(element: E) {
        reserve()
        items[index(count)] = element
        count++
    }

    // Makes room for one more element
    private inline fun reserve() {
        if (count == items.size) {
            grow()
        }
    }

    private fun grow() {
        if (items.size == MAX_CAPACITY) {
            throw Error("The deque cannot grow any more")
        }

        val grown = arrayOfNulls<Any>(items.size * 2)

        copyInto(grown)
        items = grown
        head = 0
    }

    // Copies the elements into [target], from the head to the tail
    private fun copyInto(target: Array<Any?>) {
        val untilEnd = minOf(count, items.size - head)

        val _ = items.copyInto(target, 0, head, head + untilEnd)
        val _ = items.copyInto(target, untilEnd, 0, count - untilEnd)
    }

    private fun snapshot(): Array<Any?> = arrayOfNulls<Any>(count).also(::copyInto)

    private inline fun indexOfFirst(predicate: (Any?) -> Boolean): Int {
        for (position in 0..<count) {
            if (predicate(items[index(position)])) {
                return position
            }
        }

        return -1
    }

    // Removes the first element for which the predicate holds. Returns whether there was one.
    private inline fun removeFirst(predicate: (Any?) -> Boolean): Boolean {
        val position = indexOfFirst(predicate)

        if (position < 0) {
            return false
        }

        removeAt(position)
        return true
    }

    // Removes the element at [position] from the head. The elements of the shorter side move
    // one step to close the gap.
    private fun removeAt(position: Int) {
        if (position < count / 2) {
            for (moved in position downTo 1) {
                items[index(moved)] = items[index(moved - 1)]
            }

            items[head] = null
            head = index(1)
        } else {
            for (moved in position..<count - 1) {
                items[index(moved)] = items[index(moved + 1)]
            }

            items[index(count - 1)] = null
        }

        count--
        releaseIfEmpty()
    }

    private inline fun releaseIfEmpty() {
        if (count == 0 && items.size > KEPT_CAPACITY) {
            release()
        }
    }

    // Drops a ring that a burst made large
    private fun release() {
        items = arrayOfNulls(INITIAL_CAPACITY)
        head = 0
    }
}
