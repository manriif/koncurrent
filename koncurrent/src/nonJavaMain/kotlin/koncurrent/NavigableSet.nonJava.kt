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

@SubclassOptInRequired(InternalForInheritanceKoncurrentApi::class)
public actual interface NavigableSet<E : Any> : MutableSet<E> {

    public actual fun comparator(): Comparator<in E>?

    public actual fun first(): E

    public actual fun last(): E

    public actual fun lower(element: E): E?

    public actual fun floor(element: E): E?

    public actual fun ceiling(element: E): E?

    public actual fun higher(element: E): E?

    @IgnorableReturnValue
    public actual fun pollFirst(): E?

    @IgnorableReturnValue
    public actual fun pollLast(): E?

    public actual fun descendingSet(): NavigableSet<E>

    public actual fun descendingIterator(): MutableIterator<E>

    public actual fun subSet(
        fromElement: E,
        fromInclusive: Boolean,
        toElement: E,
        toInclusive: Boolean,
    ): NavigableSet<E>

    public actual fun headSet(toElement: E, inclusive: Boolean): NavigableSet<E>

    public actual fun tailSet(fromElement: E, inclusive: Boolean): NavigableSet<E>
}
