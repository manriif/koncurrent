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

import koncurrent.Thread

private const val NANOS_PER_MILLI = 1_000_000L

internal actual fun callingThread(): Thread = Thread.currentThread()

internal actual fun clearInterrupt(): Boolean = Thread.interrupted()

// The Java functions take the milliseconds, then the nanoseconds that are left

internal actual fun sleepNanos(nanos: Long) {
    val time = nanos.coerceAtLeast(0L)

    Thread.sleep(time / NANOS_PER_MILLI, (time % NANOS_PER_MILLI).toInt())
}

internal actual fun Thread.joinNanos(nanos: Long): Boolean {
    // Java waits without end for a time of zero
    if (nanos > 0L) {
        join(nanos / NANOS_PER_MILLI, (nanos % NANOS_PER_MILLI).toInt())
    }

    return !isAlive
}
