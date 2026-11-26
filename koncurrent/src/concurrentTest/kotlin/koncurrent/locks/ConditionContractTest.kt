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

private const val WAITER_COUNT = 8
private const val PRODUCER_COUNT = 4
private const val CONSUMER_COUNT = 4
private const val ITEM_COUNT = 20_000
private const val CAPACITY = 4
private const val RACE_ROUND_COUNT = 300

private val BlockedDelay = 50.milliseconds
private val ShortTimeout = 50.milliseconds
private val LongTimeout = 30.seconds

// The time that a wait lasts when a signal is sent to race with its end, and the steps by
// which that signal comes earlier or later from one round to the next
private val RaceTimeout = 2.milliseconds
private val RaceStep = 10.microseconds
private const val RACE_STEP_COUNT = 20
private const val RACE_EARLY_STEP_COUNT = 15

// The signals handed one at a time to threads that wait for a time often too short
private const val HAND_OFF_COUNT = 10_000
private const val HAND_OFF_WAITER_COUNT = 4

private val HandOffTimeout = 20.microseconds
private val TakeTimeout = 10.seconds

/**
 * A condition under test, whatever its type.
 */
internal class TestCondition(
    val await: () -> Unit,
    val awaitFor: (Duration) -> Boolean,
    val signal: () -> Unit,
    val signalAll: () -> Unit,
)

/**
 * A reentrant lock under test with its conditions, whatever its type.
 */
internal class TestConditionLock(
    val lock: TestLock,
    val newCondition: () -> TestCondition,
)

/**
 * Returns a public [lock] as a lock under test with its conditions.
 */
internal fun TestConditionLock(lock: ReentrantLock): TestConditionLock = TestConditionLock(
    lock = TestLock(lock),
    newCondition = {
        val condition = lock.newCondition()

        TestCondition(
            await = { condition.await() },
            awaitFor = { condition.await(it) },
            signal = { condition.signal() },
            signalAll = { condition.signalAll() },
        )
    },
)

/**
 * Tests what several threads see of the conditions of a reentrant lock.
 */
abstract class ConditionContractTest {

    internal abstract fun newLock(): TestConditionLock

    @Test
    fun awaitGivesTheLockUpUntilSignalled() {
        val locks = newLock()
        val lock = locks.lock
        val condition = locks.newCondition()
        val locked = AtomicBoolean(false)
        var ready = false

        val join = startThread {
            // Two holds, to check that the wait gives both up and takes both back
            lock.lock()
            lock.lock()
            locked.store(true)

            while (!ready) {
                condition.await()
            }

            lock.unlock()
            lock.unlock()

            assertFailsWith<IllegalMonitorStateException> { lock.unlock() }
        }

        while (!locked.load()) {
            // Spins until the thread holds the lock
        }

        // Only acquired once the thread waits
        lock.lock()
        ready = true
        condition.signal()
        lock.unlock()

        assertNull(join())
    }

    @Test
    fun signalWakesOneThreadAndSignalAllWakesTheOthers() {
        val locks = newLock()
        val lock = locks.lock
        val condition = locks.newCondition()
        var waiting = 0
        var permits = 0
        var wakeUps = 0
        var served = 0

        val joins = List(WAITER_COUNT) {
            startThread {
                lock.lock()
                waiting++

                while (permits == 0) {
                    condition.await()
                    wakeUps++
                }

                permits--
                served++
                lock.unlock()
            }
        }

        awaitState(lock) { waiting == WAITER_COUNT }

        lock.lock()
        permits = 1
        condition.signal()
        lock.unlock()

        awaitState(lock) { served == 1 }
        spinFor(BlockedDelay)

        lock.lock()
        val wakeUpsOfSignal = wakeUps
        permits = WAITER_COUNT - 1
        condition.signalAll()
        lock.unlock()

        joins.forEach { assertNull(it()) }

        assertEquals(1, wakeUpsOfSignal)
        assertEquals(WAITER_COUNT, wakeUps)
        assertEquals(WAITER_COUNT, served)
        assertEquals(0, permits)
    }

    @Test
    fun producersAndConsumersExchangeEveryItem() {
        val locks = newLock()
        val lock = locks.lock
        val notFull = locks.newCondition()
        val notEmpty = locks.newCondition()
        val items = ArrayDeque<Int>()
        var produced = 0L
        var consumed = 0L

        runConcurrently(PRODUCER_COUNT + CONSUMER_COUNT) { thread ->
            var sum = 0L

            if (thread < PRODUCER_COUNT) {
                repeat(ITEM_COUNT) { round ->
                    val item = thread * ITEM_COUNT + round

                    lock.lock()

                    while (items.size == CAPACITY) {
                        notFull.await()
                    }

                    items.addLast(item)
                    notEmpty.signal()
                    lock.unlock()

                    sum += item
                }

                lock.lock()
                produced += sum
                lock.unlock()
            } else {
                repeat(PRODUCER_COUNT * ITEM_COUNT / CONSUMER_COUNT) {
                    lock.lock()

                    while (items.isEmpty()) {
                        notEmpty.await()
                    }

                    sum += items.removeFirst()
                    notFull.signal()
                    lock.unlock()
                }

                lock.lock()
                consumed += sum
                lock.unlock()
            }
        }

        assertEquals(0, items.size)
        assertEquals(produced, consumed)
    }

    @Test
    fun conditionFailsForAThreadThatDoesNotHoldTheLock() {
        val locks = newLock()
        val condition = locks.newCondition()

        locks.lock.lock()

        assertNull(
            startThread {
                assertFailsWith<IllegalMonitorStateException> { condition.await() }
                assertFailsWith<IllegalMonitorStateException> { condition.signal() }
                assertFailsWith<IllegalMonitorStateException> { condition.signalAll() }
            }(),
        )

        locks.lock.unlock()
    }

    ///////////////////////////////////////////////////////////////////////////
    // Timeouts
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun timedAwaitReturnsFalseOnceItsTimeIsOver() {
        val locks = newLock()
        val lock = locks.lock
        val condition = locks.newCondition()

        lock.lock()
        lock.lock()

        val start = TimeSource.Monotonic.markNow()

        assertFalse(condition.awaitFor(Duration.ZERO))
        assertFalse(condition.awaitFor(ShortTimeout))
        assertTrue(start.elapsedNow() >= ShortTimeout)

        // The wait gave both holds back
        lock.unlock()
        lock.unlock()
        assertFailsWith<IllegalMonitorStateException> { lock.unlock() }
    }

    @Test
    fun timedAwaitReturnsTrueWhenSignalledInTime() {
        val locks = newLock()
        val lock = locks.lock
        val condition = locks.newCondition()
        val locked = AtomicBoolean(false)

        val join = startThread {
            lock.lock()
            locked.store(true)
            assertTrue(condition.awaitFor(LongTimeout))
            lock.unlock()
        }

        while (!locked.load()) {
            // Spins until the thread holds the lock, which it only gives up by waiting
        }

        lock.lock()
        condition.signal()
        lock.unlock()

        assertNull(join())
    }

    @Test
    fun aWaitThatRanOutDoesNotTakeASignal() {
        assertSignalPassesAWaitThatEnded(
            wait = { condition -> assertFalse(condition.awaitFor(ShortTimeout)) },
            end = {},
        )
    }

    @Test
    fun aSignalIsNotLostToAWaitThatRunsOutAtTheSameTime() {
        repeat(RACE_ROUND_COUNT) { round ->
            val locks = newLock()
            val lock = locks.lock
            val condition = locks.newCondition()
            val waiting = AtomicInt(0)
            val firstSignalled = AtomicBoolean(false)
            var firstWaits = TimeSource.Monotonic.markNow()

            val first = startThread {
                lock.lock()
                firstWaits = TimeSource.Monotonic.markNow()
                val _ = waiting.incrementAndFetch()
                firstSignalled.store(condition.awaitFor(RaceTimeout))
                lock.unlock()
            }

            while (waiting.load() < 1) {
                // Spins until the first thread holds the lock, so that it waits first
            }

            val second = startThread {
                lock.lock()
                val _ = waiting.incrementAndFetch()
                assertTrue(condition.awaitFor(LongTimeout))
                lock.unlock()
            }

            while (waiting.load() < 2) {
                // Spins until the second thread holds the lock
            }

            // Both threads wait once the lock is free
            lock.lock()
            lock.unlock()

            // The signal comes about when the time of the first thread runs out, a little
            // earlier or later from one round to the next
            val early = RaceStep * (RACE_EARLY_STEP_COUNT - round % RACE_STEP_COUNT)

            while (firstWaits.elapsedNow() < RaceTimeout - early) {
                // Spins until the moment to signal
            }

            lock.lock()
            condition.signal()
            lock.unlock()

            assertNull(first())

            // The signal was for the second thread if the first one says that it got none
            if (firstSignalled.load()) {
                lock.lock()
                condition.signal()
                lock.unlock()
            }

            assertNull(second())
        }
    }

    @Test
    fun aSignalledThreadPassesOneThatGaveUpOnTheLock() {
        val locks = newLock()
        val lock = locks.lock
        val condition = locks.newCondition()
        val locked = AtomicBoolean(false)

        // It wakes up by itself after a while, so that a lost wake-up fails this test
        val waiter = startThread {
            lock.lock()
            locked.store(true)
            assertTrue(condition.awaitFor(TakeTimeout))
            lock.unlock()
        }

        while (!locked.load()) {
            // Spins until the thread holds the lock, which it only gives up by waiting
        }

        lock.lock()

        // This thread gives up while the lock is held, and leaves its node in the queue
        assertNull(startThread { assertFalse(lock.tryLockFor(ShortTimeout)) }())

        // The signal puts the waiting thread behind that node
        condition.signal()

        val release = TimeSource.Monotonic.markNow()

        lock.unlock()

        assertNull(waiter())

        // The release woke the thread up, not the end of its own time
        assertTrue(release.elapsedNow() < TakeTimeout / 2)
    }

    @Test
    fun everySignalIsTakenByAThreadThatReportsIt() {
        val locks = newLock()
        val lock = locks.lock
        val condition = locks.newCondition()
        val sentinelWaits = AtomicBoolean(false)
        var tokens = 0
        var taken = 0
        var done = false

        // A thread only takes a token when its wait reports a signal
        fun waitForTokens(timeout: (round: Int) -> Duration) {
            var round = 0

            lock.lock()

            while (!done) {
                if (condition.awaitFor(timeout(round++)) && !done) {
                    assertEquals(1, tokens)
                    tokens--
                    taken++
                }
            }

            lock.unlock()
        }

        // This thread never gives up, so that every signal finds a thread to wake up
        val sentinel = startThread {
            waitForTokens { round ->
                if (round == 0) {
                    sentinelWaits.store(true)
                }

                LongTimeout
            }
        }

        while (!sentinelWaits.load()) {
            // Spins until the sentinel holds the lock, which it only gives up by waiting
        }

        // These give up all the time. A signal often comes just as one of them does.
        val waiters = List(HAND_OFF_WAITER_COUNT) {
            startThread { waitForTokens { round -> HandOffTimeout * (round % 4 + 1) } }
        }

        // These ask for the lock and give up too, which leaves their nodes in its queue
        val stopped = AtomicBoolean(false)

        val passersBy = List(HAND_OFF_WAITER_COUNT) {
            startThread {
                while (!stopped.load()) {
                    if (lock.tryLockFor(HandOffTimeout)) {
                        lock.unlock()
                    }
                }
            }
        }

        try {
            repeat(HAND_OFF_COUNT) {
                lock.lock()
                tokens++
                condition.signal()
                lock.unlock()

                // The signal is lost if the thread that got it says that its time ran out
                val start = TimeSource.Monotonic.markNow()

                awaitState(lock) {
                    assertTrue(start.elapsedNow() < TakeTimeout)
                    tokens == 0
                }
            }
        } finally {
            stopped.store(true)
            lock.lock()
            done = true
            condition.signalAll()
            lock.unlock()
        }

        assertNull(sentinel())
        (waiters + passersBy).forEach { assertNull(it()) }
        assertEquals(HAND_OFF_COUNT, taken)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Interruption
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun anInterruptEndsAWaitWithTheLockHeldAgain() {
        val locks = newLock()
        val lock = locks.lock
        val condition = locks.newCondition()
        val locked = AtomicBoolean(false)

        val waiter = TestThread {
            lock.lock()
            lock.lock()
            locked.store(true)

            assertFailsWith<InterruptedException> { condition.await() }
            assertFalse(currentThread().isInterrupted())

            // The wait gave both holds back
            lock.unlock()
            lock.unlock()
            assertFailsWith<IllegalMonitorStateException> { lock.unlock() }
        }

        while (!locked.load()) {
            // Spins until the thread holds the lock, which it only gives up by waiting
        }

        lock.lock()
        lock.unlock()
        waiter.interrupt()

        assertNull(waiter.join())
    }

    @Test
    fun anInterruptEndsATimedWait() {
        val locks = newLock()
        val lock = locks.lock
        val condition = locks.newCondition()
        val locked = AtomicBoolean(false)

        val waiter = TestThread {
            lock.lock()
            locked.store(true)

            assertFailsWith<InterruptedException> { val _ = condition.awaitFor(LongTimeout) }
            assertFalse(currentThread().isInterrupted())

            lock.unlock()
        }

        while (!locked.load()) {
            // Spins until the thread holds the lock, which it only gives up by waiting
        }

        lock.lock()
        lock.unlock()
        waiter.interrupt()

        assertNull(waiter.join())
    }

    @Test
    fun anInterruptedThreadDoesNotWait() {
        val locks = newLock()
        val lock = locks.lock
        val condition = locks.newCondition()

        val thread = TestThread {
            lock.lock()
            currentThread().interrupt()

            assertFailsWith<InterruptedException> { condition.await() }
            assertFalse(interrupted())

            currentThread().interrupt()

            assertFailsWith<InterruptedException> { val _ = condition.awaitFor(LongTimeout) }
            assertFalse(interrupted())

            lock.unlock()
        }

        assertNull(thread.join())
    }

    @Test
    fun aWaitEndedByAnInterruptDoesNotTakeASignal() {
        assertSignalPassesAWaitThatEnded(
            wait = { condition -> assertFailsWith<InterruptedException> { condition.await() } },
            end = { waiter -> waiter.interrupt() },
        )
    }

    /**
     * Makes a first thread [wait] on a condition, and a second one wait behind it. Once [end]
     * made the first one give up, a single signal must wake up the second one.
     */
    private fun assertSignalPassesAWaitThatEnded(
        wait: (TestCondition) -> Unit,
        end: (TestThread) -> Unit,
    ) {
        val locks = newLock()
        val lock = locks.lock
        val condition = locks.newCondition()
        val firstLocked = AtomicBoolean(false)
        val secondLocked = AtomicBoolean(false)
        val gaveUp = AtomicBoolean(false)
        var signalled = false

        val first = TestThread {
            lock.lock()
            firstLocked.store(true)
            wait(condition)
            gaveUp.store(true)
            lock.unlock()
        }

        while (!firstLocked.load()) {
            // Spins until the first thread holds the lock, so that it waits first
        }

        val second = startThread {
            lock.lock()
            secondLocked.store(true)

            while (!signalled) {
                condition.await()
            }

            lock.unlock()
        }

        while (!secondLocked.load()) {
            // Spins until the second thread holds the lock, which it only gives up by waiting
        }

        lock.lock()
        lock.unlock()
        end(first)

        while (!gaveUp.load()) {
            // Spins until the first thread gave up
        }

        lock.lock()
        signalled = true
        condition.signal()
        lock.unlock()

        assertNull(first.join())
        assertNull(second())
    }

    /**
     * Waits until what the [lock] guards has [reached] a state.
     */
    private fun awaitState(lock: TestLock, reached: () -> Boolean) {
        while (true) {
            lock.lock()
            val done = reached()
            lock.unlock()

            if (done) {
                return
            }
        }
    }
}

/**
 * Tests the conditions of the public [ReentrantLock] on real threads.
 */
class ConditionStressTest : ConditionContractTest() {

    override fun newLock(): TestConditionLock = TestConditionLock(ReentrantLock())
}

/**
 * Tests the conditions of the public [ReentrantLock] on real threads when it is fair.
 */
class FairConditionStressTest : ConditionContractTest() {

    override fun newLock(): TestConditionLock = TestConditionLock(ReentrantLock(true))
}
