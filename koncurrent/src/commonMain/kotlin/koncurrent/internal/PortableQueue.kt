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

import koncurrent.InterruptedException

/**
 * What the ports of the queues share: the functions that a queue builds from [offer], [poll]
 * and [peek]. It stands for the `AbstractQueue` of java.util.
 */
internal abstract class PortableQueue<E : Any> : AbstractMutableCollection<E>() {

    /**
     * Adds [element] if the queue has room for it. Never waits.
     *
     * @return whether the element was added
     */
    abstract fun offer(element: E): Boolean

    /**
     * Removes the head and returns it, or returns `null` if the queue is empty.
     */
    abstract fun poll(): E?

    /**
     * Returns the head without removing it, or `null` if the queue is empty.
     */
    abstract fun peek(): E?

    /**
     * Removes the head and returns it.
     *
     * @throws NoSuchElementException if the queue is empty
     */
    fun remove(): E = poll() ?: throw NoSuchElementException()

    /**
     * Returns the head without removing it.
     *
     * @throws NoSuchElementException if the queue is empty
     */
    fun element(): E = peek() ?: throw NoSuchElementException()

    override fun add(element: E): Boolean {
        check(offer(element)) { QUEUE_FULL }
        return true
    }

    override fun addAll(elements: Collection<E>): Boolean {
        // It would never end on a queue without a capacity
        require(elements !== this) { ADDED_TO_ITSELF }
        return super.addAll(elements)
    }
}

/**
 * What the ports of the blocking queues share. A time limit is counted in nanoseconds, as the
 * timed waits of the locks are.
 */
internal abstract class PortableBlockingQueue<E : Any> : PortableQueue<E>() {

    /**
     * Adds [element], and waits for room while the queue is full.
     *
     * @throws InterruptedException if the thread is interrupted before or while it waits
     */
    abstract fun put(element: E)

    /**
     * Adds [element], and waits for room for [nanos] nanoseconds at most.
     *
     * @return `false` if the time ran out before there was room
     * @throws InterruptedException if the thread is interrupted before or while it waits
     */
    abstract fun offer(element: E, nanos: Long): Boolean

    /**
     * Removes the head and returns it, and waits for an element while the queue is empty.
     *
     * @throws InterruptedException if the thread is interrupted before or while it waits
     */
    abstract fun take(): E

    /**
     * Removes the head and returns it, and waits for an element for [nanos] nanoseconds at
     * most.
     *
     * @return `null` if the time ran out before there was an element
     * @throws InterruptedException if the thread is interrupted before or while it waits
     */
    abstract fun poll(nanos: Long): E?

    /**
     * Returns the number of elements that the queue can take without waiting, or
     * [Int.MAX_VALUE] if it has no limit.
     */
    abstract fun remainingCapacity(): Int

    /**
     * Moves [maxElements] elements at most from the head of this queue to [collection]. Never
     * waits.
     *
     * @return the number of elements moved
     * @throws IllegalArgumentException if [collection] is this queue
     */
    abstract fun drainTo(collection: MutableCollection<in E>, maxElements: Int): Int
}
