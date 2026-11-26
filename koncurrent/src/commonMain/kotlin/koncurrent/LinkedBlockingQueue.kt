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
package koncurrent

/**
 * A [BlockingQueue] on linked nodes, in the order of arrival: the head is the element that has
 * been in the queue for the longest time.
 *
 * It has no capacity unless it is given one. Without a capacity [put] never waits, and the
 * queue grows for as long as elements are added faster than they are taken. Give it a capacity
 * when producers must be slowed down to the pace of the consumers.
 *
 * The threads that add and the threads that take do not wait for each other. They use two
 * different locks.
 *
 * An iterator is weakly consistent. It never throws [ConcurrentModificationException], and it
 * may or may not show the elements added or removed since it was created.
 *
 * ### Platforms
 *
 * - On the JVM and Android this is `java.util.concurrent.LinkedBlockingQueue`.
 * - Elsewhere this is a port of the `LinkedBlockingQueue` of JSR-166.
 * - JS and Wasm have one thread, which nothing could wake up. [put] on a full queue and [take]
 *   on an empty one throw [IllegalStateException] there.
 *
 * @param E the type of the elements
 */
public expect class LinkedBlockingQueue<E : Any> : BlockingQueue<E> {

    override val size: Int

    /**
     * Creates a queue without a capacity.
     */
    public constructor()

    /**
     * Creates a queue that holds [capacity] elements at most.
     *
     * @throws IllegalArgumentException if [capacity] is not positive
     */
    public constructor(capacity: Int)

    /**
     * Creates a queue without a capacity that holds the given [elements], in the order of
     * their iterator.
     */
    public constructor(elements: Collection<E>)

    override fun isEmpty(): Boolean

    override fun contains(element: E): Boolean

    override fun containsAll(elements: Collection<E>): Boolean

    override fun iterator(): MutableIterator<E>

    @IgnorableReturnValue
    override fun add(element: E): Boolean

    @IgnorableReturnValue
    override fun addAll(elements: Collection<E>): Boolean

    @IgnorableReturnValue
    override fun remove(element: E): Boolean

    @IgnorableReturnValue
    override fun removeAll(elements: Collection<E>): Boolean

    @IgnorableReturnValue
    override fun retainAll(elements: Collection<E>): Boolean

    override fun clear()

    override fun offer(element: E): Boolean

    @IgnorableReturnValue
    override fun remove(): E

    @IgnorableReturnValue
    override fun poll(): E?

    override fun element(): E

    override fun peek(): E?

    override fun put(element: E)

    @IgnorableReturnValue
    override fun take(): E

    override fun remainingCapacity(): Int

    @IgnorableReturnValue
    override fun drainTo(collection: MutableCollection<in E>): Int

    @IgnorableReturnValue
    override fun drainTo(collection: MutableCollection<in E>, maxElements: Int): Int
}
