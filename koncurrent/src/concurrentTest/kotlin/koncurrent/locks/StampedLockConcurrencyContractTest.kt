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
import koncurrent.TestThread
import koncurrent.currentThread
import koncurrent.interrupted
import koncurrent.runConcurrently
import koncurrent.spinFor
import koncurrent.startThread
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
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

// The distance between two numbers of an array that never share a cache line
private const val FAR_APART = 63

// Together the threads hold more than the state of the port counts
private const val HOLDS_PER_THREAD = 40
private const val MANY_HOLDS_ROUND_COUNT = 200

private val BlockedDelay = 50.milliseconds

// How long a reader stays alone before it tells that no other reader could come in
private val AloneDelay = 20.milliseconds
private val SharedTimeout = 10.seconds
private val ShortTimeout = 50.milliseconds
private val LongTimeout = 30.seconds

// What a thread holds a lock for while others give up, and the longest that those wait
private val GiveUpHold = 20.microseconds
private val GiveUpTimeout = 60.microseconds

/**
 * Tests what several threads see of a stamped lock.
 */
abstract class StampedLockConcurrencyContractTest {

    internal abstract fun newLock(): TestStampedLock

    @Test
    fun writersExcludeEachOther() {
        val lock = newLock()
        var count = 0

        runConcurrently(THREAD_COUNT) {
            repeat(ROUND_COUNT) {
                val stamp = lock.writeLock()
                count++
                lock.unlockWrite(stamp)
            }
        }

        assertEquals(THREAD_COUNT * ROUND_COUNT, count)
        assertFalse(lock.isWriteLocked())
    }

    @Test
    fun readersNeverSeeAWriteHalfDone() {
        val lock = newLock()
        var first = 0
        var second = 0

        runConcurrently(THREAD_COUNT) { thread ->
            repeat(ROUND_COUNT) {
                if (thread % 4 == 0) {
                    val stamp = lock.writeLock()
                    first++
                    second++
                    lock.unlockWrite(stamp)
                } else {
                    val stamp = lock.readLock()
                    val seenFirst = first
                    val seenSecond = second
                    lock.unlockRead(stamp)

                    assertEquals(seenFirst, seenSecond)
                }
            }
        }

        assertEquals(THREAD_COUNT / 4 * ROUND_COUNT, first)
        assertEquals(first, second)
        assertEquals(0, lock.getReadLockCount())
    }

    /**
     * The two numbers are far apart in memory. A reader then gets the second one late, when a
     * writer changed it since the reader last had it. That is when a processor may read the
     * state ahead of it, which the lock must prevent for the validation to mean anything.
     */
    @Test
    fun aValidatedOptimisticReadNeverSeesAWriteHalfDone() {
        val lock = newLock()
        val validated = AtomicLong(0L)
        val numbers = IntArray(FAR_APART + 1)

        runConcurrently(THREAD_COUNT) { thread ->
            var valid = 0L

            repeat(ROUND_COUNT) { round ->
                if (thread % 4 == 0) {
                    val stamp = lock.writeLock()
                    numbers[0]++
                    numbers[FAR_APART]++
                    lock.unlockWrite(stamp)

                    // Leaves the readers some time without a write
                    if (round % 16 == 0) {
                        spinFor(GiveUpHold)
                    }
                } else {
                    val stamp = lock.tryOptimisticRead()
                    val seenFirst = numbers[0]
                    val seenSecond = numbers[FAR_APART]

                    if (lock.validate(stamp)) {
                        assertEquals(seenFirst, seenSecond)
                        valid++
                    }
                }
            }

            val _ = validated.addAndFetch(valid)
        }

        assertEquals(THREAD_COUNT / 4 * ROUND_COUNT, numbers[0])
        assertEquals(numbers[0], numbers[FAR_APART])

        // The reads would prove nothing if none was valid
        assertTrue(validated.load() > 0L)
    }

    @Test
    fun readLockIsSharedAndExcludesWriters() {
        val lock = newLock()
        val stamp = lock.readLock()

        assertNull(
            startThread {
                val other = lock.tryReadLock()

                assertNotEquals(0L, other)
                lock.unlockRead(other)
                assertEquals(0L, lock.tryWriteLock())
            }(),
        )

        lock.unlockRead(stamp)

        assertNull(startThread { lock.unlockWrite(lock.tryWriteLock()) }())
    }

    @Test
    fun writeLockExcludesReadersAndWriters() {
        val lock = newLock()
        val stamp = lock.writeLock()

        assertNull(
            startThread {
                assertEquals(0L, lock.tryReadLock())
                assertEquals(0L, lock.tryWriteLock())
                assertEquals(0L, lock.tryOptimisticRead())
            }(),
        )

        lock.unlockWrite(stamp)

        assertNull(startThread { lock.unlockRead(lock.tryReadLock()) }())
    }

    @Test
    fun aLockIsReleasedByAnotherThreadThanItsOwn() {
        val lock = newLock()
        val write = lock.writeLock()

        assertNull(startThread { lock.unlockWrite(write) }())
        assertFalse(lock.isWriteLocked())

        val read = lock.readLock()

        assertNull(startThread { lock.unlockRead(read) }())
        assertFalse(lock.isReadLocked())
    }

    @Test
    fun writerWaitsForTheReaders() {
        val lock = newLock()
        val started = AtomicBoolean(false)
        val written = AtomicBoolean(false)
        val stamp = lock.readLock()

        val join = startThread {
            started.store(true)

            val write = lock.writeLock()

            written.store(true)
            lock.unlockWrite(write)
        }

        while (!started.load()) {
            // Spins until the thread asks for the write lock
        }

        spinFor(BlockedDelay)
        assertFalse(written.load())

        lock.unlockRead(stamp)

        assertNull(join())
        assertTrue(written.load())
    }

    @Test
    fun waitingReadersAcquireTogetherOnceTheWriterReleases() {
        val lock = newLock()
        val started = AtomicInt(0)
        val inside = AtomicInt(0)
        val stamp = lock.writeLock()

        val joins = List(READER_COUNT) {
            startThread {
                val _ = started.incrementAndFetch()

                val read = lock.readLock()
                val _ = inside.incrementAndFetch()

                // Every reader gets in while the others still hold the read lock
                val start = TimeSource.Monotonic.markNow()

                while (inside.load() < READER_COUNT && start.elapsedNow() < SharedTimeout) {
                    // Spins until the readers are all in
                }

                val together = inside.load()
                lock.unlockRead(read)

                assertEquals(READER_COUNT, together)
            }
        }

        while (started.load() < READER_COUNT) {
            // Spins until the threads ask for the read lock
        }

        spinFor(BlockedDelay)
        assertEquals(0, inside.load())

        lock.unlockWrite(stamp)

        joins.forEach { assertNull(it()) }
        assertEquals(0, lock.getReadLockCount())
    }

    @Test
    fun readersWaitWhileAWriterTurnsItsLockIntoAReadLock() {
        val lock = newLock()
        val started = AtomicBoolean(false)
        val read = AtomicBoolean(false)
        val write = lock.writeLock()

        val join = startThread {
            started.store(true)

            val stamp = lock.readLock()

            read.store(true)
            lock.unlockRead(stamp)
        }

        while (!started.load()) {
            // Spins until the thread asks for the read lock
        }

        spinFor(BlockedDelay)
        assertFalse(read.load())

        // The waiting reader gets in as soon as the writer only reads
        val stamp = lock.tryConvertToReadLock(write)

        assertNotEquals(0L, stamp)
        assertNull(join())
        assertTrue(read.load())

        lock.unlockRead(stamp)
    }

    @Test
    fun moreReadersThanAStateHoldsAreAllCounted() {
        val lock = newLock()

        runConcurrently(THREAD_COUNT) {
            val stamps = LongArray(HOLDS_PER_THREAD)

            repeat(MANY_HOLDS_ROUND_COUNT) {
                for (index in stamps.indices) {
                    stamps[index] = lock.readLock()
                }

                assertTrue(lock.getReadLockCount() >= HOLDS_PER_THREAD)
                assertEquals(0L, lock.tryWriteLock())

                for (index in stamps.indices) {
                    lock.unlockRead(stamps[index])
                }
            }
        }

        assertEquals(0, lock.getReadLockCount())
        lock.unlockWrite(lock.tryWriteLock())
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
                when (random.nextInt(10)) {
                    0 -> {
                        val stamp = lock.writeLock()
                        first++
                        second++
                        val _ = writes.incrementAndFetch()
                        lock.unlock(stamp)
                    }

                    1 -> {
                        val stamp = lock.tryWriteLock()

                        if (stamp != 0L) {
                            first++
                            second++
                            val _ = writes.incrementAndFetch()
                            lock.unlockWrite(stamp)
                        }
                    }

                    2 -> {
                        // Writes, then goes on reading without letting a writer in between
                        val write = lock.writeLock()
                        first++
                        second++
                        val _ = writes.incrementAndFetch()
                        val written = first

                        val read = lock.tryConvertToReadLock(write)
                        val seenFirst = first
                        val seenSecond = second
                        lock.unlockRead(read)

                        assertNotEquals(0L, read)
                        assertEquals(written, seenFirst)
                        assertEquals(written, seenSecond)
                    }

                    3 -> {
                        // Reads, then writes if it is the only reader
                        val read = lock.readLock()
                        val seen = first
                        val write = lock.tryConvertToWriteLock(read)

                        if (write != 0L) {
                            // No thread wrote since the read
                            assertEquals(seen, first)
                            first++
                            second++
                            val _ = writes.incrementAndFetch()
                            lock.unlockWrite(write)
                        } else {
                            lock.unlockRead(read)
                        }
                    }

                    4 -> {
                        // Writes if no thread wrote since it looked
                        val optimistic = lock.tryOptimisticRead()
                        val seen = first
                        val write = lock.tryConvertToWriteLock(optimistic)

                        if (write != 0L) {
                            assertEquals(seen, first)
                            first++
                            second++
                            val _ = writes.incrementAndFetch()
                            lock.unlockWrite(write)
                        }
                    }

                    5 -> {
                        val stamp = lock.tryReadLock()

                        if (stamp != 0L) {
                            val seenFirst = first
                            val seenSecond = second
                            lock.unlock(stamp)

                            assertEquals(seenFirst, seenSecond)
                        }
                    }

                    6 -> {
                        // Reads without a lock, and with one when a thread wrote meanwhile
                        var stamp = lock.tryOptimisticRead()
                        var seenFirst = first
                        var seenSecond = second

                        if (!lock.validate(stamp)) {
                            stamp = lock.readLock()
                            seenFirst = first
                            seenSecond = second
                            lock.unlockRead(stamp)
                        }

                        assertEquals(seenFirst, seenSecond)
                    }

                    7 -> {
                        // Gives its read lock back for a stamp, which is valid until a write
                        val read = lock.readLock()
                        val seenFirst = first
                        val optimistic = lock.tryConvertToOptimisticRead(read)
                        val seenSecond = second

                        assertNotEquals(0L, optimistic)

                        if (lock.validate(optimistic)) {
                            assertEquals(seenFirst, seenSecond)
                        }
                    }

                    else -> {
                        val stamp = lock.readLock()
                        val seenFirst = first
                        val seenSecond = second
                        lock.unlockRead(stamp)

                        assertEquals(seenFirst, seenSecond)
                    }
                }
            }
        }

        assertEquals(writes.load(), first)
        assertEquals(first, second)
        assertEquals(0, lock.getReadLockCount())
        assertFalse(lock.isWriteLocked())
    }

    ///////////////////////////////////////////////////////////////////////////
    // Timeouts
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun timedTryOfTheWriteLockGivesUpWhileAThreadReads() {
        val lock = newLock()
        val stamp = lock.readLock()

        assertGivesUpThenAcquires(
            tryLock = lock::tryWriteLock,
            unlock = lock::unlockWrite,
            release = { lock.unlockRead(stamp) },
        )
    }

    @Test
    fun timedTryOfTheReadLockGivesUpWhileAThreadWrites() {
        val lock = newLock()
        val stamp = lock.writeLock()

        assertGivesUpThenAcquires(
            tryLock = lock::tryReadLock,
            unlock = lock::unlockRead,
            release = { lock.unlockWrite(stamp) },
        )
    }

    /**
     * Has another thread ask for a lock with a time limit, while the calling thread holds one
     * that keeps it out. It must give up, then acquire once the calling thread [release]s.
     */
    private fun assertGivesUpThenAcquires(
        tryLock: (Duration) -> Long,
        unlock: (Long) -> Unit,
        release: () -> Unit,
    ) {
        assertNull(
            startThread {
                val start = TimeSource.Monotonic.markNow()

                assertEquals(0L, tryLock(Duration.ZERO))
                assertEquals(0L, tryLock(ShortTimeout))
                assertTrue(start.elapsedNow() >= ShortTimeout)
            }(),
        )

        val join = startThread {
            val stamp = tryLock(LongTimeout)

            assertNotEquals(0L, stamp)
            unlock(stamp)
        }

        spinFor(BlockedDelay)
        release()

        assertNull(join())
    }

    /**
     * The readers follow each other without ever leaving the lock free: each one stays until
     * a reader that came after it is in. So the writer only gets in if the readers that ask
     * after it wait behind it. The last reader in is then alone, and leaves after a while.
     */
    @Test
    fun waitingWriterPassesBeforeTheReadersThatWait() {
        val lock = newLock()
        val entries = AtomicInt(0)
        val written = AtomicBoolean(false)
        val starved = AtomicBoolean(false)
        val start = TimeSource.Monotonic.markNow()

        runConcurrently(THREAD_COUNT) { thread ->
            if (thread == 0) {
                while (entries.load() < THREAD_COUNT) {
                    // Spins until the readers follow each other
                }

                val stamp = lock.writeLock()

                written.store(true)
                lock.unlockWrite(stamp)
            } else {
                while (!written.load()) {
                    if (start.elapsedNow() > SharedTimeout) {
                        starved.store(true)
                        break
                    }

                    // The try with a time limit is the one that looks at the queue first
                    val stamp = lock.tryReadLock(LongTimeout)
                    val entry = entries.incrementAndFetch()
                    val entered = TimeSource.Monotonic.markNow()

                    while (entries.load() == entry && entered.elapsedNow() < AloneDelay) {
                        // Spins until a reader came after this one, or none could
                    }

                    lock.unlockRead(stamp)
                }
            }
        }

        assertTrue(written.load())
        assertFalse(starved.load())
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
                        val stamp = lock.writeLock()
                        first++
                        spinFor(GiveUpHold)
                        second++
                        val _ = writes.incrementAndFetch()
                        lock.unlockWrite(stamp)
                    }

                    1 -> {
                        val stamp = lock.tryWriteLock(timeout)

                        if (stamp != 0L) {
                            first++
                            second++
                            val _ = writes.incrementAndFetch()
                            lock.unlockWrite(stamp)
                        }
                    }

                    2 -> {
                        val stamp = lock.readLock()
                        val seenFirst = first
                        spinFor(GiveUpHold)
                        val seenSecond = second
                        lock.unlockRead(stamp)

                        assertEquals(seenFirst, seenSecond)
                    }

                    else -> {
                        val stamp = lock.tryReadLock(timeout)

                        if (stamp != 0L) {
                            val seenFirst = first
                            val seenSecond = second
                            lock.unlockRead(stamp)

                            assertEquals(seenFirst, seenSecond)
                        }
                    }
                }
            }
        }

        assertEquals(writes.load(), first)
        assertEquals(first, second)
        assertLockIsFree(lock)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Interruption
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun anInterruptEndsWriteLockInterruptibly() {
        assertInterruptEndsTheWait(write = true) { lock -> lock.writeLockInterruptibly() }
    }

    @Test
    fun anInterruptEndsATimedTryOfTheWriteLock() {
        assertInterruptEndsTheWait(write = true) { lock -> lock.tryWriteLock(LongTimeout) }
    }

    @Test
    fun anInterruptEndsReadLockInterruptibly() {
        assertInterruptEndsTheWait(write = false) { lock -> lock.readLockInterruptibly() }
    }

    @Test
    fun anInterruptEndsATimedTryOfTheReadLock() {
        assertInterruptEndsTheWait(write = false) { lock -> lock.tryReadLock(LongTimeout) }
    }

    /**
     * Makes a thread [wait] for a lock that the calling thread keeps it out of, then interrupts
     * it. The wait must end with the exception, the flag cleared and the lock left as it was.
     * [write] tells whether the thread waits for the write lock.
     */
    private fun assertInterruptEndsTheWait(write: Boolean, wait: (TestStampedLock) -> Long) {
        val lock = newLock()
        val started = AtomicBoolean(false)

        // A reader keeps a writer out, and a writer keeps a reader out
        val stamp = if (write) lock.readLock() else lock.writeLock()

        val waiter = TestThread {
            started.store(true)

            assertFailsWith<InterruptedException> { val _ = wait(lock) }
            assertFalse(currentThread().isInterrupted())
        }

        while (!started.load()) {
            // Spins until the thread asks for the lock
        }

        spinFor(BlockedDelay)
        waiter.interrupt()

        assertNull(waiter.join())

        lock.unlock(stamp)
        assertLockIsFree(lock)
    }

    @Test
    fun anInterruptedThreadDoesNotTakeTheLock() {
        val lock = newLock()

        val thread = TestThread {
            val waits = listOf(
                lock::writeLockInterruptibly,
                lock::readLockInterruptibly,
                { lock.tryWriteLock(LongTimeout) },
                { lock.tryReadLock(LongTimeout) },
            )

            for (wait in waits) {
                currentThread().interrupt()

                assertFailsWith<InterruptedException> { val _ = wait() }
                assertFalse(interrupted())
            }
        }

        assertNull(thread.join())
        assertLockIsFree(lock)
    }

    @Test
    fun anInterruptDoesNotEndWriteLock() {
        val lock = newLock()
        val started = AtomicBoolean(false)
        val acquired = AtomicBoolean(false)
        val stamp = lock.readLock()

        val waiter = TestThread {
            started.store(true)

            val write = lock.writeLock()

            acquired.store(true)
            lock.unlockWrite(write)

            // The interrupt is kept for the thread to find
            assertTrue(interrupted())
        }

        while (!started.load()) {
            // Spins until the thread asks for the lock
        }

        spinFor(BlockedDelay)
        waiter.interrupt()
        spinFor(BlockedDelay)
        assertFalse(acquired.load())

        lock.unlockRead(stamp)

        assertNull(waiter.join())
        assertTrue(acquired.load())
    }

    // A thread that gave up must leave nothing that keeps another one out
    private fun assertLockIsFree(lock: TestStampedLock) {
        assertNull(
            startThread {
                lock.unlockWrite(lock.writeLock())
                lock.unlockRead(lock.readLock())
            }(),
        )

        assertFalse(lock.isWriteLocked())
        assertEquals(0, lock.getReadLockCount())
    }
}

/**
 * Tests the public [StampedLock] on real threads.
 */
class StampedLockStressTest : StampedLockConcurrencyContractTest() {

    override fun newLock(): TestStampedLock = StampedLock().asTestStampedLock()

    @Test
    fun viewsAndBlocksExcludeAsTheStampsDo() {
        val lock = StampedLock()
        val readView = lock.asReadLock()
        val writeView = lock.asWriteLock()
        var first = 0
        var second = 0

        runConcurrently(THREAD_COUNT) { thread ->
            repeat(ROUND_COUNT) { round ->
                when ((thread + round) % 4) {
                    0 -> writeView.withLock {
                        first++
                        second++
                    }

                    1 -> lock.write {
                        first++
                        second++
                    }

                    2 -> readView.withLock { assertEquals(first, second) }
                    else -> lock.read { assertEquals(first, second) }
                }
            }
        }

        assertEquals(THREAD_COUNT * ROUND_COUNT / 2, first)
        assertEquals(first, second)
        assertFalse(lock.isWriteLocked())
        assertEquals(0, lock.getReadLockCount())
    }
}
