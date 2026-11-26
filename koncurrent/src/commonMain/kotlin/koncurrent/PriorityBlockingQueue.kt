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
 * A [BlockingQueue] that hands over its smallest element first, whatever the order of arrival.
 * The order is the one of a [Comparator], or the natural order of the elements when the queue
 * is created without one. Elements that compare as equal leave in no particular order.
 *
 * The queue has no capacity. [put] and [offer] never wait and never fail, and
 * [remainingCapacity] is always [Int.MAX_VALUE]. An empty queue makes [take] wait.
 *
 * Without a comparator the elements must be [Comparable]. An element that is not fails its
 * insertion with [ClassCastException].
 *
 * An iterator goes through the elements as they were when it was created, in no particular
 * order. Use [drainTo] to get them in the order of the queue.
 *
 * ### Platforms
 *
 * - On the JVM and Android this is `java.util.concurrent.PriorityBlockingQueue`.
 * - Elsewhere this is a port of the `PriorityBlockingQueue` of JSR-166.
 * - JS and Wasm have one thread, which nothing could wake up. [take] on an empty queue throws
 *   [IllegalStateException] there.
 *
 * @param E the type of the elements
 */
public expect class PriorityBlockingQueue<E : Any> : BlockingQueue<E> {

    override val size: Int

    /**
     * Creates a queue in the natural order of its elements.
     */
    public constructor()

    /**
     * Creates a queue in the natural order of its elements, with room for [initialCapacity]
     * elements before it has to grow.
     *
     * @throws IllegalArgumentException if [initialCapacity] is less than 1
     */
    public constructor(initialCapacity: Int)

    /**
     * Creates a queue in the order of [comparator], with room for [initialCapacity] elements
     * before it has to grow.
     *
     * @throws IllegalArgumentException if [initialCapacity] is less than 1
     */
    public constructor(initialCapacity: Int, comparator: Comparator<in E>)

    /**
     * Creates a queue that holds the given [elements]. It takes the order of [elements] when
     * that is a `PriorityBlockingQueue`, else the natural order of the elements.
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
