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

import koncurrent.InterruptedException
import koncurrent.Thread
import kotlin.concurrent.atomics.AtomicInt

/**
 * Counts the threads whose interrupted flag is set. It is zero nearly always, which spares
 * the waits that check for an interrupt the lookup of the calling thread.
 *
 * No thread local is declared in this file. Kotlin/Native initializes the thread locals of a
 * file for each thread. So an access from another file would first look the thread up, even
 * to read this property, and [clearInterrupt] reads it in every wait.
 */
internal val InterruptedCount = AtomicInt(0)

internal actual fun callingThread(): Thread = CurrentThread ?: Thread().also { CurrentThread = it }

@Suppress("NOTHING_TO_INLINE")
internal actual inline fun clearInterrupt(): Boolean =
    InterruptedCount.load() != 0 && callingThread().clearInterrupt()

internal actual fun sleepNanos(nanos: Long) {
    if (clearInterrupt()) {
        throw InterruptedException()
    }

    val deadline = deadlineAfter(nanos)

    while (true) {
        val left = deadline - nanoTime()

        if (left <= 0L) {
            return
        }

        parkNanos(left)

        if (clearInterrupt()) {
            throw InterruptedException()
        }
    }
}

internal actual fun Thread.joinNanos(nanos: Long): Boolean = join(nanos)
