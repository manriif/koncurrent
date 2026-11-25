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

import koncurrent.internal.PortableStampedLock
import koncurrent.internal.StampedReadLockView
import koncurrent.internal.StampedReadWriteLockView
import koncurrent.internal.StampedWriteLockView

public actual class StampedLock actual constructor() {

    private val lock = PortableStampedLock()

    public actual fun writeLock(): Long = lock.writeLock()

    public actual fun tryWriteLock(): Long = lock.tryWriteLock()

    public actual fun writeLockInterruptibly(): Long = lock.writeLockInterruptibly()

    public actual fun readLock(): Long = lock.readLock()

    public actual fun tryReadLock(): Long = lock.tryReadLock()

    public actual fun readLockInterruptibly(): Long = lock.readLockInterruptibly()

    public actual fun tryOptimisticRead(): Long = lock.tryOptimisticRead()

    public actual fun validate(stamp: Long): Boolean = lock.validate(stamp)

    public actual fun unlockWrite(stamp: Long): Unit = lock.unlockWrite(stamp)

    public actual fun unlockRead(stamp: Long): Unit = lock.unlockRead(stamp)

    public actual fun unlock(stamp: Long): Unit = lock.unlock(stamp)

    public actual fun tryConvertToWriteLock(stamp: Long): Long = lock.tryConvertToWriteLock(stamp)

    public actual fun tryConvertToReadLock(stamp: Long): Long = lock.tryConvertToReadLock(stamp)

    public actual fun tryConvertToOptimisticRead(stamp: Long): Long =
        lock.tryConvertToOptimisticRead(stamp)

    @IgnorableReturnValue
    public actual fun tryUnlockWrite(): Boolean = lock.tryUnlockWrite()

    @IgnorableReturnValue
    public actual fun tryUnlockRead(): Boolean = lock.tryUnlockRead()

    public actual fun isWriteLocked(): Boolean = lock.isWriteLocked()

    public actual fun isReadLocked(): Boolean = lock.isReadLocked()

    public actual fun getReadLockCount(): Int = lock.getReadLockCount()

    public actual fun asReadLock(): Lock = StampedReadLockView(this)

    public actual fun asWriteLock(): Lock = StampedWriteLockView(this)

    public actual fun asReadWriteLock(): ReadWriteLock = StampedReadWriteLockView(this)

    internal fun tryWriteLock(nanos: Long): Long = lock.tryWriteLock(nanos)

    internal fun tryReadLock(nanos: Long): Long = lock.tryReadLock(nanos)
}
