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

import koncurrent.internal.awaitWithin
import kotlin.time.Duration

/**
 * A gate that opens once a number of events happened. Threads wait at it until other threads
 * have counted down to zero.
 *
 * [countDown] takes one from the count. [await] waits until the count is zero, then returns,
 * as does every later call. The count cannot be set again. A latch is used once.
 *
 * It serves to wait for the end of several tasks, with a count of their number, or to start
 * several threads at the same moment, with a count of one.
 *
 * ### Platforms
 *
 * - On the JVM and Android this is `java.util.concurrent.CountDownLatch`.
 * - On native this is a port of the `CountDownLatch` of JSR-166.
 * - JS and Wasm have one thread. An [await] before the count is zero could never end, so it
 *   throws [IllegalStateException] there.
 *
 * @param count the number of calls to [countDown] that open the gate
 * @throws IllegalArgumentException if [count] is negative
 */
public expect class CountDownLatch(count: Int) {

    /**
     * Waits until the count is zero. Returns at once if it already is.
     *
     * @throws InterruptedException if the thread is interrupted before or while it waits
     */
    public fun await()

    /**
     * Takes one from the count, and wakes up all the waiting threads when it reaches zero.
     * Does nothing if the count is already zero.
     */
    public fun countDown()

    /**
     * Returns the current count.
     */
    public fun getCount(): Long
}

/**
 * Waits until the count is zero, for [timeout] at most. Does not wait when [timeout] is zero
 * or negative.
 *
 * On JS and Wasm this never waits, since no other thread could count down.
 *
 * @return whether the count is zero
 * @throws InterruptedException if the thread is interrupted before or while it waits
 */
public fun CountDownLatch.await(timeout: Duration): Boolean =
    awaitWithin(timeout.inWholeNanoseconds)
