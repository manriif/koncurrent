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
import koncurrent.internal.DRAINED_TO_ITSELF
import koncurrent.internal.PortableLinkedBlockingQueue
import kotlin.time.Duration

public actual class LinkedBlockingQueue<E : Any> private constructor(
    private val queue: PortableLinkedBlockingQueue<E>,
) : BlockingQueue<E>, MutableCollection<E> by queue {

    public actual constructor() : this(PortableLinkedBlockingQueue())

    public actual constructor(capacity: Int) : this(PortableLinkedBlockingQueue(capacity))

    public actual constructor(elements: Collection<E>) : this(
        PortableLinkedBlockingQueue(elements),
    )

    @IgnorableReturnValue
    actual override fun addAll(elements: Collection<E>): Boolean {
        require(elements !== this) { ADDED_TO_ITSELF }
        return queue.addAll(elements)
    }

    actual override fun offer(element: E): Boolean = queue.offer(element)

    override fun offer(element: E, timeout: Duration): Boolean =
        queue.offer(element, timeout.inWholeNanoseconds)

    @IgnorableReturnValue
    actual override fun remove(): E = queue.remove()

    @IgnorableReturnValue
    actual override fun poll(): E? = queue.poll()

    @IgnorableReturnValue
    override fun poll(timeout: Duration): E? = queue.poll(timeout.inWholeNanoseconds)

    actual override fun element(): E = queue.element()

    actual override fun peek(): E? = queue.peek()

    actual override fun put(element: E): Unit = queue.put(element)

    @IgnorableReturnValue
    actual override fun take(): E = queue.take()

    actual override fun remainingCapacity(): Int = queue.remainingCapacity()

    @IgnorableReturnValue
    actual override fun drainTo(collection: MutableCollection<in E>): Int =
        drainTo(collection, Int.MAX_VALUE)

    @IgnorableReturnValue
    actual override fun drainTo(collection: MutableCollection<in E>, maxElements: Int): Int {
        require(collection !== this) { DRAINED_TO_ITSELF }
        return queue.drainTo(collection, maxElements)
    }

    override fun toString(): String = queue.toString()
}
