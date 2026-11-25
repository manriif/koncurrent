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
 * A [NavigableSet] that several threads use at once, on a [ConcurrentSkipListMap].
 *
 * It finds, adds and removes an element in a time that grows with the logarithm of its size.
 * Its elements are in their natural order, or in the order of the [Comparator] that it is
 * given.
 *
 * Its iterators are weakly consistent. They never throw [ConcurrentModificationException],
 * and they may or may not show the elements added or removed since they were created.
 *
 * Elements cannot be `null`.
 *
 * ### Platforms
 *
 * - On the JVM and Android this is `java.util.concurrent.ConcurrentSkipListSet`.
 * - Elsewhere this is the sorted map of this library, with its elements as keys.
 *
 * @param E the type of the elements
 */
public expect class ConcurrentSkipListSet<E : Any> : NavigableSet<E> {

    override val size: Int

    /**
     * Creates an empty set in the natural order of its elements. An element that is not
     * [Comparable] makes the set throw [ClassCastException].
     */
    public constructor()

    /**
     * Creates an empty set in the order of [comparator], or in the natural order of its
     * elements when it is `null`.
     */
    public constructor(comparator: Comparator<in E>?)

    /**
     * Creates a set in the natural order of its elements that holds the given [elements].
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

    override fun comparator(): Comparator<in E>?

    override fun first(): E

    override fun last(): E

    override fun lower(element: E): E?

    override fun floor(element: E): E?

    override fun ceiling(element: E): E?

    override fun higher(element: E): E?

    @IgnorableReturnValue
    override fun pollFirst(): E?

    @IgnorableReturnValue
    override fun pollLast(): E?

    override fun descendingSet(): NavigableSet<E>

    override fun descendingIterator(): MutableIterator<E>

    override fun subSet(
        fromElement: E,
        fromInclusive: Boolean,
        toElement: E,
        toInclusive: Boolean,
    ): NavigableSet<E>

    override fun headSet(toElement: E, inclusive: Boolean): NavigableSet<E>

    override fun tailSet(fromElement: E, inclusive: Boolean): NavigableSet<E>

    /**
     * Returns a view of the elements from [fromElement] to just before [toElement].
     *
     * @throws IllegalArgumentException if [fromElement] is after [toElement]
     */
    public fun subSet(fromElement: E, toElement: E): NavigableSet<E>

    /**
     * Returns a view of the elements before [toElement].
     */
    public fun headSet(toElement: E): NavigableSet<E>

    /**
     * Returns a view of the elements that are [fromElement] or after it.
     */
    public fun tailSet(fromElement: E): NavigableSet<E>
}
