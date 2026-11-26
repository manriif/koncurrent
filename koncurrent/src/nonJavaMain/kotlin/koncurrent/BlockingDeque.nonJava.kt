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

import kotlin.time.Duration

@SubclassOptInRequired(InternalForInheritanceKoncurrentApi::class)
public actual interface BlockingDeque<E : Any> : BlockingQueue<E>, Deque<E> {

    public actual fun putFirst(element: E)

    public actual fun putLast(element: E)

    /**
     * Adds [element] at the head, and waits for room for [timeout] at most. Does not wait when
     * [timeout] is zero or negative. Common code reaches it through the extension of the same
     * name.
     *
     * On JS and Wasm this never waits, since no other thread could make room.
     *
     * @return `false` if the time ran out before there was room
     * @throws InterruptedException if the thread is interrupted before or while it waits
     */
    public fun offerFirst(element: E, timeout: Duration): Boolean

    /**
     * Adds [element] at the tail, and waits for room for [timeout] at most. Does not wait when
     * [timeout] is zero or negative. Common code reaches it through the extension of the same
     * name.
     *
     * On JS and Wasm this never waits, since no other thread could make room.
     *
     * @return `false` if the time ran out before there was room
     * @throws InterruptedException if the thread is interrupted before or while it waits
     */
    public fun offerLast(element: E, timeout: Duration): Boolean

    @IgnorableReturnValue
    public actual fun takeFirst(): E

    @IgnorableReturnValue
    public actual fun takeLast(): E

    /**
     * Removes the first element and returns it, and waits for an element for [timeout] at
     * most. Does not wait when [timeout] is zero or negative. Common code reaches it through
     * the extension of the same name.
     *
     * On JS and Wasm this never waits, since no other thread could add an element.
     *
     * @return `null` if the time ran out before there was an element
     * @throws InterruptedException if the thread is interrupted before or while it waits
     */
    @IgnorableReturnValue
    public fun pollFirst(timeout: Duration): E?

    /**
     * Removes the last element and returns it, and waits for an element for [timeout] at most.
     * Does not wait when [timeout] is zero or negative. Common code reaches it through the
     * extension of the same name.
     *
     * On JS and Wasm this never waits, since no other thread could add an element.
     *
     * @return `null` if the time ran out before there was an element
     * @throws InterruptedException if the thread is interrupted before or while it waits
     */
    @IgnorableReturnValue
    public fun pollLast(timeout: Duration): E?
}
