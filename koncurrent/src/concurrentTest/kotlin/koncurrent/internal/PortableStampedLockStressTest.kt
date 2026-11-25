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

import koncurrent.locks.StampedLockConcurrencyContractTest
import koncurrent.locks.StampedLockContractTest
import koncurrent.locks.TestStampedLock
import kotlin.time.Duration

/**
 * Returns a lock behind the public one as a stamped lock under test.
 */
internal fun StampedLockBackend.asTestStampedLock(): TestStampedLock {
    val lock = this

    return object : TestStampedLock {

        override fun writeLock() = lock.writeLock()

        override fun tryWriteLock() = lock.tryWriteLock()

        override fun tryWriteLock(timeout: Duration) = lock.tryWriteLock(timeout.inWholeNanoseconds)

        override fun writeLockInterruptibly() = lock.writeLockInterruptibly()

        override fun readLock() = lock.readLock()

        override fun tryReadLock() = lock.tryReadLock()

        override fun tryReadLock(timeout: Duration) = lock.tryReadLock(timeout.inWholeNanoseconds)

        override fun readLockInterruptibly() = lock.readLockInterruptibly()

        override fun tryOptimisticRead() = lock.tryOptimisticRead()

        override fun validate(stamp: Long) = lock.validate(stamp)

        override fun unlockWrite(stamp: Long) = lock.unlockWrite(stamp)

        override fun unlockRead(stamp: Long) = lock.unlockRead(stamp)

        override fun unlock(stamp: Long) = lock.unlock(stamp)

        override fun tryConvertToWriteLock(stamp: Long) = lock.tryConvertToWriteLock(stamp)

        override fun tryConvertToReadLock(stamp: Long) = lock.tryConvertToReadLock(stamp)

        override fun tryConvertToOptimisticRead(stamp: Long) =
            lock.tryConvertToOptimisticRead(stamp)

        override fun tryUnlockWrite() = lock.tryUnlockWrite()

        override fun tryUnlockRead() = lock.tryUnlockRead()

        override fun isWriteLocked() = lock.isWriteLocked()

        override fun isReadLocked() = lock.isReadLocked()

        override fun getReadLockCount() = lock.getReadLockCount()
    }
}

/**
 * Tests [PortableStampedLock] on one thread, on the JVM too where it backs nothing.
 */
class PortableStampedLockTest : StampedLockContractTest() {

    override fun newLock(): TestStampedLock = PortableStampedLock().asTestStampedLock()
}

/**
 * Tests [PortableStampedLock] on real threads, on the JVM too where it backs nothing.
 */
class PortableStampedLockStressTest : StampedLockConcurrencyContractTest() {

    override fun newLock(): TestStampedLock = PortableStampedLock().asTestStampedLock()
}
