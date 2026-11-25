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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private const val PRODUCER_COUNT = 4
private const val CONSUMER_COUNT = 4
private const val ELEMENT_COUNT = 20_000
private const val WAITER_COUNT = 16

private val BlockedDelay = 50.milliseconds
private val ShortTimeout = 50.milliseconds
private val LongTimeout = 30.seconds

/**
 * Tests what several threads see of a blocking queue that hands its smallest element over
 * first.
 */
abstract class PriorityBlockingQueueContractTest {

    /**
     * Creates a queue in the natural order of its elements.
     */
    internal abstract fun <E : Any> newQueue(): TestQueue<E>

    @Test
    fun producersAndConsumersExchangeEveryElement() {
        val queue = newQueue<Int>()
        val total = PRODUCER_COUNT * ELEMENT_COUNT
        val toTake = AtomicInt(total)
        val consumed = List(CONSUMER_COUNT) { ArrayList<Int>() }

        // The queue grows while the consumers take
        runConcurrently(PRODUCER_COUNT + CONSUMER_COUNT) { thread ->
            if (thread < PRODUCER_COUNT) {
                repeat(ELEMENT_COUNT) { sequence ->
                    val element = thread * ELEMENT_COUNT + sequence

                    when (sequence % 3) {
                        0 -> queue.put(element)
                        1 -> assertTrue(queue.offer(element))
                        else -> assertTrue(queue.offerFor(element, ShortTimeout))
                    }
                }
            } else {
                val taken = consumed[thread - PRODUCER_COUNT]

                while (toTake.decrementAndFetch() >= 0) {
                    taken += if (taken.size % 2 == 0) {
                        queue.take()
                    } else {
                        checkNotNull(queue.pollFor(LongTimeout))
                    }
                }
            }
        }

        assertEquals(List(total) { it }, consumed.flatten().sorted())
        assertEquals(0, queue.size)
        assertNull(queue.poll())
    }

    @Test
    fun smallestElementsLeaveFirstOnceTheProducersAreDone() {
        val queue = newQueue<Int>()
        val total = PRODUCER_COUNT * ELEMENT_COUNT
        val drained = ArrayList<Int>()

        // Each producer adds its elements from the largest to the smallest
        runConcurrently(PRODUCER_COUNT) { thread ->
            for (sequence in ELEMENT_COUNT - 1 downTo 0) {
                queue.put(sequence * PRODUCER_COUNT + thread)
            }
        }

        assertEquals(total, queue.size)
        assertEquals(0, queue.peek())
        assertEquals(total, queue.drainAllTo(drained))
        assertEquals(List(total) { it }, drained)
    }

    @Test
    fun removeTakesEachElementOnce() {
        val queue = newQueue<Int>()
        val removed = AtomicInt(0)

        repeat(ELEMENT_COUNT / 10) { queue.put(it) }

        // Every thread goes after every element, from its own starting point
        runConcurrently(PRODUCER_COUNT + CONSUMER_COUNT) { thread ->
            repeat(ELEMENT_COUNT / 10) { index ->
                val element = (index + thread * 131) % (ELEMENT_COUNT / 10)

                if (queue.collection.remove(element)) {
                    val _ = removed.incrementAndFetch()
                }
            }
        }

        assertEquals(ELEMENT_COUNT / 10, removed.load())
        assertEquals(0, queue.size)
    }

    @Test
    fun iterationAndSearchGoOnDuringAnExchange() {
        val queue = newQueue<Int>()
        val running = AtomicBoolean(true)

        val exchange = startThread {
            repeat(ELEMENT_COUNT) { round ->
                queue.put(round)
                queue.put(round)

                if (round % 3 == 0) {
                    val _ = queue.collection.remove(round)
                } else {
                    val _ = queue.take()
                }

                if (queue.size > 512) {
                    queue.collection.clear()
                }
            }

            running.store(false)
        }

        val readers = List(2) {
            startThread {
                while (running.load()) {
                    for (element in queue.collection) {
                        assertTrue(element in 0..<ELEMENT_COUNT)
                    }

                    val _ = ELEMENT_COUNT / 2 in queue.collection
                    assertTrue(queue.collection.toString().startsWith("["))
                }
            }
        }

        assertNull(exchange())
        readers.forEach { assertNull(it()) }
    }

    @Test
    fun takeWaitsForAnElement() {
        val queue = newQueue<Int>()
        val taken = AtomicBoolean(false)

        val join = startThread {
            assertEquals(1, queue.take())
            taken.store(true)
        }

        spinFor(BlockedDelay)
        assertFalse(taken.load())
        queue.put(1)

        assertNull(join())
        assertEquals(0, queue.size)
    }

    @Test
    fun timedPollGivesUpOnceItsTimeIsOver() {
        val queue = newQueue<Int>()
        val start = TimeSource.Monotonic.markNow()

        assertNull(queue.pollFor(ShortTimeout))
        assertTrue(start.elapsedNow() >= ShortTimeout)
    }

    @Test
    fun timedPollTakesAnElementAddedInTime() {
        val queue = newQueue<Int>()
        val join = startThread { assertEquals(1, queue.pollFor(LongTimeout)) }

        spinFor(BlockedDelay)
        assertTrue(queue.offer(1))

        assertNull(join())
        assertEquals(0, queue.size)
    }

    @Test
    fun everyWaitingTakerGetsAnElement() {
        val queue = newQueue<Int>()
        val taken = AtomicInt(0)

        val takers = List(WAITER_COUNT) {
            startThread {
                val _ = queue.take()
                val _ = taken.incrementAndFetch()
            }
        }

        spinFor(BlockedDelay)
        assertEquals(0, taken.load())
        repeat(WAITER_COUNT) { queue.put(it) }

        takers.forEach { assertNull(it()) }
        assertEquals(WAITER_COUNT, taken.load())
        assertEquals(0, queue.size)
    }

    @Test
    fun anInterruptEndsTake() {
        assertInterruptEndsTheWait { queue -> val _ = queue.take() }
    }

    @Test
    fun anInterruptEndsATimedPoll() {
        assertInterruptEndsTheWait { queue -> val _ = queue.pollFor(LongTimeout) }
    }

    /**
     * Makes a thread [wait] on an empty queue, then interrupts it. The wait must end with the
     * exception and the flag cleared, and the queue must still hand elements over.
     */
    private fun assertInterruptEndsTheWait(wait: (TestQueue<Int>) -> Unit) {
        val queue = newQueue<Int>()

        val waiter = TestThread {
            assertFailsWith<InterruptedException> { wait(queue) }
            assertFalse(currentThread().isInterrupted())
        }

        spinFor(BlockedDelay)
        waiter.interrupt()

        assertNull(waiter.join())
        assertEquals(0, queue.size)

        val join = startThread { assertEquals(1, queue.take()) }
        queue.put(1)

        assertNull(join())
    }

    @Test
    fun anInterruptedThreadDoesNotTake() {
        val queue = newQueue<Int>()

        queue.put(1)

        val thread = TestThread {
            // Both could take the element without waiting
            currentThread().interrupt()

            assertFailsWith<InterruptedException> { val _ = queue.take() }
            assertFalse(interrupted())

            currentThread().interrupt()

            assertFailsWith<InterruptedException> { val _ = queue.pollFor(LongTimeout) }
            assertFalse(interrupted())

            // Adding never waits, so it never looks at the flag
            currentThread().interrupt()

            queue.put(2)
            assertTrue(queue.offer(3))
            assertTrue(queue.offerFor(4, LongTimeout))
            assertEquals(1, queue.poll())
            assertTrue(interrupted())
        }

        assertNull(thread.join())
        assertEquals(3, queue.size)
    }
}

/**
 * Tests the public [PriorityBlockingQueue] on real threads.
 */
class PriorityBlockingQueueStressTest : PriorityBlockingQueueContractTest() {

    override fun <E : Any> newQueue(): TestQueue<E> = PriorityBlockingQueue<E>().asTestQueue()
}
