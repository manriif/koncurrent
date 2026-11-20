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

import koncurrent.internal.LOCK_NOT_HELD
import koncurrent.internal.SingleThreadCondition

// JS and Wasm have one thread, which never waits. The holds are counted so that an unlock
// without a lock fails as it does on the other platforms.
public actual class ReentrantLock : Lock {

    private var holds = 0

    actual override fun lock() {
        holds++
    }

    actual override fun lockInterruptibly(): Unit = lock()

    actual override fun tryLock(): Boolean {
        holds++
        return true
    }

    actual override fun unlock() {
        checkHeld()
        holds--
    }

    public actual fun newCondition(): Condition = SingleThreadCondition(::checkHeld)

    private fun checkHeld() {
        if (holds == 0) {
            throw IllegalMonitorStateException(LOCK_NOT_HELD)
        }
    }
}
