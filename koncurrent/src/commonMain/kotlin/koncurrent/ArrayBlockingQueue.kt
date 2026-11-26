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
 * A [BlockingQueue] on an array of fixed size, in the order of arrival: the head is the
 * element that has been in the queue for the longest time.
 *
 * Its capacity is set once. A full queue makes [put] wait, and an empty one makes [take] wait.
 * A fair queue serves the threads that wait in the order of their arrival, which is slower.
 * It allocates nothing once created, where [LinkedBlockingQueue] allocates a node for each
 * element. One lock guards both ends, so the threads that add and those that take wait for
 * each other.
 *
 * An iterator is weakly consistent. It never throws [ConcurrentModificationException], and it
 * may or may not show the elements added or removed since it was created.
 *
 * ### Platforms
 *
 * - On the JVM and Android this is `java.util.concurrent.ArrayBlockingQueue`.
 * - Elsewhere this is a port of the `ArrayBlockingQueue` of JSR-166. Its iterator goes through
 *   the elements as they were when it was created.
 * - JS and Wasm have one thread, which nothing could wake up. [put] on a full queue and [take]
 *   on an empty one throw [IllegalStateException] there.
 *
 * @param E the type of the elements
 */
public expect class ArrayBlockingQueue<E : Any> : BlockingQueue<E> {

    override val size: Int

    /**
     * Creates a queue that holds [capacity] elements at most.
     *
     * @throws IllegalArgumentException if [capacity] is not positive
     */
    public constructor(capacity: Int)

    /**
     * Creates a queue that holds [capacity] elements at most, and that is [fair] or not.
     *
     * @throws IllegalArgumentException if [capacity] is not positive
     */
    public constructor(capacity: Int, fair: Boolean)

    /**
     * Creates a queue that holds [capacity] elements at most, that is [fair] or not, and that
     * starts with the given [elements], in the order of their iterator.
     *
     * @throws IllegalArgumentException if [capacity] is not positive, or less than the number
     * of [elements]
     */
    public constructor(capacity: Int, fair: Boolean, elements: Collection<E>)

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
