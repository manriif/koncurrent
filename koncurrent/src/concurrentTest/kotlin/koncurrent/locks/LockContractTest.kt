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
import kotlin.concurrent.atomics.incrementAndFetch
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
private const val WAITER_COUNT = 16
private const val ROUND_COUNT = 50_000
private const val SLOW_ROUND_COUNT = 300
private const val GIVE_UP_ROUND_COUNT = 2_000

private val BlockedDelay = 50.milliseconds
private val HoldDelay = 100.milliseconds
private val ShortTimeout = 50.milliseconds
private val LongTimeout = 30.seconds

// What a thread holds the lock for while others give up, and the longest that those wait
private val GiveUpHold = 20.microseconds
private val GiveUpTimeout = 60.microseconds

/**
 * A reentrant lock under test, whatever its type.
 */
internal class TestLock(
    val lock: () -> Unit,
    val lockInterruptibly: () -> Unit,
    val tryLock: () -> Boolean,
    val tryLockFor: (Duration) -> Boolean,
    val unlock: () -> Unit,
)

/**
 * Returns a public [lock] as a lock under test.
 */
internal fun TestLock(lock: Lock): TestLock = TestLock(
    lock = lock::lock,
    lockInterruptibly = lock::lockInterruptibly,
    tryLock = lock::tryLock,
    tryLockFor = { lock.tryLock(it) },
    unlock = lock::unlock,
)

/**
 * Tests what several threads see of a reentrant lock.
 */
abstract class LockContractTest {

    internal abstract fun newLock(): TestLock

    @Test
    fun lockExcludesTheOtherThreads() {
        val lock = newLock()
        var count = 0

        runConcurrently(THREAD_COUNT) {
            repeat(ROUND_COUNT) {
                lock.lock()
                count++
                lock.unlock()
            }
        }

        assertEquals(THREAD_COUNT * ROUND_COUNT, count)
    }

    @Test
    fun waitingThreadsAcquireOneAfterTheOther() {
        val lock = newLock()
        var inside = 0
        var count = 0

        // The holder keeps the lock for long enough to send the other threads to sleep
        runConcurrently(WAITER_COUNT) {
            repeat(SLOW_ROUND_COUNT) {
                lock.lock()
                inside++
                assertEquals(1, inside)
                spinFor(HoldDelay / SLOW_ROUND_COUNT)
                count++
                inside--
                lock.unlock()
            }
        }

        assertEquals(WAITER_COUNT * SLOW_ROUND_COUNT, count)
    }

    @Test
    fun lockBlocksUntilTheHolderReleases() {
        val lock = newLock()
        val started = AtomicBoolean(false)
        val acquired = AtomicBoolean(false)

        lock.lock()

        val join = startThread {
            started.store(true)
            lock.lock()
            acquired.store(true)
            lock.unlock()
        }

        while (!started.load()) {
            // Spins until the thread asks for the lock
        }

        spinFor(BlockedDelay)
        assertFalse(acquired.load())

        lock.unlock()

        assertNull(join())
        assertTrue(acquired.load())
    }

    @Test
    fun tryLockFailsWhileAnotherThreadHoldsTheLock() {
        val lock = newLock()

        lock.lock()
        assertNull(startThread { assertFalse(lock.tryLock()) }())
        lock.unlock()

        assertNull(
            startThread {
                assertTrue(lock.tryLock())
                lock.unlock()
            }(),
        )
    }

    @Test
    fun reentrantHoldsAreAllReleasedBeforeAnotherThreadAcquires() {
        val lock = newLock()

        lock.lock()
        lock.lock()
        lock.unlock()
        assertNull(startThread { assertFalse(lock.tryLock()) }())
        lock.unlock()

        assertNull(
            startThread {
                assertTrue(lock.tryLock())
                lock.unlock()
            }(),
        )
    }

    @Test
    fun unlockFailsForAThreadThatDoesNotHoldTheLock() {
        val lock = newLock()

        assertFailsWith<IllegalMonitorStateException> { lock.unlock() }

        lock.lock()

        assertNull(
            startThread {
                assertFailsWith<IllegalMonitorStateException> { lock.unlock() }
            }(),
        )

        lock.unlock()
    }

    @Test
    fun tryLockTakesTurnsAcrossThreads() {
        val lock = newLock()
        val acquisitions = AtomicInt(0)
        var count = 0

        runConcurrently(THREAD_COUNT) {
            repeat(ROUND_COUNT) {
                if (lock.tryLock()) {
                    count++
                    val _ = acquisitions.incrementAndFetch()
                    lock.unlock()
                }
            }
        }

        assertEquals(acquisitions.load(), count)
        assertTrue(count > 0)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Timeouts
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun timedTryLockTakesAFreeLockAtOnce() {
        val lock = newLock()

        assertTrue(lock.tryLockFor(Duration.ZERO))
        assertTrue(lock.tryLockFor(LongTimeout))
        lock.unlock()
        lock.unlock()

        assertNull(
            startThread {
                assertTrue(lock.tryLockFor(Duration.ZERO))
                lock.unlock()
            }(),
        )
    }

    @Test
    fun timedTryLockGivesUpOnceItsTimeIsOver() {
        val lock = newLock()

        lock.lock()

        assertNull(
            startThread {
                val start = TimeSource.Monotonic.markNow()

                assertFalse(lock.tryLockFor(Duration.ZERO))
                assertFalse(lock.tryLockFor(ShortTimeout))
                assertTrue(start.elapsedNow() >= ShortTimeout)
            }(),
        )

        lock.unlock()
        assertLockIsFree(lock)
    }

    @Test
    fun timedTryLockAcquiresALockReleasedInTime() {
        val lock = newLock()
        val started = AtomicBoolean(false)

        lock.lock()

        val join = startThread {
            started.store(true)
            assertTrue(lock.tryLockFor(LongTimeout))
            lock.unlock()
        }

        while (!started.load()) {
            // Spins until the thread asks for the lock
        }

        spinFor(BlockedDelay)
        lock.unlock()

        assertNull(join())
    }

    @Test
    fun threadsThatGiveUpLetTheOthersAcquire() {
        val lock = newLock()
        val acquisitions = AtomicInt(0)
        var count = 0

        // Half of the threads wait for the lock without end. The others give up after a
        // time that is often too short.
        runConcurrently(THREAD_COUNT) { thread ->
            repeat(GIVE_UP_ROUND_COUNT) { round ->
                val acquired = if (thread % 2 == 0) {
                    lock.lock()
                    true
                } else {
                    lock.tryLockFor(GiveUpTimeout * (round % 4) / 3)
                }

                if (acquired) {
                    count++
                    val _ = acquisitions.incrementAndFetch()
                    spinFor(GiveUpHold)
                    lock.unlock()
                }
            }
        }

        assertEquals(acquisitions.load(), count)
        assertTrue(count >= THREAD_COUNT / 2 * GIVE_UP_ROUND_COUNT)
        assertLockIsFree(lock)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Interruption
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun anInterruptEndsLockInterruptibly() {
        assertInterruptEndsTheWait { lock -> lock.lockInterruptibly() }
    }

    @Test
    fun anInterruptEndsATimedTryLock() {
        assertInterruptEndsTheWait { lock -> val _ = lock.tryLockFor(LongTimeout) }
    }

    @Test
    fun anInterruptedThreadDoesNotTakeTheLock() {
        val lock = newLock()

        val thread = TestThread {
            currentThread().interrupt()

            assertFailsWith<InterruptedException> { lock.lockInterruptibly() }
            assertFalse(interrupted())

            currentThread().interrupt()

            assertFailsWith<InterruptedException> { val _ = lock.tryLockFor(LongTimeout) }
            assertFalse(interrupted())
        }

        assertNull(thread.join())
        assertLockIsFree(lock)
    }

    @Test
    fun anInterruptDoesNotEndLock() {
        val lock = newLock()
        val started = AtomicBoolean(false)
        val acquired = AtomicBoolean(false)

        lock.lock()

        val waiter = TestThread {
            started.store(true)
            lock.lock()
            acquired.store(true)
            lock.unlock()

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

        lock.unlock()

        assertNull(waiter.join())
        assertTrue(acquired.load())
    }

    /**
     * Makes a thread [wait] for a lock that is held, then interrupts it. The wait must end
     * with the exception, the flag cleared and the lock left as it was.
     */
    private fun assertInterruptEndsTheWait(wait: (TestLock) -> Unit) {
        val lock = newLock()
        val started = AtomicBoolean(false)

        lock.lock()

        val waiter = TestThread {
            started.store(true)

            assertFailsWith<InterruptedException> { wait(lock) }
            assertFalse(currentThread().isInterrupted())
        }

        while (!started.load()) {
            // Spins until the thread asks for the lock
        }

        spinFor(BlockedDelay)
        waiter.interrupt()

        assertNull(waiter.join())

        lock.unlock()
        assertLockIsFree(lock)
    }

    // A thread that gave up must leave nothing that keeps another one out
    private fun assertLockIsFree(lock: TestLock) {
        assertNull(
            startThread {
                lock.lock()
                lock.unlock()
            }(),
        )
    }
}

/**
 * Tests the public [ReentrantLock] on real threads.
 */
class ReentrantLockStressTest : LockContractTest() {

    override fun newLock(): TestLock = TestLock(ReentrantLock())
}

/**
 * Tests the public [ReentrantLock] on real threads when it is fair.
 */
class FairReentrantLockStressTest : LockContractTest() {

    override fun newLock(): TestLock = TestLock(ReentrantLock(true))
}
