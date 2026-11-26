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
 * A set that keeps its elements in order, and that finds the closest ones to a given element.
 *
 * The order is the natural one of the elements, or the one of a [Comparator]. An iterator goes
 * through the elements from the first one to the last one.
 *
 * [subSet], [headSet], [tailSet] and [descendingSet] return views. A view reads and changes
 * the set that it comes from, and refuses an element that is out of its bounds.
 *
 * On the JVM and Android this is `java.util.NavigableSet`.
 *
 * @param E the type of the elements
 */
@SubclassOptInRequired(InternalForInheritanceKoncurrentApi::class)
public expect interface NavigableSet<E : Any> : MutableSet<E> {

    /**
     * Returns the comparator that orders this set, or `null` when it is in the natural order of
     * its elements.
     */
    public fun comparator(): Comparator<in E>?

    /**
     * Returns the first element.
     *
     * @throws NoSuchElementException if the set is empty
     */
    public fun first(): E

    /**
     * Returns the last element.
     *
     * @throws NoSuchElementException if the set is empty
     */
    public fun last(): E

    /**
     * Returns the last element before [element], or `null` if there is none.
     */
    public fun lower(element: E): E?

    /**
     * Returns [element] if the set holds it, else the last element before it, or `null` if
     * there is none.
     */
    public fun floor(element: E): E?

    /**
     * Returns [element] if the set holds it, else the first element after it, or `null` if
     * there is none.
     */
    public fun ceiling(element: E): E?

    /**
     * Returns the first element after [element], or `null` if there is none.
     */
    public fun higher(element: E): E?

    /**
     * Removes the first element and returns it, or returns `null` if the set is empty.
     */
    @IgnorableReturnValue
    public fun pollFirst(): E?

    /**
     * Removes the last element and returns it, or returns `null` if the set is empty.
     */
    @IgnorableReturnValue
    public fun pollLast(): E?

    /**
     * Returns a view of this set in the reverse order.
     */
    public fun descendingSet(): NavigableSet<E>

    /**
     * Returns an iterator that goes from the last element to the first one.
     */
    public fun descendingIterator(): MutableIterator<E>

    /**
     * Returns a view of the elements from [fromElement] to [toElement]. Each flag tells whether
     * the view holds an element that equals its bound.
     *
     * @throws IllegalArgumentException if [fromElement] is after [toElement], or if a bound is
     * out of the bounds of this set when it is itself a view
     */
    public fun subSet(
        fromElement: E,
        fromInclusive: Boolean,
        toElement: E,
        toInclusive: Boolean,
    ): NavigableSet<E>

    /**
     * Returns a view of the elements before [toElement], and of that one when [inclusive].
     *
     * @throws IllegalArgumentException if [toElement] is out of the bounds of this set when it
     * is itself a view
     */
    public fun headSet(toElement: E, inclusive: Boolean): NavigableSet<E>

    /**
     * Returns a view of the elements after [fromElement], and of that one when [inclusive].
     *
     * @throws IllegalArgumentException if [fromElement] is out of the bounds of this set when
     * it is itself a view
     */
    public fun tailSet(fromElement: E, inclusive: Boolean): NavigableSet<E>
}
