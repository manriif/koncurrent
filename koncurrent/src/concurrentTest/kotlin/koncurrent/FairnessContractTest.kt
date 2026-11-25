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

import koncurrent.locks.TestLock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private const val WAITER_COUNT = 9

private val LongTimeout = 30.seconds

/**
 * What is fair under test, whatever its type: a lock, the write lock of a pair, or a semaphore
 * of one permit. One thread holds it at a time.
 *
 * [acquirers] are all the ways of waiting for it, which keep the order of arrival.
 * [queueLength] counts the threads that wait for it.
 */
internal class TestFairSynchronizer(
    val acquirers: List<() -> Unit>,
    val release: () -> Unit,
    val queueLength: () -> Int,
)

/**
 * Returns a fair [lock] as a synchronizer under test.
 */
internal fun TestFairSynchronizer(lock: TestLock, queueLength: () -> Int): TestFairSynchronizer =
    TestFairSynchronizer(
        acquirers = listOf(
            lock.lock,
            lock.lockInterruptibly,
            { assertTrue(lock.tryLockFor(LongTimeout)) },
        ),
        release = lock.unlock,
        queueLength = queueLength,
    )

/**
 * Returns a fair [semaphore] of one permit as a synchronizer under test.
 */
internal fun TestFairSynchronizer(
    semaphore: TestSemaphore,
    queueLength: () -> Int,
): TestFairSynchronizer = TestFairSynchronizer(
    acquirers = listOf(
        semaphore.acquire,
        { assertTrue(semaphore.tryAcquireFor(LongTimeout)) },
    ),
    release = semaphore.release,
    queueLength = queueLength,
)

/**
 * A fair read-write lock under test, whatever its type. [queueLength] counts the threads that
 * wait for it.
 */
internal class TestFairReadWriteLock(
    val read: TestLock,
    val write: TestLock,
    val queueLength: () -> Int,
)

/**
 * Tests that what is fair serves the threads in the order of their arrival.
 *
 * No test relies on a delay. Each one waits for the threads to be in the queue, which the ports
 * and the classes of the JDK tell. The public classes do not, so the suite does not run on
 * them where they wrap a port.
 */
abstract class FairnessContractTest {

    internal abstract fun newSynchronizer(): TestFairSynchronizer

    @Test
    fun waitingThreadsAcquireInTheOrderOfTheirArrival() {
        val synchronizer = newSynchronizer()
        val order = mutableListOf<Int>()

        synchronizer.acquirers.first()()

        val joins = List(WAITER_COUNT) { index ->
            val acquire = synchronizer.acquirers[index % synchronizer.acquirers.size]

            val join = startThread {
                acquire()
                order += index
                synchronizer.release()
            }

            awaitQueueLength(synchronizer.queueLength, index + 1)
            join
        }

        synchronizer.release()

        joins.forEach { assertNull(it()) }
        assertEquals(List(WAITER_COUNT) { it }, order)
    }

    @Test
    fun aThreadDoesNotTakeBackWhatAnotherThreadWaitsFor() {
        val synchronizer = newSynchronizer()

        for (acquire in synchronizer.acquirers) {
            var passed = false

            acquire()

            val waiter = startThread {
                acquire()
                passed = true
                synchronizer.release()
            }

            awaitQueueLength(synchronizer.queueLength, 1)

            // What is not fair is taken back here, long before the waiting thread wakes up
            synchronizer.release()
            acquire()

            assertTrue(passed)
            synchronizer.release()

            assertNull(waiter())
        }
    }
}

/**
 * Tests the order in which a fair read-write lock serves a thread that reads. Its write lock
 * is tested as any fair lock, by [FairnessContractTest].
 */
abstract class FairReadWriteLockContractTest {

    internal abstract fun newLock(): TestFairReadWriteLock

    @Test
    fun aReaderWaitsBehindAWriterThatIsNotFirst() {
        val lock = newLock()
        var written = false

        lock.write.lock()

        val reader = startThread {
            lock.read.lock()
            lock.read.unlock()
        }

        awaitQueueLength(lock.queueLength, 1)

        val writer = startThread {
            lock.write.lock()
            written = true
            lock.write.unlock()
        }

        awaitQueueLength(lock.queueLength, 2)

        // A lock that is not fair lets this thread read at once, since the first thread of the
        // queue reads too
        lock.write.unlock()
        lock.read.lock()

        assertTrue(written)
        lock.read.unlock()

        assertNull(reader())
        assertNull(writer())
    }
}

// Waits until [queueLength] counts [length] threads
private fun awaitQueueLength(queueLength: () -> Int, length: Int) {
    val start = TimeSource.Monotonic.markNow()

    while (queueLength() != length) {
        check(start.elapsedNow() < LongTimeout) { "The queue never held $length threads" }
    }
}
