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

import koncurrent.internal.ENDLESS_WAIT

// JS and Wasm have one thread, so a plain count is enough
public actual class Semaphore actual constructor(
    private var permits: Int,
    private val fair: Boolean,
) {

    public actual constructor(permits: Int) : this(permits, false)

    // No other thread could release, so the wait would never end
    public actual fun acquire() {
        check(tryAcquire()) { ENDLESS_WAIT }
    }

    public actual fun tryAcquire(): Boolean {
        if (permits <= 0) {
            return false
        }

        permits--
        return true
    }

    public actual fun release() {
        if (permits == Int.MAX_VALUE) {
            throw Error("Maximum permit count exceeded")
        }

        permits++
    }

    public actual fun availablePermits(): Int = permits

    public actual fun isFair(): Boolean = fair
}
