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
import kotlin.time.TimeSource

/**
 * Blocks the calling thread until [unpark] is called for it. An [unpark] called before is not
 * lost, the next [park] then returns at once. This may also return for no reason.
 */
internal expect fun park()

/**
 * Blocks the calling thread as [park] does, for [nanos] nanoseconds at most.
 */
internal expect fun parkNanos(nanos: Long)

internal expect fun unpark(thread: Thread)

///////////////////////////////////////////////////////////////////////////
// Time
///////////////////////////////////////////////////////////////////////////

private val Origin = TimeSource.Monotonic.markNow()

/**
 * Returns the nanoseconds elapsed since a moment that never changes. It never goes back.
 */
internal fun nanoTime(): Long = Origin.elapsedNow().inWholeNanoseconds

/**
 * Returns the [nanoTime] at which a wait of [nanos] nanoseconds that starts now is over. A wait
 * too long to be counted never ends.
 */
internal fun deadlineAfter(nanos: Long): Long {
    val now = nanoTime()

    return if (nanos > Long.MAX_VALUE - now) Long.MAX_VALUE else now + nanos
}
