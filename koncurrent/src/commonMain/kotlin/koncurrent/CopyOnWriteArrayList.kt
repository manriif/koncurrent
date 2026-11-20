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
 * A list that any number of threads can read and update at the same time. Every update makes a
 * new copy of the elements.
 *
 * A read never blocks and costs what a read of an array costs. An update costs a copy of the
 * whole list. So this list suits data that is read often and rarely changed, like a list of
 * listeners.
 *
 * Each update is atomic. A reader sees the elements as one update left them, never a list
 * half updated.
 *
 * An iterator is a snapshot. It goes through the elements as they were when it was created,
 * whatever happens to the list in the meantime. It never throws
 * [ConcurrentModificationException], and it cannot change the list. Its `remove`, `set` and
 * `add` throw [UnsupportedOperationException].
 *
 * [subList] returns a view that reads and updates this list. The view throws
 * [ConcurrentModificationException] once the list was updated otherwise than through it.
 *
 * Elements can be `null`.
 *
 * ### Platforms
 *
 * - On the JVM and Android this is `java.util.concurrent.CopyOnWriteArrayList`.
 * - Elsewhere this is a list written for this library, with the same behavior.
 *
 * @param E the type of the elements
 */
public expect class CopyOnWriteArrayList<E> : MutableList<E> {

    override val size: Int

    /**
     * Creates an empty list.
     */
    public constructor()

    /**
     * Creates a list of the given [elements], in the order of their iterator.
     */
    public constructor(elements: Collection<E>)

    override fun isEmpty(): Boolean

    override fun contains(element: E): Boolean

    override fun containsAll(elements: Collection<E>): Boolean

    override fun get(index: Int): E

    override fun indexOf(element: E): Int

    override fun lastIndexOf(element: E): Int

    override fun iterator(): MutableIterator<E>

    override fun listIterator(): MutableListIterator<E>

    override fun listIterator(index: Int): MutableListIterator<E>

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<E>

    @IgnorableReturnValue
    override fun add(element: E): Boolean

    override fun add(index: Int, element: E)

    @IgnorableReturnValue
    override fun addAll(elements: Collection<E>): Boolean

    @IgnorableReturnValue
    override fun addAll(index: Int, elements: Collection<E>): Boolean

    @IgnorableReturnValue
    override fun set(index: Int, element: E): E

    @IgnorableReturnValue
    override fun remove(element: E): Boolean

    @IgnorableReturnValue
    override fun removeAt(index: Int): E

    @IgnorableReturnValue
    override fun removeAll(elements: Collection<E>): Boolean

    @IgnorableReturnValue
    override fun retainAll(elements: Collection<E>): Boolean

    override fun clear()

    /**
     * Adds [element] at the end unless the list already holds it. The check and the addition
     * are one atomic operation, so among the threads that add the same element at once, only
     * one gets `true`.
     *
     * @return whether the element was added
     */
    @IgnorableReturnValue
    public fun addIfAbsent(element: E): Boolean

    /**
     * Adds at the end each of the [elements] that the list does not hold yet, once, in the
     * order of their iterator. The whole call is one atomic operation.
     *
     * @return the number of elements added
     */
    @IgnorableReturnValue
    public fun addAllAbsent(elements: Collection<E>): Int
}
