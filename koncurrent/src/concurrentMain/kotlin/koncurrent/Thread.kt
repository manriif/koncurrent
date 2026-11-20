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

import koncurrent.internal.callingThread
import koncurrent.internal.clearInterrupt
import koncurrent.internal.joinNanos
import koncurrent.internal.sleepNanos
import kotlin.time.Duration

/**
 * A thread, which runs code at the same time as the other threads of the program.
 *
 * A thread runs its [Runnable] once [start] is called, and ends when that returns. [join] waits
 * for its end. An exception that escapes the [Runnable] ends the thread, which prints the stack
 * trace. Prefer [thread], which creates and starts a thread from a lambda.
 *
 * ### Interruption
 *
 * [interrupt] asks a thread to stop what it is doing. It only sets a flag of the thread. A
 * function of this library that waits throws [InterruptedException] when it finds the flag
 * set, and clears it: [sleep], [join], the waits of a condition, of a semaphore and of a latch,
 * and the ways to take a lock that say so. A thread that waits in one of them is woken up for
 * that. Code that does not wait reads the flag itself, with [isInterrupted] or [interrupted].
 *
 * ### Platforms
 *
 * - On the JVM and Android this is `java.lang.Thread`.
 * - On native this is a thread of the system, with three limits.
 *   - [interrupt] does not wake a thread up from a call to the system, like a read of a file.
 *   - A program ends when its main function returns, whether threads still run or not. So
 *     [setDaemon] changes nothing there.
 *   - [currentThread] also works on a thread that this library did not start, like the main
 *     one. Such a thread never tells that it ended. [isAlive] stays `true`, and [join] waits
 *     until its time is over, or forever.
 * - JS and Wasm have one thread, so this class does not exist there.
 *
 * @param target what the thread runs
 */
public expect class Thread(target: Runnable) {

    /**
     * Makes the thread run. Returns at once, while the thread runs its [Runnable].
     *
     * @throws IllegalThreadStateException if the thread was already started
     */
    public fun start()

    /**
     * Waits for the end of the thread. Returns at once if it has ended, or was never started.
     *
     * @throws InterruptedException if the current thread is interrupted before or while it
     * waits
     */
    public fun join()

    /**
     * Sets the interrupted flag of the thread, and wakes it up if it waits in a function that
     * an interrupt ends.
     */
    public fun interrupt()

    /**
     * Tells whether the interrupted flag of the thread is set. Does not clear it.
     */
    public fun isInterrupted(): Boolean

    /**
     * Tells whether the thread was started and has not ended yet.
     */
    public fun isAlive(): Boolean

    /**
     * Returns the name of the thread. A new thread is named `Thread-` followed by a number.
     */
    public fun getName(): String

    /**
     * Gives the thread another name.
     */
    public fun setName(name: String)

    /**
     * Tells whether the thread is a daemon. See [setDaemon].
     */
    public fun isDaemon(): Boolean

    /**
     * Makes the thread a daemon, or not. The JVM ends a program once only daemon threads are
     * left. To call before [start].
     *
     * @throws IllegalThreadStateException if the thread is alive
     */
    public fun setDaemon(on: Boolean)
}

///////////////////////////////////////////////////////////////////////////
// Functions
///////////////////////////////////////////////////////////////////////////

/**
 * Creates a thread that runs [block], and starts it unless [start] is `false`. It has the name
 * and the defaults of the `thread` function that the standard library has for the JVM.
 *
 * @param isDaemon whether the thread is a daemon, see [Thread.setDaemon]
 * @param name the name of the thread, or `null` to keep the one that it gets
 */
public fun thread(
    start: Boolean = true,
    isDaemon: Boolean = false,
    name: String? = null,
    block: () -> Unit,
): Thread {
    val thread = Thread(block)

    if (isDaemon) {
        thread.setDaemon(true)
    }

    if (name != null) {
        thread.setName(name)
    }

    if (start) {
        thread.start()
    }

    return thread
}

/**
 * Returns the thread that calls this.
 */
public fun currentThread(): Thread = callingThread()

/**
 * Tells whether the current thread is interrupted, and clears its interrupted flag. So a second
 * call returns `false`, unless the thread was interrupted again in between.
 */
public fun interrupted(): Boolean = clearInterrupt()

/**
 * Makes the current thread wait for [duration]. Only checks for an interrupt when [duration]
 * is zero or negative.
 *
 * @throws InterruptedException if the thread is interrupted before or while it waits
 */
public fun sleep(duration: Duration): Unit = sleepNanos(duration.inWholeNanoseconds)

/**
 * Waits for the end of this thread, for [timeout] at most.
 *
 * @return `false` if the thread still runs when the time is over
 * @throws InterruptedException if the current thread is interrupted before or while it waits
 */
public fun Thread.join(timeout: Duration): Boolean = joinNanos(timeout.inWholeNanoseconds)
