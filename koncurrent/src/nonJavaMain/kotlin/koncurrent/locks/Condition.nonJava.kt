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
package koncurrent.locks

import koncurrent.InternalForInheritanceKoncurrentApi
import koncurrent.InterruptedException
import kotlin.time.Duration

@SubclassOptInRequired(InternalForInheritanceKoncurrentApi::class)
public actual interface Condition {

    public actual fun await()

    /**
     * Waits as [await] does, for [timeout] at most. Does not wait when [timeout] is zero or
     * negative. The current thread holds the lock again when this returns or throws. Common code
     * reaches it through the extension of the same name.
     *
     * On JS and Wasm this returns `false` at once, since no other thread could signal.
     *
     * @return `false` if the time ran out before a signal
     * @throws IllegalMonitorStateException if the current thread does not hold the lock
     * @throws InterruptedException if the thread is interrupted before it is signalled
     */
    public fun await(timeout: Duration): Boolean {
        // Fails as a wait does when the lock is not held, and does nothing else there
        signal()

        return false
    }

    public actual fun signal()

    public actual fun signalAll()
}
