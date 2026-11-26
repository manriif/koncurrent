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
 * A [BlockingDeque] on linked nodes. Threads add and take at both ends, and wait when they
 * have to.
 *
 * It has no capacity unless it is given one. Without a capacity a `put` never waits, and the
 * deque grows for as long as elements are added faster than they are taken.
 *
 * One lock guards the whole deque, so the threads at one end wait for those at the other end.
 * [LinkedBlockingQueue] does not have that limit, with one end to add and the other to take.
 *
 * An iterator is weakly consistent. It never throws [ConcurrentModificationException], and it
 * may or may not show the elements added or removed since it was created.
 *
 * [putFirst], [putLast], [takeFirst] and [takeLast], and so [put] and [take], only look at the
 * interrupt of their thread when they have to wait.
 *
 * ### Platforms
 *
 * - On the JVM and Android this is `java.util.concurrent.LinkedBlockingDeque`.
 * - Elsewhere this is a port of the `LinkedBlockingDeque` of JSR-166.
 * - JS and Wasm have one thread, which nothing could wake up. A `put` on a full deque and a
 *   `take` on an empty one throw [IllegalStateException] there.
 *
 * @param E the type of the elements
 */
public expect class LinkedBlockingDeque<E : Any> : BlockingDeque<E> {

    override val size: Int

    /**
     * Creates a deque without a capacity.
     */
    public constructor()

    /**
     * Creates a deque that holds [capacity] elements at most.
     *
     * @throws IllegalArgumentException if [capacity] is not positive
     */
    public constructor(capacity: Int)

    /**
     * Creates a deque without a capacity that holds the given [elements], in the order of
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

    override fun addFirst(element: E)

    override fun addLast(element: E)

    override fun offerFirst(element: E): Boolean

    override fun offerLast(element: E): Boolean

    @IgnorableReturnValue
    override fun removeFirst(): E

    @IgnorableReturnValue
    override fun removeLast(): E

    @IgnorableReturnValue
    override fun pollFirst(): E?

    @IgnorableReturnValue
    override fun pollLast(): E?

    override fun getFirst(): E

    override fun getLast(): E

    override fun peekFirst(): E?

    override fun peekLast(): E?

    override fun push(element: E)

    @IgnorableReturnValue
    override fun pop(): E

    override fun descendingIterator(): MutableIterator<E>

    override fun putFirst(element: E)

    override fun putLast(element: E)

    @IgnorableReturnValue
    override fun takeFirst(): E

    @IgnorableReturnValue
    override fun takeLast(): E
}
