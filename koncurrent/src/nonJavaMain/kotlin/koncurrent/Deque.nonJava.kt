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
public actual interface Deque<E : Any> : Queue<E> {

    public actual fun addFirst(element: E)

    public actual fun addLast(element: E)

    public actual fun offerFirst(element: E): Boolean

    public actual fun offerLast(element: E): Boolean

    @IgnorableReturnValue
    public actual fun removeFirst(): E

    @IgnorableReturnValue
    public actual fun removeLast(): E

    @IgnorableReturnValue
    public actual fun pollFirst(): E?

    @IgnorableReturnValue
    public actual fun pollLast(): E?

    public actual fun getFirst(): E

    public actual fun getLast(): E

    public actual fun peekFirst(): E?

    public actual fun peekLast(): E?

    public actual fun push(element: E)

    @IgnorableReturnValue
    public actual fun pop(): E

    public actual fun descendingIterator(): MutableIterator<E>
}
