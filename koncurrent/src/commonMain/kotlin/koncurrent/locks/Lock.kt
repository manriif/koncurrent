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
package koncurrent.locks

import koncurrent.InternalForInheritanceKoncurrentApi
import koncurrent.InterruptedException
import koncurrent.internal.tryLockWithin
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.time.Duration

/**
 * A lock that a thread takes with [lock] and gives back with [unlock].
 *
 * It is the common type of the locks of this package: [ReentrantLock], the two locks of a
 * [ReentrantReadWriteLock], and the views of a [StampedLock]. Use [withLock] to run code while
 * holding any of them.
 *
 * This interface is not meant to be implemented outside of this library. On the JVM and Android
 * it is `java.util.concurrent.locks.Lock`, which has more functions to implement.
 */
@SubclassOptInRequired(InternalForInheritanceKoncurrentApi::class)
public expect interface Lock {

    /**
     * Acquires the lock, and waits while it cannot be acquired. An interrupt does not end the
     * wait.
     */
    public fun lock()

    /**
     * Acquires the lock as [lock] does, unless the thread is interrupted.
     *
     * @throws InterruptedException if the thread is interrupted before or while it waits
     */
    public fun lockInterruptibly()

    /**
     * Acquires the lock if that is possible at once. Never waits.
     *
     * @return whether the lock was acquired
     */
    public fun tryLock(): Boolean

    /**
     * Releases the lock.
     *
     * @throws IllegalMonitorStateException if the current thread does not hold the lock
     */
    public fun unlock()
}

/**
 * Acquires the lock if that is possible within [timeout]. Does not wait when [timeout] is zero
 * or negative.
 *
 * On JS and Wasm this never waits, since no other thread could release the lock.
 *
 * @return whether the lock was acquired
 * @throws InterruptedException if the thread is interrupted before or while it waits
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER") // A member on native, JS and Wasm, which this calls
public fun Lock.tryLock(timeout: Duration): Boolean = tryLockWithin(timeout.inWholeNanoseconds)

/**
 * Runs [action] while holding this lock, and returns its result. The lock is released when
 * [action] returns or throws.
 */
@IgnorableReturnValue
@OptIn(ExperimentalContracts::class)
public inline fun <T> Lock.withLock(action: () -> T): T {
    contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }

    lock()

    try {
        return action()
    } finally {
        unlock()
    }
}
