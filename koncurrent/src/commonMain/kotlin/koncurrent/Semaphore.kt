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

import koncurrent.internal.tryAcquireWithin
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.time.Duration

/**
 * A count of permits that threads take and give back. It limits how many threads use a
 * resource at once, like the connections of a pool.
 *
 * [acquire] takes a permit, and waits while there is none. [release] gives one back, and wakes
 * up a waiting thread. Prefer [withPermit], which never forgets to release.
 *
 * A permit does not belong to a thread. Any thread can release, whether it acquired or not. So
 * a semaphore also serves as a signal between threads: one waits in [acquire] until another
 * calls [release].
 *
 * The semaphore is not fair unless it is created so. A thread that asks for a permit then
 * takes one if there is any, even when other threads wait. The permits of a fair semaphore go
 * to the threads in the order of their arrival.
 *
 * ### Platforms
 *
 * - On the JVM and Android this is `java.util.concurrent.Semaphore`.
 * - On native this is a port of the `Semaphore` of JSR-166.
 * - JS and Wasm have one thread. An [acquire] without a permit could never end, so it throws
 *   [IllegalStateException] there.
 *
 * @param permits the number of permits at the start. It may be negative, in which case
 * releases must come before any permit can be acquired.
 */
public expect class Semaphore(permits: Int) {

    /**
     * Creates a semaphore of [permits] permits that is [fair] or not.
     */
    public constructor(permits: Int, fair: Boolean)

    /**
     * Takes a permit, and waits while there is none.
     *
     * @throws InterruptedException if the thread is interrupted before or while it waits
     */
    public fun acquire()

    /**
     * Takes a permit if there is one. Never waits. It takes it even when other threads wait
     * for one, and even when the semaphore is fair.
     *
     * @return whether a permit was taken
     */
    public fun tryAcquire(): Boolean

    /**
     * Gives a permit back, and wakes up a thread that waits for one, if any. The calling
     * thread does not need to have acquired a permit.
     */
    public fun release()

    /**
     * Returns the number of permits that can be taken now. It is a transient value while other
     * threads use the semaphore.
     */
    public fun availablePermits(): Int

    /**
     * Tells whether this semaphore is fair.
     */
    public fun isFair(): Boolean
}

/**
 * Takes a permit if there is one within [timeout]. Does not wait when [timeout] is zero or
 * negative.
 *
 * On JS and Wasm this never waits, since no other thread could release a permit.
 *
 * @return whether a permit was taken
 * @throws InterruptedException if the thread is interrupted before or while it waits
 */
public fun Semaphore.tryAcquire(timeout: Duration): Boolean =
    tryAcquireWithin(timeout.inWholeNanoseconds)

/**
 * Runs [action] while holding a permit of this semaphore, and returns its result. The permit
 * is released when [action] returns or throws.
 *
 * @throws InterruptedException if the thread is interrupted before or while it waits for the
 * permit
 */
@IgnorableReturnValue
@OptIn(ExperimentalContracts::class)
public inline fun <T> Semaphore.withPermit(action: () -> T): T {
    contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }

    acquire()

    try {
        return action()
    } finally {
        release()
    }
}
