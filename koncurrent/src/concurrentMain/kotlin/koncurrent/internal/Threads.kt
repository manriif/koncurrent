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

/**
 * Returns the [Thread] of the calling thread.
 */
internal expect fun callingThread(): Thread

/**
 * Clears the interrupted flag of the calling thread. Returns whether it was set.
 */
internal expect fun clearInterrupt(): Boolean

/**
 * Makes the calling thread wait for [nanos] nanoseconds. Only checks for an interrupt when
 * [nanos] is zero or negative.
 *
 * @throws InterruptedException if the thread is interrupted before or while it waits
 */
internal expect fun sleepNanos(nanos: Long)

/**
 * Waits for the end of this thread, for [nanos] nanoseconds at most. Returns whether it does
 * not run.
 *
 * @throws InterruptedException if the calling thread is interrupted while it waits
 */
internal expect fun Thread.joinNanos(nanos: Long): Boolean
