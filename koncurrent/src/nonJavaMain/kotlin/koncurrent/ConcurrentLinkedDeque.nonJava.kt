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

import koncurrent.internal.ADDED_TO_ITSELF
import koncurrent.internal.PortableConcurrentDeque

public actual class ConcurrentLinkedDeque<E : Any> private constructor(
    private val deque: PortableConcurrentDeque<E>,
) : Deque<E>, MutableCollection<E> by deque {

    public actual constructor() : this(PortableConcurrentDeque())

    public actual constructor(elements: Collection<E>) : this(PortableConcurrentDeque(elements))

    @IgnorableReturnValue
    actual override fun addAll(elements: Collection<E>): Boolean {
        require(elements !== this) { ADDED_TO_ITSELF }
        return deque.addAll(elements)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Queue
    ///////////////////////////////////////////////////////////////////////////

    actual override fun offer(element: E): Boolean = deque.offer(element)

    @IgnorableReturnValue
    actual override fun remove(): E = deque.remove()

    @IgnorableReturnValue
    actual override fun poll(): E? = deque.poll()

    actual override fun element(): E = deque.element()

    actual override fun peek(): E? = deque.peek()

    ///////////////////////////////////////////////////////////////////////////
    // Deque
    ///////////////////////////////////////////////////////////////////////////

    actual override fun addFirst(element: E): Unit = deque.addFirst(element)

    actual override fun addLast(element: E): Unit = deque.addLast(element)

    actual override fun offerFirst(element: E): Boolean = deque.offerFirst(element)

    actual override fun offerLast(element: E): Boolean = deque.offerLast(element)

    @IgnorableReturnValue
    actual override fun removeFirst(): E = deque.removeFirst()

    @IgnorableReturnValue
    actual override fun removeLast(): E = deque.removeLast()

    @IgnorableReturnValue
    actual override fun pollFirst(): E? = deque.pollFirst()

    @IgnorableReturnValue
    actual override fun pollLast(): E? = deque.pollLast()

    actual override fun getFirst(): E = deque.getFirst()

    actual override fun getLast(): E = deque.getLast()

    actual override fun peekFirst(): E? = deque.peekFirst()

    actual override fun peekLast(): E? = deque.peekLast()

    actual override fun push(element: E): Unit = deque.push(element)

    @IgnorableReturnValue
    actual override fun pop(): E = deque.pop()

    actual override fun descendingIterator(): MutableIterator<E> = deque.descendingIterator()

    override fun toString(): String = deque.toString()
}
