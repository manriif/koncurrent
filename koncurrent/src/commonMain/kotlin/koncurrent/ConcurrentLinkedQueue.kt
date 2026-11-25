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
 * A [Queue] without a capacity that several threads use at once. An operation never waits for
 * an element nor for room: [poll] returns `null` when the queue is empty.
 *
 * It suits threads that hand work over to each other without ever sleeping for it. Use a
 * [BlockingQueue] when a thread must wait for an element.
 *
 * An iterator is weakly consistent. It never throws [ConcurrentModificationException], and it
 * may or may not show the elements added or removed since it was created.
 *
 * ### Platforms
 *
 * - On the JVM and Android this is `java.util.concurrent.ConcurrentLinkedQueue`, which is
 *   lock-free. Its size is counted by going through the elements.
 * - Elsewhere this is a ring of elements that one lock guards. Kotlin cannot update a field of
 *   a node atomically, and the lock-free queue was measured several times slower without it.
 *   No operation holds the lock for long, except [contains], [remove] of an element and the
 *   creation of an iterator, which go through the elements.
 *
 * @param E the type of the elements
 */
public expect class ConcurrentLinkedQueue<E : Any> : Queue<E> {

    override val size: Int

    /**
     * Creates an empty queue.
     */
    public constructor()

    /**
     * Creates a queue that holds the given [elements], in the order of their iterator.
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
}
