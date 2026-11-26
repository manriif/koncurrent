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
 * Ported from the Semaphore of JSR-166, which was written by Doug Lea with assistance from
 * members of JCP JSR-166 Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package koncurrent.internal

import koncurrent.InterruptedException

/**
 * The `Semaphore` of JSR-166 in common Kotlin. It backs the public semaphore where the platform
 * has none.
 *
 * The state is the number of permits. A thread that asks for a permit takes one if there is
 * any, even when other threads wait in the queue, unless the semaphore is [fair]. The permits
 * of a fair semaphore go to the threads in the order of their arrival.
 */
internal class PortableSemaphore(permits: Int, val fair: Boolean = false) : Synchronizer() {

    init {
        state.store(permits)
    }

    /**
     * @throws InterruptedException if the thread is interrupted before or while it waits
     */
    fun acquire() {
        if (clearInterrupt()) {
            throw InterruptedException()
        }

        if (!tryAcquire(fair)) {
            acquireContended()
        }
    }

    // Apart from acquire on purpose. Synchronizer.acquire tells why.
    private fun acquireContended() {
        if (fair) {
            acquireInTurn()
        } else {
            acquire(shared = true, interruptible = true) { tryAcquire() }
        }
    }

    // A thread that finds no permit for it joins the queue at once. The tries that it first
    // makes for a semaphore that is not fair would make it lose its turn.
    private fun acquireInTurn() {
        val _ = acquireQueued(null, shared = true, interruptible = true, timed = false, 0L) {
            tryAcquire(fair)
        }
    }

    /**
     * Takes a permit if there is one within [nanos] nanoseconds.
     *
     * @throws InterruptedException if the thread is interrupted before or while it waits
     */
    fun tryAcquire(nanos: Long): Boolean {
        if (clearInterrupt()) {
            throw InterruptedException()
        }

        if (tryAcquire(fair)) {
            return true
        }

        if (nanos <= 0L) {
            return false
        }

        return acquireContended(deadlineAfter(nanos))
    }

    // Apart from tryAcquire on purpose. Synchronizer.acquire tells why.
    private fun acquireContended(deadline: Long): Boolean {
        return acquireQueued(null, shared = true, interruptible = true, timed = true, deadline) {
            tryAcquire(fair)
        }
    }

    fun tryAcquire(): Boolean = tryAcquire(inTurn = false)

    // [inTurn] tells to leave the permits to the threads that have waited longer, which a fair
    // semaphore does. The queue is read again after a lost update, as in the original. Inline,
    // so that a try that takes no turn does not look at [inTurn].
    @Suppress("NOTHING_TO_INLINE")
    private inline fun tryAcquire(inTurn: Boolean): Boolean {
        while (true) {
            if (inTurn && hasQueuedPredecessors()) {
                return false
            }

            val available = state.load()

            if (available <= 0) {
                return false
            }

            if (state.compareAndSet(available, available - 1)) {
                return true
            }
        }
    }

    fun release() {
        while (true) {
            val current = state.load()

            if (current == Int.MAX_VALUE) {
                throw Error("Maximum permit count exceeded")
            }

            if (state.compareAndSet(current, current + 1)) {
                signalNext()
                return
            }
        }
    }

    fun availablePermits(): Int = state.load()
}
