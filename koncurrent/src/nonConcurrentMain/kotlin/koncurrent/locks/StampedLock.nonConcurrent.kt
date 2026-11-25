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

import koncurrent.internal.ENDLESS_WAIT
import koncurrent.internal.STAMP_NOT_HELD
import koncurrent.internal.StampedReadLockView
import koncurrent.internal.StampedReadWriteLockView
import koncurrent.internal.StampedWriteLockView

// The state counts the write locks that were released, above its two low bits. The first bit is
// set while the write lock is held, and in its stamp. The second one is only set in the stamp
// of a read lock.
private const val WRITE_BIT = 1L
private const val READ_BIT = 2L
private const val WRITE_UNIT = 4L

// JS and Wasm have one thread, which never waits. The lock keeps its modes and its stamps, so
// that it fails where it does on the other platforms.
public actual class StampedLock actual constructor() {

    private var state = WRITE_UNIT
    private var readers = 0

    // No other thread could release the lock, so the wait would never end
    public actual fun writeLock(): Long {
        val stamp = tryWriteLock()

        check(stamp != 0L) { ENDLESS_WAIT }
        return stamp
    }

    public actual fun tryWriteLock(): Long {
        if (isWriteLocked() || readers != 0) {
            return 0L
        }

        state = state or WRITE_BIT
        return state
    }

    public actual fun writeLockInterruptibly(): Long = writeLock()

    public actual fun readLock(): Long {
        val stamp = tryReadLock()

        check(stamp != 0L) { ENDLESS_WAIT }
        return stamp
    }

    public actual fun tryReadLock(): Long {
        if (isWriteLocked()) {
            return 0L
        }

        readers++
        return state or READ_BIT
    }

    public actual fun readLockInterruptibly(): Long = readLock()

    public actual fun tryOptimisticRead(): Long = if (isWriteLocked()) 0L else state

    public actual fun validate(stamp: Long): Boolean = isOfThisState(stamp)

    public actual fun unlockWrite(stamp: Long) {
        if (!isWriteStamp(stamp) || stamp != state) {
            throw IllegalMonitorStateException(STAMP_NOT_HELD)
        }

        releaseWrite()
    }

    public actual fun unlockRead(stamp: Long) {
        if (!isReadStamp(stamp) || !isOfThisState(stamp) || readers == 0) {
            throw IllegalMonitorStateException(STAMP_NOT_HELD)
        }

        readers--
    }

    public actual fun unlock(stamp: Long) {
        if (isWriteStamp(stamp)) {
            unlockWrite(stamp)
        } else {
            unlockRead(stamp)
        }
    }

    public actual fun tryConvertToWriteLock(stamp: Long): Long {
        if (!isOfThisState(stamp)) {
            return 0L
        }

        if (isWriteStamp(stamp)) {
            return stamp
        }

        // A read lock is converted when it is the only one, an optimistic stamp when no thread
        // reads
        if (readers != if (isReadStamp(stamp)) 1 else 0) {
            return 0L
        }

        readers = 0
        state = state or WRITE_BIT
        return state
    }

    public actual fun tryConvertToReadLock(stamp: Long): Long {
        if (!isOfThisState(stamp)) {
            return 0L
        }

        if (isReadStamp(stamp)) {
            return if (readers == 0) 0L else stamp
        }

        if (isWriteStamp(stamp)) {
            releaseWrite()
        }

        readers++
        return state or READ_BIT
    }

    public actual fun tryConvertToOptimisticRead(stamp: Long): Long {
        if (!isOfThisState(stamp)) {
            return 0L
        }

        if (isWriteStamp(stamp)) {
            releaseWrite()
        } else if (isReadStamp(stamp)) {
            if (readers == 0) {
                return 0L
            }

            readers--
        }

        return state
    }

    @IgnorableReturnValue
    public actual fun tryUnlockWrite(): Boolean {
        if (!isWriteLocked()) {
            return false
        }

        releaseWrite()
        return true
    }

    @IgnorableReturnValue
    public actual fun tryUnlockRead(): Boolean {
        if (readers == 0) {
            return false
        }

        readers--
        return true
    }

    public actual fun isWriteLocked(): Boolean = (state and WRITE_BIT) != 0L

    public actual fun isReadLocked(): Boolean = readers != 0

    public actual fun getReadLockCount(): Int = readers

    public actual fun asReadLock(): Lock = StampedReadLockView(this)

    public actual fun asWriteLock(): Lock = StampedWriteLockView(this)

    public actual fun asReadWriteLock(): ReadWriteLock = StampedReadWriteLockView(this)

    private fun releaseWrite() {
        state = (state and WRITE_BIT.inv()) + WRITE_UNIT
    }

    // Whether no write lock was released since the stamp was returned, and none is held unless
    // the stamp is its own
    private fun isOfThisState(stamp: Long): Boolean = (stamp and READ_BIT.inv()) == state
}

private fun isWriteStamp(stamp: Long): Boolean = (stamp and WRITE_BIT) != 0L

private fun isReadStamp(stamp: Long): Boolean = (stamp and READ_BIT) != 0L
