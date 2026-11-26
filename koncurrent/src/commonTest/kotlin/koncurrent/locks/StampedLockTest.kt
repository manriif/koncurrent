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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

// More than the state of the port counts, so that the count apart from it is used
private const val MANY_READERS = 300

private val ShortTimeout = 1.milliseconds

/**
 * A stamped lock under test, whatever its type.
 */
internal interface TestStampedLock {

    fun writeLock(): Long

    fun tryWriteLock(): Long

    fun tryWriteLock(timeout: Duration): Long

    fun writeLockInterruptibly(): Long

    fun readLock(): Long

    fun tryReadLock(): Long

    fun tryReadLock(timeout: Duration): Long

    fun readLockInterruptibly(): Long

    fun tryOptimisticRead(): Long

    fun validate(stamp: Long): Boolean

    fun unlockWrite(stamp: Long)

    fun unlockRead(stamp: Long)

    fun unlock(stamp: Long)

    fun tryConvertToWriteLock(stamp: Long): Long

    fun tryConvertToReadLock(stamp: Long): Long

    fun tryConvertToOptimisticRead(stamp: Long): Long

    fun tryUnlockWrite(): Boolean

    fun tryUnlockRead(): Boolean

    fun isWriteLocked(): Boolean

    fun isReadLocked(): Boolean

    fun getReadLockCount(): Int
}

/**
 * Returns a public lock as a stamped lock under test.
 */
internal fun StampedLock.asTestStampedLock(): TestStampedLock {
    val lock = this

    return object : TestStampedLock {

        override fun writeLock() = lock.writeLock()

        override fun tryWriteLock() = lock.tryWriteLock()

        override fun tryWriteLock(timeout: Duration) = lock.tryWriteLock(timeout)

        override fun writeLockInterruptibly() = lock.writeLockInterruptibly()

        override fun readLock() = lock.readLock()

        override fun tryReadLock() = lock.tryReadLock()

        override fun tryReadLock(timeout: Duration) = lock.tryReadLock(timeout)

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
 * Tests what one thread sees of a stamped lock. Nothing here waits, so that it also runs where
 * there is one thread.
 */
abstract class StampedLockContractTest {

    internal abstract fun newLock(): TestStampedLock

    @Test
    fun newLockIsFree() {
        val lock = newLock()

        assertFalse(lock.isWriteLocked())
        assertFalse(lock.isReadLocked())
        assertEquals(0, lock.getReadLockCount())
        assertFalse(lock.tryUnlockWrite())
        assertFalse(lock.tryUnlockRead())
    }

    @Test
    fun writeLockIsHeldAlone() {
        val lock = newLock()
        val stamp = lock.writeLock()

        assertNotEquals(0L, stamp)
        assertTrue(lock.isWriteLocked())
        assertFalse(lock.isReadLocked())
        assertEquals(0L, lock.tryWriteLock())
        assertEquals(0L, lock.tryReadLock())
        assertEquals(0L, lock.tryOptimisticRead())
        assertTrue(lock.validate(stamp))

        lock.unlockWrite(stamp)

        assertFalse(lock.isWriteLocked())
        assertFalse(lock.validate(stamp))

        // Another write lock has another stamp
        val again = lock.tryWriteLock()

        assertNotEquals(0L, again)
        assertNotEquals(stamp, again)
        lock.unlockWrite(again)
    }

    @Test
    fun readLockIsSharedAndKeepsWritersOut() {
        val lock = newLock()
        val first = lock.readLock()
        val second = lock.tryReadLock()

        assertNotEquals(0L, first)
        assertNotEquals(0L, second)
        assertTrue(lock.isReadLocked())
        assertFalse(lock.isWriteLocked())
        assertEquals(2, lock.getReadLockCount())
        assertEquals(0L, lock.tryWriteLock())
        assertTrue(lock.validate(first))

        lock.unlockRead(first)

        assertEquals(1, lock.getReadLockCount())
        assertEquals(0L, lock.tryWriteLock())

        lock.unlockRead(second)

        assertFalse(lock.isReadLocked())
        lock.unlockWrite(lock.writeLock())
    }

    @Test
    fun interruptibleLocksAcquireAFreeLock() {
        val lock = newLock()
        val write = lock.writeLockInterruptibly()

        assertTrue(lock.isWriteLocked())
        lock.unlockWrite(write)

        val read = lock.readLockInterruptibly()

        assertEquals(1, lock.getReadLockCount())
        lock.unlockRead(read)
    }

    @Test
    fun optimisticStampIsValidUntilAWriteLock() {
        val lock = newLock()
        val stamp = lock.tryOptimisticRead()

        assertNotEquals(0L, stamp)
        assertTrue(lock.validate(stamp))
        assertFalse(lock.isReadLocked())

        // A read lock does not end it, and does not prevent another optimistic read
        val read = lock.readLock()

        assertTrue(lock.validate(stamp))
        assertTrue(lock.validate(lock.tryOptimisticRead()))
        lock.unlockRead(read)
        assertTrue(lock.validate(stamp))

        val write = lock.writeLock()

        assertFalse(lock.validate(stamp))
        lock.unlockWrite(write)
        assertFalse(lock.validate(stamp))

        assertTrue(lock.validate(lock.tryOptimisticRead()))
        assertFalse(lock.validate(0L))
    }

    @Test
    fun unlockReleasesEitherLock() {
        val lock = newLock()

        lock.unlock(lock.writeLock())
        assertFalse(lock.isWriteLocked())

        lock.unlock(lock.readLock())
        assertFalse(lock.isReadLocked())
    }

    @Test
    fun unlockFailsWithAStampThatIsNotHeld() {
        val lock = newLock()
        val optimistic = lock.tryOptimisticRead()

        assertFailsWith<IllegalMonitorStateException> { lock.unlockWrite(optimistic) }
        assertFailsWith<IllegalMonitorStateException> { lock.unlockRead(optimistic) }
        assertFailsWith<IllegalMonitorStateException> { lock.unlock(optimistic) }
        assertFailsWith<IllegalMonitorStateException> { lock.unlock(0L) }

        val read = lock.readLock()

        assertFailsWith<IllegalMonitorStateException> { lock.unlockWrite(read) }
        lock.unlockRead(read)
        assertFailsWith<IllegalMonitorStateException> { lock.unlockRead(read) }

        val write = lock.writeLock()

        assertFailsWith<IllegalMonitorStateException> { lock.unlockRead(write) }
        assertFailsWith<IllegalMonitorStateException> { lock.unlockRead(read) }
        assertTrue(lock.isWriteLocked())

        lock.unlockWrite(write)

        assertFailsWith<IllegalMonitorStateException> { lock.unlockWrite(write) }
        assertFailsWith<IllegalMonitorStateException> { lock.unlock(write) }
        assertFalse(lock.isWriteLocked())

        // The stamp of a read lock from before the write does not release a later one
        val later = lock.readLock()

        assertFailsWith<IllegalMonitorStateException> { lock.unlockRead(read) }
        assertEquals(1, lock.getReadLockCount())
        lock.unlockRead(later)
    }

    @Test
    fun tryUnlockReleasesWithoutAStamp() {
        val lock = newLock()
        val write = lock.writeLock()

        assertFalse(lock.tryUnlockRead())
        assertTrue(lock.tryUnlockWrite())
        assertFalse(lock.isWriteLocked())
        assertFalse(lock.tryUnlockWrite())
        assertFailsWith<IllegalMonitorStateException> { lock.unlockWrite(write) }

        val read = lock.readLock()

        assertFalse(lock.tryUnlockWrite())
        assertTrue(lock.tryUnlockRead())
        assertFalse(lock.isReadLocked())
        assertFalse(lock.tryUnlockRead())
        assertFailsWith<IllegalMonitorStateException> { lock.unlockRead(read) }
    }

    @Test
    fun readLockCountsMoreHoldsThanAStateHolds() {
        val lock = newLock()
        val stamps = List(MANY_READERS) { lock.readLock() }

        assertEquals(MANY_READERS, lock.getReadLockCount())
        assertEquals(0L, lock.tryWriteLock())
        assertTrue(lock.validate(stamps.first()))

        // Releases some with their stamps, the others without
        stamps.take(MANY_READERS / 2).forEach { lock.unlockRead(it) }

        assertEquals(MANY_READERS - MANY_READERS / 2, lock.getReadLockCount())

        while (lock.tryUnlockRead()) {
            // Until no hold is left
        }

        assertEquals(0, lock.getReadLockCount())
        lock.unlockWrite(lock.writeLock())
    }

    @Test
    fun timedTriesAcquireAFreeLockOrGiveUp() {
        val lock = newLock()
        val write = lock.tryWriteLock(Duration.ZERO)

        assertNotEquals(0L, write)
        assertEquals(0L, lock.tryWriteLock(Duration.ZERO))
        assertEquals(0L, lock.tryReadLock(Duration.ZERO))
        assertEquals(0L, lock.tryReadLock(ShortTimeout))
        lock.unlockWrite(write)

        val read = lock.tryReadLock(ShortTimeout)

        assertNotEquals(0L, read)
        assertEquals(0L, lock.tryWriteLock(ShortTimeout))
        assertEquals(1, lock.getReadLockCount())
        lock.unlockRead(read)

        lock.unlockWrite(lock.tryWriteLock(ShortTimeout))
    }

    ///////////////////////////////////////////////////////////////////////////
    // Conversions
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun optimisticStampConvertsToTheWriteLockWhileItIsValid() {
        val lock = newLock()
        val write = lock.tryConvertToWriteLock(lock.tryOptimisticRead())

        assertNotEquals(0L, write)
        assertTrue(lock.isWriteLocked())

        // The stamp of the write lock is returned as it is
        assertEquals(write, lock.tryConvertToWriteLock(write))
        lock.unlockWrite(write)

        // A write lock was taken since this stamp
        val stale = lock.tryOptimisticRead()

        lock.unlockWrite(lock.writeLock())
        assertEquals(0L, lock.tryConvertToWriteLock(stale))
        assertFalse(lock.isWriteLocked())

        // A thread reads
        val optimistic = lock.tryOptimisticRead()
        val read = lock.readLock()

        assertEquals(0L, lock.tryConvertToWriteLock(optimistic))
        assertEquals(1, lock.getReadLockCount())
        lock.unlockRead(read)
    }

    @Test
    fun readLockConvertsToTheWriteLockWhenItIsTheOnlyOne() {
        val lock = newLock()
        val first = lock.readLock()
        val second = lock.readLock()

        assertEquals(0L, lock.tryConvertToWriteLock(first))
        assertEquals(2, lock.getReadLockCount())

        lock.unlockRead(second)

        val write = lock.tryConvertToWriteLock(first)

        assertNotEquals(0L, write)
        assertTrue(lock.isWriteLocked())
        assertFalse(lock.isReadLocked())
        lock.unlockWrite(write)

        // The read lock that the stamp was for is gone
        assertEquals(0L, lock.tryConvertToWriteLock(first))
        assertFalse(lock.isWriteLocked())
    }

    @Test
    fun writeLockConvertsToAReadLock() {
        val lock = newLock()
        val write = lock.writeLock()
        val read = lock.tryConvertToReadLock(write)

        assertNotEquals(0L, read)
        assertFalse(lock.isWriteLocked())
        assertEquals(1, lock.getReadLockCount())
        assertFalse(lock.validate(write))

        // The stamp of a read lock is returned as it is
        assertEquals(read, lock.tryConvertToReadLock(read))
        assertEquals(1, lock.getReadLockCount())

        // Other readers get in, writers do not
        val other = lock.tryReadLock()

        assertNotEquals(0L, other)
        assertEquals(0L, lock.tryWriteLock())
        lock.unlockRead(other)
        lock.unlockRead(read)

        assertEquals(0L, lock.tryConvertToReadLock(write))
        assertEquals(0L, lock.tryConvertToReadLock(read))
        assertFalse(lock.isReadLocked())
    }

    @Test
    fun optimisticStampConvertsToAReadLockWhileItIsValid() {
        val lock = newLock()
        val optimistic = lock.tryOptimisticRead()
        val read = lock.tryConvertToReadLock(optimistic)

        assertNotEquals(0L, read)
        assertEquals(1, lock.getReadLockCount())
        lock.unlockRead(read)

        lock.unlockWrite(lock.writeLock())

        assertEquals(0L, lock.tryConvertToReadLock(optimistic))
        assertFalse(lock.isReadLocked())
    }

    @Test
    fun locksConvertToAnOptimisticStamp() {
        val lock = newLock()
        val write = lock.writeLock()
        val afterWrite = lock.tryConvertToOptimisticRead(write)

        assertNotEquals(0L, afterWrite)
        assertFalse(lock.isWriteLocked())
        assertTrue(lock.validate(afterWrite))

        // An optimistic stamp is returned as it is
        assertEquals(afterWrite, lock.tryConvertToOptimisticRead(afterWrite))

        val read = lock.readLock()
        val afterRead = lock.tryConvertToOptimisticRead(read)

        assertNotEquals(0L, afterRead)
        assertFalse(lock.isReadLocked())
        assertTrue(lock.validate(afterRead))

        // Neither lock is held any more
        assertEquals(0L, lock.tryConvertToOptimisticRead(write))
        assertEquals(0L, lock.tryConvertToOptimisticRead(read))

        lock.unlockWrite(lock.writeLock())

        assertEquals(0L, lock.tryConvertToOptimisticRead(afterRead))
    }

    @Test
    fun readLockAmongOthersConvertsToAnOptimisticStamp() {
        val lock = newLock()
        val first = lock.readLock()
        val second = lock.readLock()
        val optimistic = lock.tryConvertToOptimisticRead(first)

        assertNotEquals(0L, optimistic)
        assertEquals(1, lock.getReadLockCount())
        assertTrue(lock.validate(optimistic))

        lock.unlockRead(second)
    }
}

/**
 * Tests the public [StampedLock] on one thread.
 */
class StampedLockTest : StampedLockContractTest() {

    override fun newLock(): TestStampedLock = StampedLock().asTestStampedLock()
}

/**
 * Tests what the public [StampedLock] offers without a stamp: its views as locks, and the
 * functions that run a block under a lock. Nothing here waits.
 */
abstract class StampedLockViewsContractTest {

    abstract fun newLock(): StampedLock

    @Test
    fun readViewTakesAndReleasesTheReadLock() {
        val lock = newLock()
        val view = lock.asReadLock()

        view.lock()
        view.lockInterruptibly()
        assertTrue(view.tryLock())
        assertTrue(view.tryLock(ShortTimeout))
        assertEquals(4, lock.getReadLockCount())
        assertEquals(0L, lock.tryWriteLock())

        repeat(4) { view.unlock() }

        assertFalse(lock.isReadLocked())
        assertFailsWith<IllegalMonitorStateException> { view.unlock() }
    }

    @Test
    fun writeViewTakesAndReleasesTheWriteLock() {
        val lock = newLock()
        val view = lock.asWriteLock()

        view.lock()
        assertTrue(lock.isWriteLocked())

        // The view is no more reentrant than the lock
        assertFalse(view.tryLock())
        assertFalse(view.tryLock(ShortTimeout))

        view.unlock()
        assertFalse(lock.isWriteLocked())
        assertFailsWith<IllegalMonitorStateException> { view.unlock() }

        view.lockInterruptibly()
        view.unlock()
        assertTrue(view.tryLock())
        view.unlock()
        assertTrue(view.tryLock(ShortTimeout))
        view.unlock()
        assertFalse(lock.isWriteLocked())
    }

    @Test
    fun viewsAndStampsReleaseTheSameLock() {
        val lock = newLock()
        val write = lock.writeLock()

        lock.asWriteLock().unlock()
        assertFalse(lock.isWriteLocked())
        assertFailsWith<IllegalMonitorStateException> { lock.unlockWrite(write) }

        lock.asReadLock().lock()
        assertTrue(lock.tryUnlockRead())
        assertFalse(lock.isReadLocked())

        val read = lock.readLock()

        lock.asReadLock().unlock()
        assertFailsWith<IllegalMonitorStateException> { lock.unlockRead(read) }
    }

    @Test
    fun readWriteLockViewHasBothLocks() {
        val lock = newLock()
        val locks: ReadWriteLock = lock.asReadWriteLock()

        locks.readLock().lock()
        assertEquals(1, lock.getReadLockCount())
        assertFalse(locks.writeLock().tryLock())
        locks.readLock().unlock()

        locks.writeLock().withLock { assertTrue(lock.isWriteLocked()) }
        assertFalse(lock.isWriteLocked())
    }

    @Test
    fun readAndWriteHoldTheLockForTheirAction() {
        val lock = newLock()

        val read = lock.read {
            assertEquals(1, lock.getReadLockCount())
            "read"
        }

        val written = lock.write {
            assertTrue(lock.isWriteLocked())
            "written"
        }

        assertEquals("read", read)
        assertEquals("written", written)
        assertFalse(lock.isReadLocked())
        assertFalse(lock.isWriteLocked())
    }

    @Test
    fun readAndWriteReleaseWhenTheirActionFails() {
        val lock = newLock()

        assertFailsWith<IllegalArgumentException> {
            lock.read { throw IllegalArgumentException() }
        }

        assertFailsWith<IllegalArgumentException> {
            lock.write { throw IllegalArgumentException() }
        }

        assertFalse(lock.isReadLocked())
        assertFalse(lock.isWriteLocked())
    }
}

/**
 * Tests the views and the block functions of the public [StampedLock].
 */
class StampedLockViewsTest : StampedLockViewsContractTest() {

    override fun newLock(): StampedLock = StampedLock()
}
