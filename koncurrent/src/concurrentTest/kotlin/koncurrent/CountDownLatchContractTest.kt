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
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private const val THREAD_COUNT = 8
private const val WAITER_COUNT = 16
private const val ROUND_COUNT = 5_000

private val BlockedDelay = 50.milliseconds
private val ShortTimeout = 50.milliseconds
private val LongTimeout = 30.seconds

/**
 * A latch under test, whatever its type.
 */
internal class TestLatch(
    val await: () -> Unit,
    val awaitFor: (Duration) -> Boolean,
    val countDown: () -> Unit,
    val count: () -> Long,
)

/**
 * Tests what several threads see of a count down latch.
 */
abstract class CountDownLatchContractTest {

    internal abstract fun newLatch(count: Int): TestLatch

    @Test
    fun awaitBlocksUntilTheCountIsZero() {
        val latch = newLatch(2)
        val started = AtomicBoolean(false)
        val passed = AtomicBoolean(false)

        val join = startThread {
            started.store(true)
            latch.await()
            passed.store(true)
        }

        while (!started.load()) {
            // Spins until the thread waits
        }

        latch.countDown()
        spinFor(BlockedDelay)
        assertFalse(passed.load())

        latch.countDown()

        assertNull(join())
        assertTrue(passed.load())
    }

    @Test
    fun everyWaitingThreadPassesOnceTheCountIsZero() {
        val latch = newLatch(1)
        val started = AtomicInt(0)
        val passed = AtomicInt(0)

        val joins = List(WAITER_COUNT) {
            startThread {
                val _ = started.incrementAndFetch()

                latch.await()
                val _ = passed.incrementAndFetch()
            }
        }

        while (started.load() < WAITER_COUNT) {
            // Spins until the threads wait
        }

        spinFor(BlockedDelay)
        assertEquals(0, passed.load())

        latch.countDown()

        joins.forEach { assertNull(it()) }
        assertEquals(WAITER_COUNT, passed.load())
    }

    @Test
    fun threadsCountDownToZeroTogether() {
        val latch = newLatch(THREAD_COUNT * ROUND_COUNT)
        val passed = AtomicBoolean(false)

        val join = startThread {
            latch.await()
            passed.store(true)
        }

        runConcurrently(THREAD_COUNT) { thread ->
            // The last thread leaves one count, so that the latch stays closed
            val rounds = if (thread == THREAD_COUNT - 1) ROUND_COUNT - 1 else ROUND_COUNT

            repeat(rounds) { latch.countDown() }
        }

        assertEquals(1L, latch.count())
        assertFalse(passed.load())

        latch.countDown()

        assertNull(join())
        assertTrue(passed.load())
        assertEquals(0L, latch.count())
    }

    ///////////////////////////////////////////////////////////////////////////
    // Timeouts
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun timedAwaitGivesUpOnceItsTimeIsOver() {
        val latch = newLatch(1)
        val start = TimeSource.Monotonic.markNow()

        assertFalse(latch.awaitFor(Duration.ZERO))
        assertFalse(latch.awaitFor(ShortTimeout))
        assertTrue(start.elapsedNow() >= ShortTimeout)

        latch.countDown()

        assertTrue(latch.awaitFor(Duration.ZERO))
        assertTrue(latch.awaitFor(LongTimeout))
    }

    @Test
    fun timedAwaitReturnsWhenTheGateOpensInTime() {
        val latch = newLatch(1)
        val join = startThread { assertTrue(latch.awaitFor(LongTimeout)) }

        spinFor(BlockedDelay)
        latch.countDown()

        assertNull(join())
    }

    @Test
    fun theGateOpensForTheThreadsBehindThoseThatGaveUp() {
        val latch = newLatch(1)
        val gaveUp = AtomicInt(0)

        // Every other thread of the queue gives up
        val joins = List(WAITER_COUNT) { index ->
            startThread {
                if (index % 2 == 0) {
                    latch.await()
                } else {
                    assertFalse(latch.awaitFor(ShortTimeout))
                    val _ = gaveUp.incrementAndFetch()
                }
            }
        }

        while (gaveUp.load() < WAITER_COUNT / 2) {
            // Spins until the threads that give up did
        }

        latch.countDown()

        joins.forEach { assertNull(it()) }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Interruption
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun anInterruptEndsAwait() {
        assertInterruptEndsTheWait { latch -> latch.await() }
    }

    @Test
    fun anInterruptEndsATimedAwait() {
        assertInterruptEndsTheWait { latch -> val _ = latch.awaitFor(LongTimeout) }
    }

    @Test
    fun anInterruptedThreadDoesNotPassAnOpenGate() {
        val latch = newLatch(0)

        val thread = TestThread {
            currentThread().interrupt()

            assertFailsWith<InterruptedException> { latch.await() }
            assertFalse(interrupted())

            currentThread().interrupt()

            assertFailsWith<InterruptedException> { val _ = latch.awaitFor(LongTimeout) }
            assertFalse(interrupted())
        }

        assertNull(thread.join())
    }

    /**
     * Makes a thread [wait] at a closed gate, then interrupts it. The wait must end with the
     * exception and the flag cleared, and the gate must still open for another thread.
     */
    private fun assertInterruptEndsTheWait(wait: (TestLatch) -> Unit) {
        val latch = newLatch(1)

        val waiter = TestThread {
            assertFailsWith<InterruptedException> { wait(latch) }
            assertFalse(currentThread().isInterrupted())
        }

        val other = startThread { latch.await() }

        spinFor(BlockedDelay)
        waiter.interrupt()

        assertNull(waiter.join())

        latch.countDown()

        assertNull(other())
    }
}

/**
 * Tests the public [CountDownLatch] on real threads.
 */
class CountDownLatchStressTest : CountDownLatchContractTest() {

    override fun newLatch(count: Int): TestLatch {
        val latch = CountDownLatch(count)

        return TestLatch(
            await = { latch.await() },
            awaitFor = { latch.await(it) },
            countDown = { latch.countDown() },
            count = { latch.getCount() },
        )
    }
}
