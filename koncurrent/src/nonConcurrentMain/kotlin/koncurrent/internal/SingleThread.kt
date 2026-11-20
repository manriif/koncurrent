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
package koncurrent.internal

import koncurrent.locks.Condition

/**
 * What a wait says on a platform with one thread. No other thread could end it.
 */
internal const val ENDLESS_WAIT = "A wait would never end on a platform with one thread"

/**
 * The condition of a lock on a platform with one thread. Nothing could signal it, so a wait
 * fails. [checkHeld] throws when the lock is not held.
 */
internal class SingleThreadCondition(private val checkHeld: () -> Unit) : Condition {

    override fun await() {
        checkHeld()
        throw IllegalStateException(ENDLESS_WAIT)
    }

    override fun signal(): Unit = checkHeld()

    override fun signalAll(): Unit = checkHeld()
}
