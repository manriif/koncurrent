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

import koncurrent.CountDownLatch
import koncurrent.InterruptedException
import koncurrent.TestThread
import koncurrent.await
import koncurrent.currentThread
import koncurrent.runConcurrently
import koncurrent.spinFor
import koncurrent.startThread
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private const val THREAD_COUNT = 8
private const val READER_COUNT = 4
private const val ROUND_COUNT = 20_000
private const val GIVE_UP_ROUND_COUNT = 2_000

private val BlockedDelay = 50.milliseconds
private val ReadDelay = 200.microseconds
private val SharedTimeout = 10.seconds
private val ShortTimeout = 50.milliseconds
private val LongTimeout = 30.seconds

// What a thread holds a lock for while others give up, and the longest that those wait
private val GiveUpHold = 20.microseconds
private val GiveUpTimeout = 60.microseconds

/**
 * A reentrant read-write lock under test, whatever its type.
 */
internal class TestReadWriteLock(
    val read: TestLock,
    val write: TestLock,
    val readHoldCount: () -> Int,
    val writeHoldCount: () -> Int,
)

/**
 * Tests what several threads see of a reentrant read-write lock.
 */
abstract class ReadWriteLockContractTest {

    internal abstract fun newLock(): TestReadWriteLock

    @Test
    fun writersExcludeEachOther() {
        val lock = newLock()
        var count = 0

        runConcurrently(THREAD_COUNT) {
            repeat(ROUND_COUNT) {
                lock.write.lock()
                count++
                lock.write.unlock()
            }
        }

        assertEquals(THREAD_COUNT * ROUND_COUNT, count)
    }

    @Test
    fun readersNeverSeeAWriteHalfDone() {
        val lock = newLock()
        var first = 0
        var second = 0

        runConcurrently(THREAD_COUNT) { thread ->
            repeat(ROUND_COUNT) {
                if (thread % 4 == 0) {
                    lock.write.lock()
                    first++
                    second++
                    lock.write.unlock()
                } else {
                    lock.read.lock()
                    val seenFirst = first
                    val seenSecond = second
                    lock.read.unlock()

                    assertEquals(seenFirst, seenSecond)
                }
            }
        }

        assertEquals(THREAD_COUNT / 4 * ROUND_COUNT, first)
        assertEquals(first, second)
    }

    @Test
    fun readLockIsSharedAndExcludesWriters() {
        val lock = newLock()

        lock.read.lock()

        assertNull(
            startThread {
                assertTrue(lock.read.tryLock())
                lock.read.unlock()
                assertFalse(lock.write.tryLock())
            }(),
        )

        lock.read.unlock()

        assertNull(
            startThread {
                assertTrue(lock.write.tryLock())
                lock.write.unlock()
            }(),
        )
    }

    @Test
    fun writeLockExcludesReadersAndWriters() {
        val lock = newLock()

        lock.write.lock()

        assertNull(
            startThread {
                assertFalse(lock.read.tryLock())
                assertFalse(lock.write.tryLock())
            }(),
        )

        lock.write.unlock()

        assertNull(
            startThread {
                assertTrue(lock.read.tryLock())
                lock.read.unlock()
            }(),
        )
    }

    @Test
    fun writerWaitsForTheReaders() {
        val lock = newLock()
        val started = AtomicBoolean(false)
        val written = AtomicBoolean(false)

        lock.read.lock()

        val join = startThread {
            started.store(true)
            lock.write.lock()
            written.store(true)
            lock.write.unlock()
        }

        while (!started.load()) {
            // Spins until the thread asks for the write lock
        }

        spinFor(BlockedDelay)
        assertFalse(written.load())

        lock.read.unlock()

        assertNull(join())
        assertTrue(written.load())
    }

    @Test
    fun waitingReadersAcquireTogetherOnceTheWriterReleases() {
        val lock = newLock()
        val started = AtomicInt(0)
        val inside = AtomicInt(0)

        lock.write.lock()

        val joins = List(READER_COUNT) {
            startThread {
                val _ = started.incrementAndFetch()

                lock.read.lock()
                val _ = inside.incrementAndFetch()

                // Every reader gets in while the others still hold the read lock
                val start = TimeSource.Monotonic.markNow()

                while (inside.load() < READER_COUNT && start.elapsedNow() < SharedTimeout) {
                    // Spins until the readers are all in
                }

                val together = inside.load()
                lock.read.unlock()

                assertEquals(READER_COUNT, together)
            }
        }

        while (started.load() < READER_COUNT) {
            // Spins until the threads ask for the read lock
        }

        spinFor(BlockedDelay)
        assertEquals(0, inside.load())

        lock.write.unlock()

        joins.forEach { assertNull(it()) }
    }

    @Test
    fun waitingWriterPassesBeforeNewReaders() {
        val lock = newLock()
        val written = AtomicBoolean(false)

        // The readers overlap. The read lock is never free unless they let the writer pass.
        runConcurrently(THREAD_COUNT) { thread ->
            if (thread == 0) {
                lock.write.lock()
                written.store(true)
                lock.write.unlock()
            } else {
                while (!written.load()) {
                    lock.read.lock()
                    spinFor(ReadDelay)
                    lock.read.unlock()
                }
            }
        }

        assertTrue(written.load())
    }

    @Test
    fun firstReaderCanReadAgainWhileAWriterWaits() {
        val lock = newLock()

        lock.read.lock()
        assertReadsAgainWhileAWriterWaits(lock)
    }

    @Test
    fun otherReaderCanReadAgainWhileAWriterWaits() {
        val lock = newLock()
        val reading = AtomicBoolean(false)
        val release = AtomicBoolean(false)

        val join = startThread {
            lock.read.lock()
            reading.store(true)

            while (!release.load()) {
                // Spins while it keeps the read lock that it took first
            }

            lock.read.unlock()
        }

        while (!reading.load()) {
            // Spins until the other thread reads
        }

        lock.read.lock()
        assertReadsAgainWhileAWriterWaits(lock) { release.store(true) }

        assertNull(join())
    }

    /**
     * Checks that the calling thread, which holds the read lock once, can take it again while
     * a writer waits. It would wait for itself if the writer stopped it.
     */
    private fun assertReadsAgainWhileAWriterWaits(
        lock: TestReadWriteLock,
        beforeRelease: () -> Unit = {},
    ) {
        val started = AtomicBoolean(false)
        val written = AtomicBoolean(false)

        val join = startThread {
            started.store(true)
            lock.write.lock()
            written.store(true)
            lock.write.unlock()
        }

        while (!started.load()) {
            // Spins until the thread asks for the write lock
        }

        spinFor(BlockedDelay)

        lock.read.lock()
        assertEquals(2, lock.readHoldCount())
        assertFalse(written.load())

        beforeRelease()
        lock.read.unlock()
        lock.read.unlock()

        assertNull(join())
        assertTrue(written.load())
    }

    @Test
    fun writerKeepsReadingAfterItReleasedTheWriteLock() {
        val lock = newLock()

        lock.write.lock()
        lock.read.lock()
        lock.write.unlock()

        assertNull(
            startThread {
                assertTrue(lock.read.tryLock())
                lock.read.unlock()
                assertFalse(lock.write.tryLock())
            }(),
        )

        lock.read.unlock()
    }

    @Test
    fun unlockFailsForAThreadThatDoesNotHoldTheLock() {
        val lock = newLock()

        lock.read.lock()

        assertNull(
            startThread {
                assertFailsWith<IllegalMonitorStateException> { lock.read.unlock() }
                assertFailsWith<IllegalMonitorStateException> { lock.write.unlock() }
            }(),
        )

        lock.read.unlock()
        lock.write.lock()

        assertNull(
            startThread {
                assertFailsWith<IllegalMonitorStateException> { lock.read.unlock() }
                assertFailsWith<IllegalMonitorStateException> { lock.write.unlock() }
            }(),
        )

        lock.write.unlock()
    }

    @Test
    fun holdCountsAreThoseOfTheCallingThread() {
        val lock = newLock()

        lock.read.lock()
        lock.read.lock()

        assertNull(
            startThread {
                assertEquals(0, lock.readHoldCount())

                lock.read.lock()
                assertEquals(1, lock.readHoldCount())
                lock.read.unlock()

                assertEquals(0, lock.readHoldCount())
            }(),
        )

        assertEquals(2, lock.readHoldCount())
        lock.read.unlock()
        lock.read.unlock()

        lock.write.lock()
        assertNull(startThread { assertEquals(0, lock.writeHoldCount()) }())
        assertEquals(1, lock.writeHoldCount())
        lock.write.unlock()
    }

    @Test
    fun everyKindOfHoldKeepsTheDataConsistent() {
        val lock = newLock()
        val writes = AtomicInt(0)
        var first = 0
        var second = 0

        runConcurrently(THREAD_COUNT) { thread ->
            val random = Random(thread)

            repeat(ROUND_COUNT) {
                when (random.nextInt(8)) {
                    0 -> {
                        lock.write.lock()
                        first++
                        second++
                        val _ = writes.incrementAndFetch()
                        lock.write.unlock()
                    }

                    1 -> {
                        // Writes with two holds, then keeps reading without the write lock
                        lock.write.lock()
                        lock.write.lock()
                        first++
                        lock.read.lock()
                        second++
                        val _ = writes.incrementAndFetch()
                        lock.write.unlock()
                        lock.write.unlock()

                        val seenFirst = first
                        val seenSecond = second
                        lock.read.unlock()

                        assertEquals(seenFirst, seenSecond)
                    }

                    2 -> {
                        if (lock.write.tryLock()) {
                            first++
                            second++
                            val _ = writes.incrementAndFetch()
                            lock.write.unlock()
                        }
                    }

                    3 -> {
                        if (lock.read.tryLock()) {
                            val seenFirst = first
                            val seenSecond = second
                            lock.read.unlock()

                            assertEquals(seenFirst, seenSecond)
                        }
                    }

                    else -> {
                        lock.read.lock()
                        lock.read.lock()
                        val seenFirst = first
                        val seenSecond = second
                        val holds = lock.readHoldCount()
                        lock.read.unlock()
                        lock.read.unlock()

                        assertEquals(seenFirst, seenSecond)
                        assertEquals(2, holds)
                    }
                }
            }
        }

        assertEquals(writes.load(), first)
        assertEquals(first, second)
        assertEquals(0, lock.readHoldCount())
    }

    ///////////////////////////////////////////////////////////////////////////
    // Timeouts
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun timedTryLockOfTheWriteLockGivesUpWhileAThreadReads() {
        val lock = newLock()

        lock.read.lock()
        assertGivesUpThenAcquires(lock.write, lock.read)
    }

    @Test
    fun timedTryLockOfTheReadLockGivesUpWhileAThreadWrites() {
        val lock = newLock()

        lock.write.lock()
        assertGivesUpThenAcquires(lock.read, lock.write)
    }

    /**
     * Has another thread ask for the [wanted] lock with a time limit, while the calling thread
     * holds the [held] one. It must give up, then acquire once the lock is released.
     */
    private fun assertGivesUpThenAcquires(wanted: TestLock, held: TestLock) {
        assertNull(
            startThread {
                val start = TimeSource.Monotonic.markNow()

                assertFalse(wanted.tryLockFor(Duration.ZERO))
                assertFalse(wanted.tryLockFor(ShortTimeout))
                assertTrue(start.elapsedNow() >= ShortTimeout)
            }(),
        )

        val join = startThread {
            assertTrue(wanted.tryLockFor(LongTimeout))
            wanted.unlock()
        }

        spinFor(BlockedDelay)
        held.unlock()

        assertNull(join())
    }

    @Test
    fun readersPassAWriterThatGaveUp() {
        val lock = newLock()
        val writerStarted = AtomicBoolean(false)
        val readerStarted = AtomicBoolean(false)
        val read = CountDownLatch(1)

        lock.read.lock()

        val writer = startThread {
            writerStarted.store(true)
            assertFalse(lock.write.tryLockFor(BlockedDelay * 4))
        }

        while (!writerStarted.load()) {
            // Spins until the writer asks for the lock
        }

        spinFor(BlockedDelay)

        // A thread that does not read yet lets the waiting writer pass, so it waits behind it
        val reader = startThread {
            readerStarted.store(true)
            lock.read.lock()
            read.countDown()
            lock.read.unlock()
        }

        while (!readerStarted.load()) {
            // Spins until the reader asks for the lock
        }

        assertNull(writer())

        // The writer is gone, and the calling thread still reads. So the reader gets in.
        assertTrue(read.await(LongTimeout))

        lock.read.unlock()

        assertNull(reader())
    }

    @Test
    fun threadsThatGiveUpKeepTheDataConsistent() {
        val lock = newLock()
        val writes = AtomicInt(0)
        var first = 0
        var second = 0

        runConcurrently(THREAD_COUNT) { thread ->
            repeat(GIVE_UP_ROUND_COUNT) { round ->
                val timeout = GiveUpTimeout * (round % 4) / 3

                when (thread % 4) {
                    0 -> {
                        lock.write.lock()
                        first++
                        spinFor(GiveUpHold)
                        second++
                        val _ = writes.incrementAndFetch()
                        lock.write.unlock()
                    }

                    1 -> {
                        if (lock.write.tryLockFor(timeout)) {
                            first++
                            second++
                            val _ = writes.incrementAndFetch()
                            lock.write.unlock()
                        }
                    }

                    2 -> {
                        lock.read.lock()
                        val seenFirst = first
                        spinFor(GiveUpHold)
                        val seenSecond = second
                        lock.read.unlock()

                        assertEquals(seenFirst, seenSecond)
                    }

                    else -> {
                        if (lock.read.tryLockFor(timeout)) {
                            val seenFirst = first
                            val seenSecond = second
                            lock.read.unlock()

                            assertEquals(seenFirst, seenSecond)
                        }
                    }
                }
            }
        }

        assertEquals(writes.load(), first)
        assertEquals(first, second)
        assertEquals(0, lock.readHoldCount())
        assertEquals(0, lock.writeHoldCount())
    }

    ///////////////////////////////////////////////////////////////////////////
    // Interruption
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun anInterruptEndsLockInterruptiblyOfTheWriteLock() {
        val lock = newLock()

        lock.read.lock()
        assertInterruptEndsTheWait(lock.write, lock.read)
    }

    @Test
    fun anInterruptEndsLockInterruptiblyOfTheReadLock() {
        val lock = newLock()

        lock.write.lock()
        assertInterruptEndsTheWait(lock.read, lock.write)
    }

    /**
     * Interrupts a thread that waits for the [wanted] lock while the calling thread holds the
     * [held] one. The wait must end with the exception and leave the lock as it was.
     */
    private fun assertInterruptEndsTheWait(wanted: TestLock, held: TestLock) {
        val waiter = TestThread {
            assertFailsWith<InterruptedException> { wanted.lockInterruptibly() }
            assertFalse(currentThread().isInterrupted())
        }

        spinFor(BlockedDelay)
        waiter.interrupt()

        assertNull(waiter.join())

        held.unlock()

        assertNull(
            startThread {
                wanted.lock()
                wanted.unlock()
            }(),
        )
    }
}

/**
 * Tests the public [ReentrantReadWriteLock] on real threads.
 */
class ReentrantReadWriteLockStressTest : ReadWriteLockContractTest() {

    override fun newLock(): TestReadWriteLock {
        val lock = ReentrantReadWriteLock()
        val read: Lock = lock.readLock()
        val write: Lock = lock.writeLock()

        return TestReadWriteLock(
            read = TestLock(read),
            write = TestLock(write),
            readHoldCount = { lock.getReadHoldCount() },
            writeHoldCount = { lock.getWriteHoldCount() },
        )
    }
}
