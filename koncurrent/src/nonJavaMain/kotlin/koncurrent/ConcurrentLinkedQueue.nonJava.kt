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

public actual class ConcurrentLinkedQueue<E : Any> private constructor(
    private val queue: PortableConcurrentDeque<E>,
) : Queue<E>, MutableCollection<E> by queue {

    public actual constructor() : this(PortableConcurrentDeque())

    public actual constructor(elements: Collection<E>) : this(PortableConcurrentDeque(elements))

    @IgnorableReturnValue
    actual override fun addAll(elements: Collection<E>): Boolean {
        require(elements !== this) { ADDED_TO_ITSELF }
        return queue.addAll(elements)
    }

    actual override fun offer(element: E): Boolean = queue.offer(element)

    @IgnorableReturnValue
    actual override fun remove(): E = queue.remove()

    @IgnorableReturnValue
    actual override fun poll(): E? = queue.poll()

    actual override fun element(): E = queue.element()

    actual override fun peek(): E? = queue.peek()

    override fun toString(): String = queue.toString()
}
