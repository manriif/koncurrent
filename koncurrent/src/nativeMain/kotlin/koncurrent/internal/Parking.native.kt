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
@file:OptIn(ExperimentalNativeApi::class)

package koncurrent.internal

import koncurrent.Thread
import kotlin.concurrent.atomics.AtomicInt
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner

// The thread does not wait, and no wake-up is pending
private const val EMPTY = 0

// The thread waits, or is about to, on its monitor
private const val PARKED = 1

// A wake-up is pending. It makes the next wait return at once.
private const val NOTIFIED = 2

// The longest that one timed wait lasts, a day. It keeps the time given to the system small
// enough for every target. A longer wait returns for no reason, which its caller expects.
private const val LONGEST_WAIT = 86_400_000_000_000L

/**
 * What a thread sleeps on. Each thread has one, which is the only native memory of the locks.
 */
internal class Parker {

    private val state = AtomicInt(EMPTY)
    private val monitor = Monitor()

    @Suppress("unused")
    private val cleaner = createCleaner(monitor) { it.destroy() }

    fun park() {
        // Takes a pending wake-up without any call to the system
        if (state.compareAndSet(NOTIFIED, EMPTY)) {
            return
        }

        monitor.enter()

        if (state.compareAndSet(EMPTY, PARKED)) {
            // Goes back to sleep when woken up for no reason
            while (!state.compareAndSet(NOTIFIED, EMPTY)) {
                monitor.await()
            }
        } else {
            // A wake-up came in the meantime
            state.store(EMPTY)
        }

        monitor.exit()
    }

    /**
     * Waits as [park] does, for [nanos] nanoseconds at most.
     */
    fun park(nanos: Long) {
        if (state.compareAndSet(NOTIFIED, EMPTY) || nanos <= 0L) {
            return
        }

        monitor.enter()

        if (state.compareAndSet(EMPTY, PARKED)) {
            monitor.await(minOf(nanos, LONGEST_WAIT))
        }

        // Whether woken up or not. A wake-up that came in the meantime is taken.
        state.store(EMPTY)
        monitor.exit()
    }

    fun unpark() {
        if (state.exchange(NOTIFIED) != PARKED) {
            return
        }

        // The thread may be between its change of the state and its wait. It holds the monitor
        // until it waits, so entering the monitor makes sure that it hears the signal.
        monitor.enter()
        monitor.exit()
        monitor.signal()
    }
}

internal actual fun park(): Unit = callingThread().parker.park()

internal actual fun parkNanos(nanos: Long): Unit = callingThread().parker.park(nanos)

internal actual fun unpark(thread: Thread): Unit = thread.parker.unpark()

///////////////////////////////////////////////////////////////////////////
// Monitor
///////////////////////////////////////////////////////////////////////////

/**
 * A pthread mutex and its condition. It holds native memory, which [destroy] frees.
 */
internal expect class Monitor() {

    fun enter()

    fun exit()

    /**
     * Releases the monitor and waits for a [signal], then enters the monitor again. May also
     * return for no reason.
     */
    fun await()

    /**
     * Waits as [await] does, for [nanos] nanoseconds at most.
     */
    fun await(nanos: Long)

    fun signal()

    fun destroy()
}
