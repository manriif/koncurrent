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
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * A pair of locks over the same data: a read lock that many threads hold at once, and a write
 * lock that one thread holds alone.
 *
 * It suits data that is read far more often than it is changed. Readers do not wait for each
 * other. A writer waits until no thread reads, and readers wait while a thread writes. Prefer
 * [read] and [write], which never forget to unlock.
 *
 * Both locks are reentrant. The thread that writes can also take the read lock, and keep it
 * after it released the write lock. The opposite is not possible. A thread that holds the read
 * lock must release it before it takes the write lock, or it waits for itself forever. [write]
 * does that for its caller.
 *
 * The lock is not fair unless it is created so. A thread that asks for a free lock then takes
 * it, even when other threads wait. One rule keeps writers from starving. A thread that does
 * not read yet lets a waiting writer pass first. A fair lock goes to the threads in the order
 * of their arrival, which is slower when many threads take it.
 *
 * ### Platforms
 *
 * - On the JVM and Android this is `java.util.concurrent.locks.ReentrantReadWriteLock`.
 * - On native this is a port of the `ReentrantReadWriteLock` of JSR-166.
 * - JS and Wasm have one thread, which never waits. The locks only count their holds there.
 *   Taking the write lock while holding only the read lock throws [IllegalStateException],
 *   since it could never succeed.
 */
public expect class ReentrantReadWriteLock() : ReadWriteLock {

    /**
     * Creates a lock that is [fair] or not.
     */
    public constructor(fair: Boolean)

    /**
     * Returns the lock used for reading. Many threads hold it at once.
     */
    override fun readLock(): ReadLock

    /**
     * Returns the lock used for writing. One thread holds it, while no other thread reads.
     */
    override fun writeLock(): WriteLock

    /**
     * Returns the number of holds that the current thread has on the read lock.
     */
    public fun getReadHoldCount(): Int

    /**
     * Returns the number of holds that the current thread has on the write lock.
     */
    public fun getWriteHoldCount(): Int

    /**
     * Tells whether this lock is fair.
     */
    public fun isFair(): Boolean

    /**
     * The lock returned by [ReentrantReadWriteLock.readLock]. Common code cannot name this
     * type. Declare it as a [Lock].
     */
    public class ReadLock : Lock {

        /**
         * Acquires the read lock, and waits while another thread holds the write lock. An
         * interrupt does not end the wait.
         */
        override fun lock()

        /**
         * Acquires the read lock as [lock] does, unless the thread is interrupted.
         *
         * @throws InterruptedException if the thread is interrupted before or while it waits
         */
        override fun lockInterruptibly()

        /**
         * Acquires the read lock unless another thread holds the write lock. Never waits.
         *
         * @return whether the lock was acquired
         */
        override fun tryLock(): Boolean

        /**
         * Releases one hold of the read lock.
         *
         * @throws IllegalMonitorStateException if the current thread does not hold the read
         * lock
         */
        override fun unlock()
    }

    /**
     * The lock returned by [ReentrantReadWriteLock.writeLock]. Common code cannot name this
     * type. Declare it as a [Lock].
     */
    public class WriteLock : Lock {

        /**
         * Acquires the write lock, and waits while other threads hold the read lock or the
         * write lock. An interrupt does not end the wait.
         */
        override fun lock()

        /**
         * Acquires the write lock as [lock] does, unless the thread is interrupted.
         *
         * @throws InterruptedException if the thread is interrupted before or while it waits
         */
        override fun lockInterruptibly()

        /**
         * Acquires the write lock if no other thread holds the read lock or the write lock.
         * Never waits.
         *
         * @return whether the lock was acquired
         */
        override fun tryLock(): Boolean

        /**
         * Releases one hold of the write lock.
         *
         * @throws IllegalMonitorStateException if the current thread does not hold the write
         * lock
         */
        override fun unlock()
    }
}

/**
 * Runs [action] while holding the read lock, and returns its result. The lock is released when
 * [action] returns or throws.
 */
@IgnorableReturnValue
@OptIn(ExperimentalContracts::class)
public inline fun <T> ReentrantReadWriteLock.read(action: () -> T): T {
    contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }

    val readLock = readLock()
    readLock.lock()

    try {
        return action()
    } finally {
        readLock.unlock()
    }
}

/**
 * Runs [action] while holding the write lock, and returns its result. The lock is released
 * when [action] returns or throws.
 *
 * A thread that holds the read lock cannot take the write lock. So this first releases the
 * holds that the current thread has on the read lock, and takes them back at the end. Other
 * threads may write in between. What [action] relies on must therefore be checked again inside
 * it.
 */
@IgnorableReturnValue
@OptIn(ExperimentalContracts::class)
public inline fun <T> ReentrantReadWriteLock.write(action: () -> T): T {
    contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }

    val readLock = readLock()
    val readHolds = if (getWriteHoldCount() == 0) getReadHoldCount() else 0

    repeat(readHolds) { readLock.unlock() }

    val writeLock = writeLock()
    writeLock.lock()

    try {
        return action()
    } finally {
        repeat(readHolds) { readLock.lock() }
        writeLock.unlock()
    }
}
