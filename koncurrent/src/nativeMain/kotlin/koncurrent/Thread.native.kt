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

import koncurrent.internal.InterruptedCount
import koncurrent.internal.Parker
import koncurrent.internal.PortableCountDownLatch
import koncurrent.internal.startThread
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.concurrent.atomics.incrementAndFetch

private const val NEW = 0
private const val STARTED = 1
private const val ENDED = 2

private const val ALREADY_STARTED = "The thread was already started"

// Numbers the threads, for their names
private val ThreadCount = AtomicInt(0)

public actual class Thread private constructor(private var target: Runnable?, state: Int) {

    internal val parker = Parker()

    private val state = AtomicInt(state)
    private val interrupted = AtomicInt(0)
    private val end = PortableCountDownLatch(1)

    @Volatile
    private var name = "Thread-" + ThreadCount.fetchAndIncrement()

    @Volatile
    private var daemon = false

    public actual constructor(target: Runnable) : this(target, NEW)

    // For a thread that the library did not start. It already runs.
    internal constructor() : this(null, STARTED)

    public actual fun start() {
        if (!state.compareAndSet(NEW, STARTED)) {
            throw IllegalThreadStateException(ALREADY_STARTED)
        }

        if (!startThread(this)) {
            state.store(NEW)
            throw OutOfMemoryError("The system could not create a thread")
        }
    }

    public actual fun join() {
        if (state.load() == STARTED) {
            end.await()
        }
    }

    public actual fun interrupt() {
        if (interrupted.compareAndSet(0, 1)) {
            val _ = InterruptedCount.incrementAndFetch()
        }

        parker.unpark()
    }

    public actual fun isInterrupted(): Boolean = interrupted.load() != 0

    public actual fun isAlive(): Boolean = state.load() == STARTED

    public actual fun getName(): String = name

    public actual fun setName(name: String) {
        this.name = name
    }

    public actual fun isDaemon(): Boolean = daemon

    public actual fun setDaemon(on: Boolean) {
        if (isAlive()) {
            throw IllegalThreadStateException(ALREADY_STARTED)
        }

        daemon = on
    }

    /**
     * Runs the thread until its end. The new thread of the system calls this.
     */
    internal fun run() {
        try {
            target?.run()
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
        } finally {
            val _ = clearInterrupt()

            // Lets go of what the thread ran, for those that keep this object
            target = null
            state.store(ENDED)
            end.countDown()
        }
    }

    /**
     * Waits for the end of the thread, for [nanos] nanoseconds at most. Returns whether it
     * does not run.
     */
    internal fun join(nanos: Long): Boolean {
        if (state.load() != STARTED) {
            return true
        }

        return nanos > 0L && end.await(nanos)
    }

    /**
     * Clears the interrupted flag. Returns whether it was set.
     */
    internal fun clearInterrupt(): Boolean {
        if (!interrupted.compareAndSet(1, 0)) {
            return false
        }

        val _ = InterruptedCount.decrementAndFetch()

        return true
    }
}
