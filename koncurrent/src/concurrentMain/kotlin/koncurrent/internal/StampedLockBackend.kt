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

/**
 * A stamped lock with the functions of the public one. Its time limits are in nanoseconds. The
 * port implements it, and so does the platform lock on Android.
 */
internal interface StampedLockBackend {

    fun writeLock(): Long

    fun tryWriteLock(): Long

    fun tryWriteLock(nanos: Long): Long

    fun writeLockInterruptibly(): Long

    fun readLock(): Long

    fun tryReadLock(): Long

    fun tryReadLock(nanos: Long): Long

    fun readLockInterruptibly(): Long

    fun tryOptimisticRead(): Long

    fun validate(stamp: Long): Boolean

    fun unlockWrite(stamp: Long)

    fun unlockRead(stamp: Long)

    fun unlock(stamp: Long)

    fun tryConvertToWriteLock(stamp: Long): Long

    fun tryConvertToReadLock(stamp: Long): Long

    fun tryConvertToOptimisticRead(stamp: Long): Long

    @IgnorableReturnValue
    fun tryUnlockWrite(): Boolean

    @IgnorableReturnValue
    fun tryUnlockRead(): Boolean

    fun isWriteLocked(): Boolean

    fun isReadLocked(): Boolean

    fun getReadLockCount(): Int
}
