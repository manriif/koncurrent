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
 * A [Queue] with two ends. Elements can be added, removed and read at its head and at its
 * tail. So it serves as a queue, where the first element in leaves first, and as a stack,
 * where the last one in leaves first.
 *
 * Each operation exists at both ends, in the two forms of a queue:
 *
 * | | Head, throws | Head, returns a value | Tail, throws | Tail, returns a value |
 * |---|---|---|---|---|
 * | Add | [addFirst] | [offerFirst] | [addLast] | [offerLast] |
 * | Remove | [removeFirst] | [pollFirst] | [removeLast] | [pollLast] |
 * | Read | [getFirst] | [peekFirst] | [getLast] | [peekLast] |
 *
 * The functions of a queue add at the tail and take from the head: [add] is [addLast], and
 * [poll] is [pollFirst]. The functions of a stack work at the head: [push] is [addFirst], and
 * [pop] is [removeFirst].
 *
 * This interface is not meant to be implemented outside of this library. On the JVM and
 * Android it is `java.util.Deque`, which has more functions to implement.
 *
 * @param E the type of the elements
 */
@SubclassOptInRequired(InternalForInheritanceKoncurrentApi::class)
public expect interface Deque<E : Any> : Queue<E> {

    /**
     * Adds [element] at the head.
     *
     * @throws IllegalStateException if the deque is full
     */
    public fun addFirst(element: E)

    /**
     * Adds [element] at the tail.
     *
     * @throws IllegalStateException if the deque is full
     */
    public fun addLast(element: E)

    /**
     * Adds [element] at the head if the deque has room for it. Never waits.
     *
     * @return whether the element was added
     */
    public fun offerFirst(element: E): Boolean

    /**
     * Adds [element] at the tail if the deque has room for it. Never waits.
     *
     * @return whether the element was added
     */
    public fun offerLast(element: E): Boolean

    /**
     * Removes the first element and returns it.
     *
     * @throws NoSuchElementException if the deque is empty
     */
    @IgnorableReturnValue
    public fun removeFirst(): E

    /**
     * Removes the last element and returns it.
     *
     * @throws NoSuchElementException if the deque is empty
     */
    @IgnorableReturnValue
    public fun removeLast(): E

    /**
     * Removes the first element and returns it, or returns `null` if the deque is empty.
     */
    @IgnorableReturnValue
    public fun pollFirst(): E?

    /**
     * Removes the last element and returns it, or returns `null` if the deque is empty.
     */
    @IgnorableReturnValue
    public fun pollLast(): E?

    /**
     * Returns the first element without removing it.
     *
     * @throws NoSuchElementException if the deque is empty
     */
    public fun getFirst(): E

    /**
     * Returns the last element without removing it.
     *
     * @throws NoSuchElementException if the deque is empty
     */
    public fun getLast(): E

    /**
     * Returns the first element without removing it, or `null` if the deque is empty.
     */
    public fun peekFirst(): E?

    /**
     * Returns the last element without removing it, or `null` if the deque is empty.
     */
    public fun peekLast(): E?

    /**
     * Adds [element] at the head, as on a stack. It is [addFirst].
     *
     * @throws IllegalStateException if the deque is full
     */
    public fun push(element: E)

    /**
     * Removes the first element and returns it, as from a stack. It is [removeFirst].
     *
     * @throws NoSuchElementException if the deque is empty
     */
    @IgnorableReturnValue
    public fun pop(): E

    /**
     * Returns an iterator that goes from the last element to the first one.
     */
    public fun descendingIterator(): MutableIterator<E>
}
