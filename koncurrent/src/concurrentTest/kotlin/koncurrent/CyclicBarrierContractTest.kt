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
private const val ROUND_COUNT = 2_000

private val ShortTimeout = 50.milliseconds
private val LongTimeout = 30.seconds

// The longest that a thread waits for a barrier that must break
private val WakeUpTimeout = 10.seconds

/**
 * A cyclic barrier under test, whatever its type.
 */
internal class TestBarrier(
    val await: () -> Int,
    val awaitFor: (Duration) -> Int,
    val isBroken: () -> Boolean,
    val reset: () -> Unit,
    val numberWaiting: () -> Int,
)

/**
 * Returns a public [barrier] as a barrier under test.
 */
internal fun TestBarrier(barrier: CyclicBarrier): TestBarrier = TestBarrier(
    await = { barrier.await() },
    awaitFor = { barrier.await(it) },
    isBroken = { barrier.isBroken() },
    reset = { barrier.reset() },
    numberWaiting = { barrier.getNumberWaiting() },
)

/**
 * Tests what several threads see of a cyclic barrier.
 *
 * No test relies on a delay to know that a thread waits. Each one asks the barrier for the
 * number of threads that wait at it.
 */
abstract class CyclicBarrierContractTest {

    internal abstract fun newBarrier(parties: Int, action: (() -> Unit)? = null): TestBarrier

    @Test
    fun threadsPassOnceTheLastOneArrives() {
        val barrier = newBarrier(THREAD_COUNT)
        val passed = AtomicInt(0)
        val indexes = IntArray(THREAD_COUNT)

        val joins = List(THREAD_COUNT - 1) { thread ->
            startThread {
                indexes[thread] = barrier.await()
                val _ = passed.incrementAndFetch()
            }
        }

        barrier.awaitWaiting(THREAD_COUNT - 1)
        assertEquals(0, passed.load())

        // The last thread to arrive has the index zero, and the first one the highest
        indexes[THREAD_COUNT - 1] = barrier.await()
        joins.forEach { assertNull(it()) }

        assertEquals(0, indexes[THREAD_COUNT - 1])
        assertEquals(List(THREAD_COUNT) { it }, indexes.sorted())
        assertEquals(0, barrier.numberWaiting())
        assertFalse(barrier.isBroken())
    }

    @Test
    fun barrierServesRoundAfterRound() {
        val arrivals = AtomicInt(0)
        var trips = 0

        // The action runs alone, while every thread of the round is at the barrier
        val barrier = newBarrier(THREAD_COUNT) {
            trips++
            assertEquals(trips * THREAD_COUNT, arrivals.load())
        }

        runConcurrently(THREAD_COUNT) {
            repeat(ROUND_COUNT) {
                val _ = arrivals.incrementAndFetch()
                val _ = barrier.await()
            }
        }

        assertEquals(ROUND_COUNT, trips)
        assertFalse(barrier.isBroken())
    }

    @Test
    fun timedAwaitPassesWhenTheOthersArriveInTime() {
        val barrier = newBarrier(2)
        val first = startThread { assertEquals(1, barrier.awaitFor(LongTimeout)) }

        barrier.awaitWaiting(1)

        assertEquals(0, barrier.awaitFor(LongTimeout))
        assertNull(first())
    }

    @Test
    fun aTimeLimitThatRunsOutBreaksTheBarrier() {
        val barrier = newBarrier(3)
        val waiter = startThread { barrier.assertBreaksTheWait() }

        barrier.awaitWaiting(1)

        assertFailsWith<TimeoutException> { barrier.awaitFor(ShortTimeout) }
        assertNull(waiter())

        assertTrue(barrier.isBroken())
        assertEquals(0, barrier.numberWaiting())
        assertFailsWith<BrokenBarrierException> { barrier.await() }
        assertFailsWith<BrokenBarrierException> { barrier.awaitFor(LongTimeout) }
    }

    @Test
    fun aTimeLimitOfZeroDoesNotWait() {
        val barrier = newBarrier(2)

        assertFailsWith<TimeoutException> { barrier.awaitFor(Duration.ZERO) }
        assertTrue(barrier.isBroken())
    }

    @Test
    fun anInterruptBreaksTheBarrier() {
        assertInterruptBreaksTheBarrier { it.await() }
    }

    @Test
    fun anInterruptOfATimedAwaitBreaksTheBarrier() {
        assertInterruptBreaksTheBarrier { it.awaitFor(LongTimeout) }
    }

    /**
     * Interrupts one of two threads that wait at a barrier of three parties. That thread gets
     * the interrupt, and the other one learns that the barrier is broken.
     */
    private fun assertInterruptBreaksTheBarrier(wait: (TestBarrier) -> Int) {
        val barrier = newBarrier(3)

        val interrupted = TestThread {
            assertFailsWith<InterruptedException> { wait(barrier) }
            assertFalse(interrupted())
        }

        barrier.awaitWaiting(1)

        val other = startThread { barrier.assertBreaksTheWait() }

        barrier.awaitWaiting(2)
        interrupted.interrupt()

        assertNull(interrupted.join())
        assertNull(other())
        assertTrue(barrier.isBroken())
    }

    @Test
    fun anInterruptedThreadBreaksTheBarrierWhenItArrives() {
        val barrier = newBarrier(2)
        val waiter = startThread { barrier.assertBreaksTheWait() }

        barrier.awaitWaiting(1)

        val arriving = TestThread {
            currentThread().interrupt()

            assertFailsWith<InterruptedException> { barrier.await() }
            assertFalse(interrupted())
        }

        assertNull(arriving.join())
        assertNull(waiter())
        assertTrue(barrier.isBroken())
    }

    @Test
    fun resetFailsTheThreadsThatWaitAndRepairsTheBarrier() {
        val barrier = newBarrier(2)
        val waiter = startThread { barrier.assertBreaksTheWait() }

        barrier.awaitWaiting(1)
        barrier.reset()

        assertNull(waiter())
        assertFalse(barrier.isBroken())
        assertEquals(0, barrier.numberWaiting())

        val next = startThread { assertEquals(1, barrier.await()) }

        barrier.awaitWaiting(1)

        assertEquals(0, barrier.await())
        assertNull(next())
    }

    @Test
    fun anActionThatFailsBreaksTheBarrierForEveryThread() {
        val barrier = newBarrier(2) { throw IllegalArgumentException() }
        val waiter = startThread { barrier.assertBreaksTheWait() }

        barrier.awaitWaiting(1)

        // The last thread to arrive runs the action, and gets its failure
        assertFailsWith<IllegalArgumentException> { barrier.await() }
        assertNull(waiter())
        assertTrue(barrier.isBroken())
    }

    // Waits at the barrier until it breaks. The wait would end by itself after a while, and a
    // thread that leaves that late was not woken up, which fails the test instead of hanging.
    private fun TestBarrier.assertBreaksTheWait() {
        val start = TimeSource.Monotonic.markNow()

        assertFailsWith<BrokenBarrierException> { awaitFor(WakeUpTimeout) }
        assertTrue(start.elapsedNow() < WakeUpTimeout / 2)
    }

    // Waits until [count] threads wait at the barrier
    private fun TestBarrier.awaitWaiting(count: Int) {
        val start = TimeSource.Monotonic.markNow()

        while (numberWaiting() != count) {
            check(start.elapsedNow() < LongTimeout) { "$count threads never waited" }
        }
    }
}

/**
 * Tests the public [CyclicBarrier] on real threads.
 */
class CyclicBarrierStressTest : CyclicBarrierContractTest() {

    override fun newBarrier(parties: Int, action: (() -> Unit)?): TestBarrier =
        TestBarrier(CyclicBarrier(parties, action?.let { Runnable(it) }))
}
