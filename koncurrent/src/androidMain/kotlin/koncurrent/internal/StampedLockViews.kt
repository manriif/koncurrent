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

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReadWriteLock

/**
 * The read lock of a stamped lock as a [Lock], which gives no stamp. Its `unlock` releases one
 * hold of the read lock, whichever thread took it. It has no condition, as in the JDK.
 */
internal class StampedReadLockView(private val lock: StampedLockBackend) : Lock {

    override fun lock() {
        val _ = lock.readLock()
    }

    override fun lockInterruptibly() {
        val _ = lock.readLockInterruptibly()
    }

    override fun tryLock(): Boolean = lock.tryReadLock() != 0L

    override fun tryLock(time: Long, unit: TimeUnit): Boolean =
        lock.tryReadLock(unit.toNanos(time)) != 0L

    override fun unlock() {
        if (!lock.tryUnlockRead()) {
            throw IllegalMonitorStateException(NO_READ_LOCK_HELD)
        }
    }

    override fun newCondition(): Condition = throw UnsupportedOperationException()
}

/**
 * The write lock of a stamped lock as a [Lock], which gives no stamp. Its `unlock` releases
 * the write lock, whichever thread took it. It has no condition, as in the JDK.
 */
internal class StampedWriteLockView(private val lock: StampedLockBackend) : Lock {

    override fun lock() {
        val _ = lock.writeLock()
    }

    override fun lockInterruptibly() {
        val _ = lock.writeLockInterruptibly()
    }

    override fun tryLock(): Boolean = lock.tryWriteLock() != 0L

    override fun tryLock(time: Long, unit: TimeUnit): Boolean =
        lock.tryWriteLock(unit.toNanos(time)) != 0L

    override fun unlock() {
        if (!lock.tryUnlockWrite()) {
            throw IllegalMonitorStateException(NO_WRITE_LOCK_HELD)
        }
    }

    override fun newCondition(): Condition = throw UnsupportedOperationException()
}

/**
 * A stamped lock as a [ReadWriteLock], whose two locks are the views above.
 */
internal class StampedReadWriteLockView(private val lock: StampedLockBackend) : ReadWriteLock {

    override fun readLock(): Lock = StampedReadLockView(lock)

    override fun writeLock(): Lock = StampedWriteLockView(lock)
}
