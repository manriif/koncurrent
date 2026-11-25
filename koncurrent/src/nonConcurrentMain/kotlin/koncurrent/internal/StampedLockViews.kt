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

import koncurrent.locks.IllegalMonitorStateException
import koncurrent.locks.Lock
import koncurrent.locks.ReadWriteLock
import koncurrent.locks.StampedLock

// The views of native, for one thread. Nothing interrupts it, and a try with a time limit is
// the plain one, which the interface gives on these targets.

/**
 * The read lock of a [StampedLock] as a [Lock], which gives no stamp. Its `unlock` releases one
 * hold of the read lock.
 */
internal class StampedReadLockView(private val lock: StampedLock) : Lock {

    override fun lock() {
        val _ = lock.readLock()
    }

    override fun lockInterruptibly(): Unit = lock()

    override fun tryLock(): Boolean = lock.tryReadLock() != 0L

    override fun unlock() {
        if (!lock.tryUnlockRead()) {
            throw IllegalMonitorStateException(NO_READ_LOCK_HELD)
        }
    }
}

/**
 * The write lock of a [StampedLock] as a [Lock], which gives no stamp.
 */
internal class StampedWriteLockView(private val lock: StampedLock) : Lock {

    override fun lock() {
        val _ = lock.writeLock()
    }

    override fun lockInterruptibly(): Unit = lock()

    override fun tryLock(): Boolean = lock.tryWriteLock() != 0L

    override fun unlock() {
        if (!lock.tryUnlockWrite()) {
            throw IllegalMonitorStateException(NO_WRITE_LOCK_HELD)
        }
    }
}

/**
 * A [StampedLock] as a [ReadWriteLock], whose two locks are the views above.
 */
internal class StampedReadWriteLockView(private val lock: StampedLock) : ReadWriteLock {

    override fun readLock(): Lock = lock.asReadLock()

    override fun writeLock(): Lock = lock.asWriteLock()
}
