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
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tests what one thread sees of the public [ReentrantReadWriteLock].
 */
class ReentrantReadWriteLockTest {

    @Test
    fun readLockCountsItsHolds() {
        val lock = ReentrantReadWriteLock()
        val readLock = lock.readLock()

        readLock.lock()
        assertTrue(readLock.tryLock())
        assertEquals(2, lock.getReadHoldCount())
        assertEquals(0, lock.getWriteHoldCount())

        readLock.unlock()
        readLock.unlock()
        assertEquals(0, lock.getReadHoldCount())

        assertFailsWith<IllegalMonitorStateException> { readLock.unlock() }
    }

    @Test
    fun writeLockCountsItsHolds() {
        val lock = ReentrantReadWriteLock()
        val writeLock = lock.writeLock()

        writeLock.lock()
        assertTrue(writeLock.tryLock())
        assertEquals(2, lock.getWriteHoldCount())
        assertEquals(0, lock.getReadHoldCount())

        writeLock.unlock()
        writeLock.unlock()
        assertEquals(0, lock.getWriteHoldCount())

        assertFailsWith<IllegalMonitorStateException> { writeLock.unlock() }
    }

    @Test
    fun writerCanReadAndKeepReading() {
        val lock = ReentrantReadWriteLock()

        lock.writeLock().lock()
        lock.readLock().lock()
        lock.writeLock().unlock()

        assertEquals(0, lock.getWriteHoldCount())
        assertEquals(1, lock.getReadHoldCount())

        lock.readLock().unlock()

        assertTrue(lock.writeLock().tryLock())
        lock.writeLock().unlock()
    }

    @Test
    fun readerCannotTakeTheWriteLock() {
        val lock = ReentrantReadWriteLock()

        lock.readLock().lock()
        assertFalse(lock.writeLock().tryLock())

        lock.readLock().unlock()

        assertTrue(lock.writeLock().tryLock())
        lock.writeLock().unlock()
    }

    @Test
    fun readAndWriteReturnTheValueOfTheirAction() {
        val lock = ReentrantReadWriteLock()

        assertEquals("read", lock.read { "read" })
        assertEquals("write", lock.write { "write" })

        assertEquals(0, lock.getReadHoldCount())
        assertEquals(0, lock.getWriteHoldCount())
    }

    @Test
    fun readAndWriteReleaseWhenTheirActionFails() {
        val lock = ReentrantReadWriteLock()

        assertFailsWith<IllegalArgumentException> {
            lock.read { throw IllegalArgumentException() }
        }

        assertFailsWith<IllegalArgumentException> {
            lock.write { throw IllegalArgumentException() }
        }

        assertEquals(0, lock.getReadHoldCount())
        assertEquals(0, lock.getWriteHoldCount())
    }

    @Test
    fun writeGivesBackTheReadHoldsOfItsCaller() {
        val lock = ReentrantReadWriteLock()

        lock.read {
            lock.read {
                lock.write {
                    assertEquals(0, lock.getReadHoldCount())
                    assertEquals(1, lock.getWriteHoldCount())
                }

                assertEquals(2, lock.getReadHoldCount())
                assertEquals(0, lock.getWriteHoldCount())
            }
        }

        assertEquals(0, lock.getReadHoldCount())
    }

    @Test
    fun nestedWriteKeepsTheReadHoldsTakenWhileWriting() {
        val lock = ReentrantReadWriteLock()

        lock.write {
            lock.read {
                lock.write {
                    assertEquals(1, lock.getReadHoldCount())
                    assertEquals(2, lock.getWriteHoldCount())
                }
            }
        }

        assertEquals(0, lock.getReadHoldCount())
        assertEquals(0, lock.getWriteHoldCount())
    }

    @Test
    fun bothLocksAreLocks() {
        val lock = ReentrantReadWriteLock()
        val locks: List<Lock> = listOf(lock.readLock(), lock.writeLock())

        assertEquals(listOf(0, 1), locks.mapIndexed { index, each -> each.withLock { index } })

        assertEquals(0, lock.getReadHoldCount())
        assertEquals(0, lock.getWriteHoldCount())
    }

    @Test
    fun freeLocksAreAcquiredInEveryWay() {
        val lock = ReentrantReadWriteLock()
        val read: Lock = lock.readLock()
        val write: Lock = lock.writeLock()

        read.lockInterruptibly()
        assertTrue(read.tryLock(1.milliseconds))
        assertEquals(2, lock.getReadHoldCount())

        repeat(2) { read.unlock() }

        write.lockInterruptibly()
        assertTrue(write.tryLock(1.milliseconds))
        assertEquals(2, lock.getWriteHoldCount())

        repeat(2) { write.unlock() }
    }

    @Test
    fun timedTryLockOfTheWriteLockGivesUpWhileReading() {
        val lock = ReentrantReadWriteLock()
        val write: Lock = lock.writeLock()

        lock.readLock().lock()

        assertFalse(write.tryLock(1.milliseconds))
        assertEquals(0, lock.getWriteHoldCount())

        lock.readLock().unlock()
    }
}
