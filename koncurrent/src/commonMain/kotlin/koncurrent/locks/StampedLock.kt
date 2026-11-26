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

import koncurrent.InterruptedException
import koncurrent.internal.tryReadLockWithin
import koncurrent.internal.tryWriteLockWithin
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.time.Duration

/**
 * A lock with three modes: writing, reading and optimistic reading. Each lock that it grants
 * comes with a stamp, a number that releases the lock or turns it into another mode.
 *
 * - **Writing.** [writeLock] waits until no thread holds the lock, then holds it alone.
 * - **Reading.** [readLock] waits while a thread writes. Many threads read at once.
 * - **Optimistic reading.** [tryOptimisticRead] takes no lock and never waits. It returns a
 *   stamp, and [validate] tells later whether a thread wrote since.
 *
 * An optimistic read suits short sections that only read, where writes are rare. It copies
 * what it needs, then validates, and uses the copies only when the stamp is still valid. What
 * it read may be half written until then.
 *
 * ```kotlin
 * var stamp = lock.tryOptimisticRead()
 * var x = point.x
 * var y = point.y
 *
 * if (!lock.validate(stamp)) {
 *     stamp = lock.readLock()
 *
 *     try {
 *         x = point.x
 *         y = point.y
 *     } finally {
 *         lock.unlockRead(stamp)
 *     }
 * }
 * ```
 *
 * The stamp of a lock that was granted is never zero. The functions that try return zero when
 * they fail.
 *
 * [read] and [write] run a block under a lock, and never forget to release it. [asReadLock],
 * [asWriteLock] and [asReadWriteLock] give the lock to code that expects a [Lock] or a
 * [ReadWriteLock], without its stamps.
 *
 * The lock is not reentrant. A thread that holds it and asks for it again waits for itself. It
 * has no owner either, so a thread can release what another one acquired, with its stamp. And
 * it has no condition.
 *
 * A thread that asks for a free lock takes it, even when other threads wait. The lock does not
 * always prefer the readers or the writers.
 *
 * ### Platforms
 *
 * - On the JVM this is `java.util.concurrent.locks.StampedLock`.
 * - On Android this is the platform lock from API 24, where it appeared, and the port of
 *   native below.
 * - On native this is a port of the `StampedLock` of JSR-166.
 * - JS and Wasm have one thread, which never waits. A lock that is not granted at once never
 *   could be, so [writeLock] and [readLock] throw [IllegalStateException] then.
 */
public expect class StampedLock() {

    /**
     * Acquires the write lock, and waits while a thread holds the lock in any mode. An
     * interrupt does not end the wait.
     *
     * @return the stamp that releases the lock or converts it
     */
    public fun writeLock(): Long

    /**
     * Acquires the write lock if no thread holds the lock. Never waits.
     *
     * @return the stamp that releases the lock or converts it, or zero if it was not acquired
     */
    public fun tryWriteLock(): Long

    /**
     * Acquires the write lock as [writeLock] does, unless the thread is interrupted.
     *
     * @return the stamp that releases the lock or converts it
     * @throws InterruptedException if the thread is interrupted before or while it waits
     */
    public fun writeLockInterruptibly(): Long

    /**
     * Acquires the read lock, and waits while a thread holds the write lock. An interrupt does
     * not end the wait.
     *
     * @return the stamp that releases the lock or converts it
     */
    public fun readLock(): Long

    /**
     * Acquires the read lock unless a thread holds the write lock. Never waits.
     *
     * @return the stamp that releases the lock or converts it, or zero if it was not acquired
     */
    public fun tryReadLock(): Long

    /**
     * Acquires the read lock as [readLock] does, unless the thread is interrupted.
     *
     * @return the stamp that releases the lock or converts it
     * @throws InterruptedException if the thread is interrupted before or while it waits
     */
    public fun readLockInterruptibly(): Long

    /**
     * Returns a stamp to [validate] later, or zero if a thread holds the write lock. It takes
     * no lock, so nothing has to be released.
     */
    public fun tryOptimisticRead(): Long

    /**
     * Tells whether no thread acquired the write lock since [stamp] was returned. The stamp of
     * a lock that is still held is valid. Zero never is.
     */
    public fun validate(stamp: Long): Boolean

    /**
     * Releases the write lock that [stamp] was returned for.
     *
     * @throws IllegalMonitorStateException if [stamp] is not the one of the write lock that is
     * held
     */
    public fun unlockWrite(stamp: Long)

    /**
     * Releases one hold of the read lock that [stamp] was returned for.
     *
     * @throws IllegalMonitorStateException if [stamp] is not the one of a read lock that is
     * held
     */
    public fun unlockRead(stamp: Long)

    /**
     * Releases the lock that [stamp] was returned for, in the mode that it has.
     *
     * @throws IllegalMonitorStateException if [stamp] is not the one of a lock that is held
     */
    public fun unlock(stamp: Long)

    /**
     * Turns what [stamp] stands for into the write lock, if that is possible at once.
     *
     * - The stamp of the write lock is returned as it is.
     * - A read lock becomes the write lock when no other thread reads.
     * - An optimistic stamp acquires the write lock when it is still valid and the lock is
     *   free.
     *
     * @return the stamp of the write lock, or zero if nothing changed
     */
    public fun tryConvertToWriteLock(stamp: Long): Long

    /**
     * Turns what [stamp] stands for into a read lock, if that is possible at once.
     *
     * - The write lock becomes a read lock, and no other thread writes in between.
     * - The stamp of a read lock is returned as it is.
     * - An optimistic stamp acquires the read lock when it is still valid.
     *
     * @return the stamp of the read lock, or zero if nothing changed
     */
    public fun tryConvertToReadLock(stamp: Long): Long

    /**
     * Releases the lock that [stamp] stands for, and returns a stamp to [validate] later. An
     * optimistic stamp is returned as it is when it is still valid.
     *
     * @return the optimistic stamp, or zero if [stamp] is not valid
     */
    public fun tryConvertToOptimisticRead(stamp: Long): Long

    /**
     * Releases the write lock if it is held, without its stamp. It serves to recover after an
     * error.
     *
     * @return whether the write lock was held
     */
    @IgnorableReturnValue
    public fun tryUnlockWrite(): Boolean

    /**
     * Releases one hold of the read lock if it is held, without its stamp. It serves to recover
     * after an error.
     *
     * @return whether the read lock was held
     */
    @IgnorableReturnValue
    public fun tryUnlockRead(): Boolean

    /**
     * Tells whether a thread holds the write lock.
     */
    public fun isWriteLocked(): Boolean

    /**
     * Tells whether threads hold the read lock.
     */
    public fun isReadLocked(): Boolean

    /**
     * Returns the number of holds of the read lock. It is a transient value while other
     * threads use the lock.
     */
    public fun getReadLockCount(): Int

    /**
     * Returns a view of this lock as a [Lock] that takes the read lock. Its `lock` is
     * [readLock] without the stamp, and its `unlock` releases one hold of the read lock,
     * whichever thread took it.
     */
    public fun asReadLock(): Lock

    /**
     * Returns a view of this lock as a [Lock] that takes the write lock. Its `lock` is
     * [writeLock] without the stamp, and its `unlock` releases the write lock, whichever
     * thread took it. It is not reentrant.
     */
    public fun asWriteLock(): Lock

    /**
     * Returns a view of this lock as a [ReadWriteLock], whose locks are [asReadLock] and
     * [asWriteLock].
     */
    public fun asReadWriteLock(): ReadWriteLock
}

/**
 * Runs [action] while holding the read lock, and returns its result. The lock is released when
 * [action] returns or throws.
 */
@IgnorableReturnValue
@OptIn(ExperimentalContracts::class)
public inline fun <T> StampedLock.read(action: () -> T): T {
    contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }

    val stamp = readLock()

    try {
        return action()
    } finally {
        unlockRead(stamp)
    }
}

/**
 * Runs [action] while holding the write lock, and returns its result. The lock is released
 * when [action] returns or throws.
 *
 * The lock is not reentrant. A thread that holds it in any mode must not call this, or it
 * waits for itself.
 */
@IgnorableReturnValue
@OptIn(ExperimentalContracts::class)
public inline fun <T> StampedLock.write(action: () -> T): T {
    contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }

    val stamp = writeLock()

    try {
        return action()
    } finally {
        unlockWrite(stamp)
    }
}

/**
 * Acquires the write lock if that is possible within [timeout]. Does not wait when [timeout]
 * is zero or negative.
 *
 * On JS and Wasm this never waits, since no other thread could release the lock.
 *
 * @return the stamp that releases the lock or converts it, or zero if it was not acquired
 * @throws InterruptedException if the thread is interrupted before or while it waits
 */
public fun StampedLock.tryWriteLock(timeout: Duration): Long =
    tryWriteLockWithin(timeout.inWholeNanoseconds)

/**
 * Acquires the read lock if that is possible within [timeout]. Does not wait when [timeout] is
 * zero or negative.
 *
 * On JS and Wasm this never waits, since no other thread could release the lock.
 *
 * @return the stamp that releases the lock or converts it, or zero if it was not acquired
 * @throws InterruptedException if the thread is interrupted before or while it waits
 */
public fun StampedLock.tryReadLock(timeout: Duration): Long =
    tryReadLockWithin(timeout.inWholeNanoseconds)
