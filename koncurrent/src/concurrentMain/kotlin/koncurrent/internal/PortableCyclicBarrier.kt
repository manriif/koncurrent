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
 * Ported from the CyclicBarrier of JSR-166, which was written by Doug Lea with assistance from
 * members of JCP JSR-166 Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package koncurrent.internal

import koncurrent.BrokenBarrierException
import koncurrent.InterruptedException
import koncurrent.Runnable
import koncurrent.TimeoutException

/**
 * The `CyclicBarrier` of JSR-166 in common Kotlin. It backs the public barrier where the
 * platform has none.
 *
 * One lock guards it, and its threads wait on one condition of that lock. Each use of the
 * barrier is a [Generation], which ends when the barrier opens, breaks or is reset. A thread
 * that wakes up tells from the generation of its arrival which one happened.
 */
internal class PortableCyclicBarrier(val parties: Int, private val barrierAction: Runnable?) {

    private val lock = PortableReentrantLock()
    private val trip = lock.newCondition()

    // Guarded by the lock, as the count is
    private var generation = Generation()

    // The parties that the barrier still waits for
    private var count = parties

    init {
        require(parties > 0) { "parties must be positive, was $parties" }
    }

    /**
     * Waits until all the parties have arrived, and returns the arrival index of the thread.
     *
     * @throws InterruptedException if the thread is interrupted before or while it waits
     * @throws BrokenBarrierException if the barrier is broken, or breaks or is reset meanwhile
     */
    fun await(): Int = await(timed = false, nanos = 0L)

    /**
     * Waits as [await] does, for [nanos] nanoseconds at most.
     *
     * @throws TimeoutException if the time runs out, which breaks the barrier
     */
    fun await(nanos: Long): Int = await(timed = true, nanos)

    private fun await(timed: Boolean, nanos: Long): Int = locked {
        val arrival = generation

        if (arrival.broken) {
            throw BrokenBarrierException()
        }

        if (clearInterrupt()) {
            breakBarrier()
            throw InterruptedException()
        }

        val index = --count

        if (index == 0) {
            runAction()
            nextGeneration()
        } else {
            awaitOthers(arrival, timed, nanos)
        }

        index
    }

    // The barrier breaks when its action fails
    private fun runAction() {
        try {
            barrierAction?.run()
        } catch (failure: Throwable) {
            breakBarrier()
            throw failure
        }
    }

    // Waits until the generation of the [arrival] ends. Returns when the barrier opened, and
    // throws when it broke, on this thread or on another one.
    private fun awaitOthers(arrival: Generation, timed: Boolean, nanos: Long) {
        val deadline = if (timed) deadlineAfter(nanos) else 0L
        var remaining = nanos

        while (true) {
            try {
                if (!timed) {
                    trip.await()
                } else if (remaining > 0L) {
                    remaining = if (trip.await(remaining)) deadline - nanoTime() else 0L
                }
            } catch (interrupt: InterruptedException) {
                if (arrival !== generation || arrival.broken) {
                    // The wait was over anyway. The interrupt is kept for what the thread
                    // does next.
                    callingThread().interrupt()
                } else {
                    breakBarrier()
                    throw interrupt
                }
            }

            if (arrival.broken) {
                throw BrokenBarrierException()
            }

            if (arrival !== generation) {
                return
            }

            if (timed && remaining <= 0L) {
                breakBarrier()
                throw TimeoutException()
            }
        }
    }

    fun isBroken(): Boolean = locked { generation.broken }

    /**
     * Breaks the barrier for the threads that wait at it, then puts it back in its first
     * state.
     */
    fun reset(): Unit = locked {
        breakBarrier()
        nextGeneration()
    }

    fun numberWaiting(): Int = locked { parties - count }

    // Lets the threads of the generation go on, and starts the next one. The lock is held.
    private fun nextGeneration() {
        trip.signalAll()
        count = parties
        generation = Generation()
    }

    // Ends the generation as broken, and wakes its threads up. The lock is held.
    private fun breakBarrier() {
        generation.broken = true
        count = parties
        trip.signalAll()
    }

    private inline fun <T> locked(action: () -> T): T {
        lock.lock()

        try {
            return action()
        } finally {
            lock.unlock()
        }
    }
}

/**
 * One use of a barrier, from the first thread that arrives until the barrier opens, breaks or
 * is reset.
 */
private class Generation {

    var broken = false
}
