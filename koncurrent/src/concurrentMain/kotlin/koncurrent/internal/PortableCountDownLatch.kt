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
/*
 * Ported from the CountDownLatch of JSR-166, which was written by Doug Lea with assistance from
 * members of JCP JSR-166 Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package koncurrent.internal

import koncurrent.InterruptedException

/**
 * The `CountDownLatch` of JSR-166 in common Kotlin. It backs the public latch where the
 * platform has none.
 *
 * The state is the count. The threads that wait for it to reach zero all acquire together
 * then, each one waking up the next.
 */
internal class PortableCountDownLatch(count: Int) : Synchronizer() {

    init {
        require(count >= 0) { "count < 0" }

        state.store(count)
    }

    /**
     * @throws InterruptedException if the thread is interrupted before or while it waits
     */
    fun await() {
        if (clearInterrupt()) {
            throw InterruptedException()
        }

        if (state.load() != 0) {
            awaitZero()
        }
    }

    // Apart from await on purpose. Synchronizer.acquire tells why.
    private fun awaitZero() {
        acquire(shared = true, interruptible = true) { state.load() == 0 }
    }

    /**
     * Waits for the count to be zero, for [nanos] nanoseconds at most.
     *
     * @return whether the count is zero
     * @throws InterruptedException if the thread is interrupted before or while it waits
     */
    fun await(nanos: Long): Boolean {
        if (clearInterrupt()) {
            throw InterruptedException()
        }

        if (state.load() == 0) {
            return true
        }

        if (nanos <= 0L) {
            return false
        }

        return awaitZero(deadlineAfter(nanos))
    }

    // Apart from await on purpose. Synchronizer.acquire tells why.
    private fun awaitZero(deadline: Long): Boolean {
        return acquireQueued(null, shared = true, interruptible = true, timed = true, deadline) {
            state.load() == 0
        }
    }

    fun countDown() {
        while (true) {
            val count = state.load()

            if (count == 0) {
                return
            }

            if (state.compareAndSet(count, count - 1)) {
                if (count == 1) {
                    signalNext()
                }

                return
            }
        }
    }

    fun count(): Int = state.load()
}
