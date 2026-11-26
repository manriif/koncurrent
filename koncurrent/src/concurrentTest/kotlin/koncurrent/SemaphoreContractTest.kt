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
package koncurrent

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.decrementAndFetch
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
private const val PERMIT_COUNT = 3
private const val ROUND_COUNT = 20_000
private const val GIVE_UP_ROUND_COUNT = 2_000

private val BlockedDelay = 50.milliseconds
private val ShortTimeout = 50.milliseconds
private val LongTimeout = 30.seconds

// What a thread holds a permit for while others give up, and the longest that those wait
private val GiveUpHold = 20.microseconds
private val GiveUpTimeout = 60.microseconds

/**
 * A semaphore under test, whatever its type.
 */
internal class TestSemaphore(
    val acquire: () -> Unit,
    val tryAcquire: () -> Boolean,
    val tryAcquireFor: (Duration) -> Boolean,
    val release: () -> Unit,
    val availablePermits: () -> Int,
)

/**
 * Returns a public [semaphore] as a semaphore under test.
 */
internal fun TestSemaphore(semaphore: Semaphore): TestSemaphore = TestSemaphore(
    acquire = { semaphore.acquire() },
    tryAcquire = { semaphore.tryAcquire() },
    tryAcquireFor = { semaphore.tryAcquire(it) },
    release = { semaphore.release() },
    availablePermits = { semaphore.availablePermits() },
)

/**
 * Tests what several threads see of a semaphore.
 */
abstract class SemaphoreContractTest {

    internal abstract fun newSemaphore(permits: Int): TestSemaphore

    @Test
    fun permitsLimitTheThreadsInside() {
        val semaphore = newSemaphore(PERMIT_COUNT)
        val inside = AtomicInt(0)
        val exceeded = AtomicBoolean(false)

        runConcurrently(THREAD_COUNT) {
            repeat(ROUND_COUNT) {
                semaphore.acquire()

                if (inside.incrementAndFetch() > PERMIT_COUNT) {
                    exceeded.store(true)
                }

                val _ = inside.decrementAndFetch()
                semaphore.release()
            }
        }

        assertFalse(exceeded.load())
        assertEquals(PERMIT_COUNT, semaphore.availablePermits())
    }

    @Test
    fun onePermitExcludesTheOtherThreads() {
        val semaphore = newSemaphore(1)
        var count = 0

        runConcurrently(THREAD_COUNT) {
            repeat(ROUND_COUNT) {
                semaphore.acquire()
                count++
                semaphore.release()
            }
        }

        assertEquals(THREAD_COUNT * ROUND_COUNT, count)
    }

    @Test
    fun acquireBlocksUntilAPermitIsReleased() {
        val semaphore = newSemaphore(0)
        val started = AtomicBoolean(false)
        val acquired = AtomicBoolean(false)

        val join = startThread {
            started.store(true)
            semaphore.acquire()
            acquired.store(true)
        }

        while (!started.load()) {
            // Spins until the thread asks for a permit
        }

        spinFor(BlockedDelay)
        assertFalse(acquired.load())

        semaphore.release()

        assertNull(join())
        assertTrue(acquired.load())
        assertEquals(0, semaphore.availablePermits())
    }

    @Test
    fun everyWaitingThreadGetsItsPermit() {
        val semaphore = newSemaphore(0)
        val started = AtomicInt(0)
        val acquired = AtomicInt(0)

        val joins = List(WAITER_COUNT) {
            startThread {
                val _ = started.incrementAndFetch()

                semaphore.acquire()
                val _ = acquired.incrementAndFetch()
            }
        }

        while (started.load() < WAITER_COUNT) {
            // Spins until the threads ask for a permit
        }

        spinFor(BlockedDelay)
        assertEquals(0, acquired.load())

        // The permits come at once. Each thread that takes one wakes up the next.
        repeat(WAITER_COUNT) { semaphore.release() }

        joins.forEach { assertNull(it()) }

        assertEquals(WAITER_COUNT, acquired.load())
        assertEquals(0, semaphore.availablePermits())
    }

    @Test
    fun tryAcquireNeverTakesMoreThanThePermits() {
        val semaphore = newSemaphore(PERMIT_COUNT)
        val inside = AtomicInt(0)
        val exceeded = AtomicBoolean(false)
        val acquisitions = AtomicInt(0)

        runConcurrently(THREAD_COUNT) {
            repeat(ROUND_COUNT) {
                if (semaphore.tryAcquire()) {
                    if (inside.incrementAndFetch() > PERMIT_COUNT) {
                        exceeded.store(true)
                    }

                    val _ = acquisitions.incrementAndFetch()
                    val _ = inside.decrementAndFetch()
                    semaphore.release()
                }
            }
        }

        assertFalse(exceeded.load())
        assertTrue(acquisitions.load() > 0)
        assertEquals(PERMIT_COUNT, semaphore.availablePermits())
    }

    ///////////////////////////////////////////////////////////////////////////
    // Timeouts
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun timedTryAcquireGivesUpOnceItsTimeIsOver() {
        val semaphore = newSemaphore(0)
        val start = TimeSource.Monotonic.markNow()

        assertFalse(semaphore.tryAcquireFor(Duration.ZERO))
        assertFalse(semaphore.tryAcquireFor(ShortTimeout))
        assertTrue(start.elapsedNow() >= ShortTimeout)
        assertEquals(0, semaphore.availablePermits())

        semaphore.release()

        assertTrue(semaphore.tryAcquireFor(Duration.ZERO))
        assertEquals(0, semaphore.availablePermits())
    }

    @Test
    fun timedTryAcquireTakesAPermitReleasedInTime() {
        val semaphore = newSemaphore(0)
        val join = startThread { assertTrue(semaphore.tryAcquireFor(LongTimeout)) }

        spinFor(BlockedDelay)
        semaphore.release()

        assertNull(join())
        assertEquals(0, semaphore.availablePermits())
    }

    @Test
    fun permitsAreNeitherLostNorMadeWhenThreadsGiveUp() {
        val semaphore = newSemaphore(PERMIT_COUNT)
        val inside = AtomicInt(0)
        val acquisitions = AtomicInt(0)

        // Half of the threads wait for a permit without end. The others give up after a time
        // that is often too short.
        runConcurrently(THREAD_COUNT) { thread ->
            repeat(GIVE_UP_ROUND_COUNT) { round ->
                val acquired = if (thread % 2 == 0) {
                    semaphore.acquire()
                    true
                } else {
                    semaphore.tryAcquireFor(GiveUpTimeout * (round % 4) / 3)
                }

                if (acquired) {
                    assertTrue(inside.incrementAndFetch() <= PERMIT_COUNT)
                    spinFor(GiveUpHold)

                    val _ = inside.decrementAndFetch()
                    val _ = acquisitions.incrementAndFetch()

                    semaphore.release()
                }
            }
        }

        assertEquals(PERMIT_COUNT, semaphore.availablePermits())
        assertTrue(acquisitions.load() >= THREAD_COUNT / 2 * GIVE_UP_ROUND_COUNT)
    }

    @Test
    fun permitsReleasedTogetherPassTheThreadsThatGaveUp() {
        val semaphore = newSemaphore(0)
        val gaveUp = AtomicInt(0)

        // Every other thread of the queue gives up
        val joins = List(WAITER_COUNT) { index ->
            startThread {
                if (index % 2 == 0) {
                    semaphore.acquire()
                } else {
                    assertFalse(semaphore.tryAcquireFor(ShortTimeout))
                    val _ = gaveUp.incrementAndFetch()
                }
            }
        }

        while (gaveUp.load() < WAITER_COUNT / 2) {
            // Spins until the threads that give up did
        }

        repeat(WAITER_COUNT / 2) { semaphore.release() }

        joins.forEach { assertNull(it()) }
        assertEquals(0, semaphore.availablePermits())
    }

    ///////////////////////////////////////////////////////////////////////////
    // Interruption
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun anInterruptEndsAcquire() {
        assertInterruptEndsTheWait { semaphore -> semaphore.acquire() }
    }

    @Test
    fun anInterruptEndsATimedTryAcquire() {
        assertInterruptEndsTheWait { semaphore -> val _ = semaphore.tryAcquireFor(LongTimeout) }
    }

    @Test
    fun anInterruptedThreadDoesNotTakeAPermit() {
        val semaphore = newSemaphore(1)

        val thread = TestThread {
            currentThread().interrupt()

            assertFailsWith<InterruptedException> { semaphore.acquire() }
            assertFalse(interrupted())

            currentThread().interrupt()

            assertFailsWith<InterruptedException> { val _ = semaphore.tryAcquireFor(LongTimeout) }
            assertFalse(interrupted())
        }

        assertNull(thread.join())
        assertEquals(1, semaphore.availablePermits())
    }

    /**
     * Makes a thread [wait] for a permit that is not there, then interrupts it. The wait must
     * end with the exception, the flag cleared, and a permit released later still there.
     */
    private fun assertInterruptEndsTheWait(wait: (TestSemaphore) -> Unit) {
        val semaphore = newSemaphore(0)

        val waiter = TestThread {
            assertFailsWith<InterruptedException> { wait(semaphore) }
            assertFalse(currentThread().isInterrupted())
        }

        spinFor(BlockedDelay)
        waiter.interrupt()

        assertNull(waiter.join())

        semaphore.release()

        assertEquals(1, semaphore.availablePermits())
        assertTrue(semaphore.tryAcquire())
    }
}

/**
 * Tests the public [Semaphore] on real threads.
 */
class SemaphoreStressTest : SemaphoreContractTest() {

    override fun newSemaphore(permits: Int): TestSemaphore = TestSemaphore(Semaphore(permits))
}

/**
 * Tests the public [Semaphore] on real threads when it is fair.
 */
class FairSemaphoreStressTest : SemaphoreContractTest() {

    override fun newSemaphore(permits: Int): TestSemaphore =
        TestSemaphore(Semaphore(permits, true))
}
