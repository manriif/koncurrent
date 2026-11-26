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
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.time.TimeSource

/**
 * The lock that a port of common code guards itself with.
 *
 * It is the lock port where there are threads, whose `lock` and `unlock` are inline. The public
 * lock costs a call for each of them, which was measured to take a third of the speed of a
 * queue on native. It is nothing where there is one thread.
 */
internal expect class InternalLock() {

    fun lock()

    /**
     * @throws InterruptedException if the thread is interrupted before it holds the lock
     */
    fun lockInterruptibly()

    /**
     * Takes the lock if it is free, without waiting. Returns whether it did.
     */
    fun tryLock(): Boolean

    fun unlock()

    fun newCondition(): InternalCondition
}

/**
 * A condition of an [InternalLock], which a thread that holds the lock waits on.
 */
internal expect class InternalCondition {

    /**
     * @throws InterruptedException if the thread is interrupted before a signal
     * @throws IllegalStateException where there is one thread, which nothing could signal
     */
    fun await()

    /**
     * Waits for [nanos] nanoseconds at most. Returns `false` if the time ran out before a
     * signal, which it does at once where there is one thread.
     *
     * @throws InterruptedException if the thread is interrupted before a signal
     */
    fun await(nanos: Long): Boolean

    fun signal()

    fun signalAll()
}

/**
 * Returns a lock that is [fair] or not.
 */
internal expect fun InternalLock(fair: Boolean): InternalLock

/**
 * Runs [action] with this lock held.
 */
@OptIn(ExperimentalContracts::class)
internal inline fun <T> InternalLock.locked(action: () -> T): T {
    contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }

    lock()

    try {
        return action()
    } finally {
        unlock()
    }
}

/**
 * Runs [action] with this lock held. The thread gives up waiting for the lock when it is
 * interrupted.
 *
 * @throws InterruptedException if the thread is interrupted before it holds the lock
 */
@OptIn(ExperimentalContracts::class)
internal inline fun <T> InternalLock.lockedInterruptibly(action: () -> T): T {
    contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }

    lockInterruptibly()

    try {
        return action()
    } finally {
        unlock()
    }
}

/**
 * Waits on this condition for [nanos] nanoseconds at most.
 *
 * @return the nanoseconds of [nanos] that are left. Zero or less tells that the time ran out.
 * @throws InterruptedException if the thread is interrupted before it is signalled
 */
internal fun InternalCondition.awaitRemaining(nanos: Long): Long {
    val start = TimeSource.Monotonic.markNow()

    // A wait that ran out of time leaves none, whatever the clock says. JS and Wasm answer so
    // at once, and a caller that found time left there would wait in a loop.
    if (!await(nanos)) {
        return 0L
    }

    return nanos - start.elapsedNow().inWholeNanoseconds
}
