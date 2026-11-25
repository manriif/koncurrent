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
public actual interface BlockingQueue<E : Any> : Queue<E> {

    public actual fun put(element: E)

    /**
     * Adds [element], and waits for room for [timeout] at most. Does not wait when [timeout]
     * is zero or negative. Common code reaches it through the extension of the same name.
     *
     * On JS and Wasm this never waits, since no other thread could make room.
     *
     * @return `false` if the time ran out before there was room
     * @throws InterruptedException if the thread is interrupted before or while it waits
     */
    public fun offer(element: E, timeout: Duration): Boolean

    @IgnorableReturnValue
    public actual fun take(): E

    /**
     * Removes the head of the queue and returns it, and waits for an element for [timeout] at
     * most. Does not wait when [timeout] is zero or negative. Common code reaches it through
     * the extension of the same name.
     *
     * On JS and Wasm this never waits, since no other thread could add an element.
     *
     * @return `null` if the time ran out before there was an element
     * @throws InterruptedException if the thread is interrupted before or while it waits
     */
    @IgnorableReturnValue
    public fun poll(timeout: Duration): E?

    public actual fun remainingCapacity(): Int

    @IgnorableReturnValue
    public actual fun drainTo(collection: MutableCollection<in E>): Int

    @IgnorableReturnValue
    public actual fun drainTo(collection: MutableCollection<in E>, maxElements: Int): Int
}
