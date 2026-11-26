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
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private const val PRODUCER_COUNT = 4
private const val CONSUMER_COUNT = 4
private const val ELEMENT_COUNT = 20_000
private const val GIVE_UP_ELEMENT_COUNT = 2_000
private const val WAITER_COUNT = 16
private const val SMALL_CAPACITY = 4
private const val LARGE_CAPACITY = 1_024

private val BlockedDelay = 50.milliseconds
private val ShortTimeout = 50.milliseconds
private val LongTimeout = 30.seconds

// The longest that a thread waits before it tries again, where threads give up all the time
private val GiveUpTimeout = 60.microseconds

/**
 * Tests what several threads see of a blocking queue that has a capacity and that keeps the
 * order of arrival.
 */
abstract class BlockingQueueContractTest {

    internal abstract fun <E : Any> newQueue(capacity: Int): TestQueue<E>

    /**
     * Whether `put` and `take` look at the interrupt of their thread before anything else.
     * Those of a `LinkedBlockingDeque` only look at it once they have to wait.
     */
    internal open val untimedWaitsLookAtTheInterruptFirst: Boolean
        get() = true

    ///////////////////////////////////////////////////////////////////////////
    // Exchanges
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun producersAndConsumersExchangeEveryElement() {
        // Half of the threads wait, the others try again, so that a thread that does not wait
        // has to wake up one that does
        assertEveryElementIsExchanged(
            elementCount = ELEMENT_COUNT,
            produce = { queue, thread, element ->
                if (thread % 2 == 0) {
                    queue.put(element)
                } else {
                    while (!queue.offer(element)) {
                        // Tries again until there is room
                    }
                }
            },
            consume = { queue, thread ->
                if (thread % 2 == 0) queue.take() else pollUntilThere(queue)
            },
        )
    }

    @Test
    fun threadsThatGiveUpExchangeEveryElement() {
        assertEveryElementIsExchanged(
            elementCount = GIVE_UP_ELEMENT_COUNT,
            produce = { queue, thread, element ->
                var round = thread

                while (!queue.offerFor(element, GiveUpTimeout * (round++ % 4) / 3)) {
                    // Gave up, tries again
                }
            },
            consume = { queue, thread ->
                var round = thread
                var element = queue.pollFor(GiveUpTimeout * (round++ % 4) / 3)

                while (element == null) {
                    element = queue.pollFor(GiveUpTimeout * (round++ % 4) / 3)
                }

                element
            },
        )
    }

    /**
     * Has producers add numbered elements to a small queue while consumers take them. Every
     * element must come out once, and those of one producer in their order for each consumer.
     */
    private fun assertEveryElementIsExchanged(
        elementCount: Int,
        produce: (queue: TestQueue<Int>, thread: Int, element: Int) -> Unit,
        consume: (queue: TestQueue<Int>, thread: Int) -> Int,
    ) {
        val queue = newQueue<Int>(SMALL_CAPACITY)
        val total = PRODUCER_COUNT * elementCount
        val toTake = AtomicInt(total)
        val overflowed = AtomicBoolean(false)
        val consumed = List(CONSUMER_COUNT) { ArrayList<Int>() }

        runConcurrently(PRODUCER_COUNT + CONSUMER_COUNT) { thread ->
            if (thread < PRODUCER_COUNT) {
                repeat(elementCount) { sequence ->
                    produce(queue, thread, thread * elementCount + sequence)
                }
            } else {
                val taken = consumed[thread - PRODUCER_COUNT]
                val lastSequences = IntArray(PRODUCER_COUNT) { -1 }

                while (toTake.decrementAndFetch() >= 0) {
                    val element = consume(queue, thread)
                    val producer = element / elementCount

                    // A queue hands the elements of one producer over in their order
                    assertTrue(element % elementCount > lastSequences[producer])
                    lastSequences[producer] = element % elementCount
                    taken += element

                    if (queue.size > SMALL_CAPACITY || queue.remainingCapacity() < 0) {
                        overflowed.store(true)
                    }
                }
            }
        }

        assertFalse(overflowed.load())
        assertEquals(List(total) { it }, consumed.flatten().sorted())
        assertEquals(0, queue.size)
        assertEquals(SMALL_CAPACITY, queue.remainingCapacity())
        assertNull(queue.poll())
    }

    private fun pollUntilThere(queue: TestQueue<Int>): Int {
        while (true) {
            return queue.poll() ?: continue
        }
    }

    @Test
    fun drainToTakesEveryElementOnce() {
        val queue = newQueue<Int>(SMALL_CAPACITY)
        val total = PRODUCER_COUNT * ELEMENT_COUNT
        val drained = ArrayList<Int>()

        val producers = List(PRODUCER_COUNT) { thread ->
            startThread {
                repeat(ELEMENT_COUNT) { queue.put(thread * ELEMENT_COUNT + it) }
            }
        }

        while (drained.size < total) {
            val count = queue.drainTo(drained, 3)

            assertTrue(count in 0..3)
        }

        producers.forEach { assertNull(it()) }
        assertEquals(List(total) { it }, drained.sorted())
        assertEquals(0, queue.size)
    }

    @Test
    fun removeTakesEachElementOnce() {
        val queue = newQueue<Int>(LARGE_CAPACITY)
        val removed = AtomicInt(0)

        repeat(LARGE_CAPACITY) { queue.put(it) }

        // Every thread goes after every element, from its own starting point
        runConcurrently(PRODUCER_COUNT + CONSUMER_COUNT) { thread ->
            repeat(LARGE_CAPACITY) { index ->
                val element = (index + thread * 131) % LARGE_CAPACITY

                if (queue.collection.remove(element)) {
                    val _ = removed.incrementAndFetch()
                }
            }
        }

        assertEquals(LARGE_CAPACITY, removed.load())
        assertEquals(0, queue.size)
        assertEquals(LARGE_CAPACITY, queue.remainingCapacity())
        assertTrue(queue.offer(1))
        assertEquals(listOf(1), queue.toList())
    }

    @Test
    fun iterationAndSearchGoOnDuringAnExchange() {
        val queue = newQueue<Int>(LARGE_CAPACITY)
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

                if (queue.size > LARGE_CAPACITY / 2) {
                    queue.collection.clear()
                }
            }

            running.store(false)
        }

        val readers = List(2) {
            startThread {
                while (running.load()) {
                    val iterator = queue.collection.iterator()
                    var seen = 0

                    while (iterator.hasNext() && seen++ < LARGE_CAPACITY) {
                        assertTrue(iterator.next() in 0..<ELEMENT_COUNT)
                    }

                    val _ = ELEMENT_COUNT / 2 in queue.collection
                    assertTrue(queue.collection.toString().startsWith("["))
                }
            }
        }

        assertNull(exchange())
        readers.forEach { assertNull(it()) }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Waiting
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun takeWaitsForAnElement() {
        val queue = newQueue<String>(SMALL_CAPACITY)
        val taken = AtomicBoolean(false)

        val join = startThread {
            assertEquals("a", queue.take())
            taken.store(true)
        }

        spinFor(BlockedDelay)
        assertFalse(taken.load())
        queue.put("a")

        assertNull(join())
        assertEquals(0, queue.size)
    }

    @Test
    fun putWaitsForRoom() {
        val queue = newQueue<String>(1)
        val added = AtomicBoolean(false)

        queue.put("a")

        val join = startThread {
            queue.put("b")
            added.store(true)
        }

        spinFor(BlockedDelay)
        assertFalse(added.load())
        assertEquals(listOf("a"), queue.toList())
        assertEquals("a", queue.poll())

        assertNull(join())
        assertEquals(listOf("b"), queue.toList())
    }

    @Test
    fun timedPollGivesUpOnceItsTimeIsOver() {
        val queue = newQueue<String>(SMALL_CAPACITY)
        val start = TimeSource.Monotonic.markNow()

        assertNull(queue.pollFor(ShortTimeout))
        assertTrue(start.elapsedNow() >= ShortTimeout)
    }

    @Test
    fun timedPollTakesAnElementAddedInTime() {
        val queue = newQueue<String>(SMALL_CAPACITY)
        val join = startThread { assertEquals("a", queue.pollFor(LongTimeout)) }

        spinFor(BlockedDelay)
        assertTrue(queue.offer("a"))

        assertNull(join())
        assertEquals(0, queue.size)
    }

    @Test
    fun timedOfferGivesUpOnceItsTimeIsOver() {
        val queue = newQueue<String>(1)

        queue.put("a")

        val start = TimeSource.Monotonic.markNow()

        assertFalse(queue.offerFor("b", ShortTimeout))
        assertTrue(start.elapsedNow() >= ShortTimeout)
        assertEquals(listOf("a"), queue.toList())
    }

    @Test
    fun timedOfferAddsOnceThereIsRoom() {
        val queue = newQueue<String>(1)

        queue.put("a")

        val join = startThread { assertTrue(queue.offerFor("b", LongTimeout)) }

        spinFor(BlockedDelay)
        assertEquals("a", queue.take())

        assertNull(join())
        assertEquals(listOf("b"), queue.toList())
    }

    @Test
    fun everyWaitingTakerGetsAnElement() {
        val queue = newQueue<Int>(WAITER_COUNT)
        val taken = AtomicInt(0)

        val takers = List(WAITER_COUNT) {
            startThread {
                val _ = queue.take()
                val _ = taken.incrementAndFetch()
            }
        }

        spinFor(BlockedDelay)
        assertEquals(0, taken.load())

        // One thread adds them all. The takers have to wake each other up.
        repeat(WAITER_COUNT) { assertTrue(queue.offer(it)) }

        takers.forEach { assertNull(it()) }
        assertEquals(WAITER_COUNT, taken.load())
        assertEquals(0, queue.size)
    }

    @Test
    fun everyWaitingPutterGetsRoom() {
        val queue = newQueue<Int>(1)
        val taken = ArrayList<Int>()

        queue.put(-1)

        val putters = List(WAITER_COUNT) { thread ->
            startThread { queue.put(thread) }
        }

        spinFor(BlockedDelay)
        assertEquals(listOf(-1), queue.toList())

        while (taken.size <= WAITER_COUNT) {
            queue.poll()?.let(taken::add)
        }

        putters.forEach { assertNull(it()) }
        assertEquals(listOf(-1) + List(WAITER_COUNT) { it }, taken.sorted())
        assertEquals(0, queue.size)
    }

    @Test
    fun drainToMakesRoomForTheWaitingPutters() {
        assertRoomFreesThePutters { queue -> assertEquals(2, queue.drainAllTo(ArrayList())) }
    }

    @Test
    fun clearMakesRoomForTheWaitingPutters() {
        assertRoomFreesThePutters { queue -> queue.collection.clear() }
    }

    @Test
    fun removingElementsMakesRoomForTheWaitingPutters() {
        assertRoomFreesThePutters { queue ->
            assertTrue(queue.collection.remove(-1))
            assertTrue(queue.collection.remove(-2))
        }
    }

    @Test
    fun anIteratorThatRemovesMakesRoomForTheWaitingPutters() {
        assertRoomFreesThePutters { queue ->
            val iterator = queue.collection.iterator()

            repeat(2) {
                assertTrue(iterator.next() < 0)
                iterator.remove()
            }
        }
    }

    /**
     * Fills a queue of two elements, makes two threads wait to add, then empties the queue
     * with [makeRoom]. Both threads must get in.
     */
    private fun assertRoomFreesThePutters(makeRoom: (TestQueue<Int>) -> Unit) {
        val queue = newQueue<Int>(2)

        queue.put(-1)
        queue.put(-2)

        val putters = List(2) { thread ->
            startThread { queue.put(thread) }
        }

        spinFor(BlockedDelay)
        assertEquals(listOf(-1, -2), queue.toList())
        makeRoom(queue)

        putters.forEach { assertNull(it()) }
        assertEquals(listOf(0, 1), queue.toList().sorted())
        assertEquals(0, queue.remainingCapacity())
    }

    ///////////////////////////////////////////////////////////////////////////
    // Interruption
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun anInterruptEndsTake() {
        assertInterruptEndsTheWait(filled = false) { queue -> val _ = queue.take() }
    }

    @Test
    fun anInterruptEndsATimedPoll() {
        assertInterruptEndsTheWait(filled = false) { queue -> val _ = queue.pollFor(LongTimeout) }
    }

    @Test
    fun anInterruptEndsPut() {
        assertInterruptEndsTheWait(filled = true) { queue -> queue.put("z") }
    }

    @Test
    fun anInterruptEndsATimedOffer() {
        assertInterruptEndsTheWait(filled = true) { queue ->
            val _ = queue.offerFor("z", LongTimeout)
        }
    }

    /**
     * Makes a thread [wait] on a queue that is empty, or [filled], then interrupts it. The
     * wait must end with the exception, the flag cleared, and the queue as it was.
     */
    private fun assertInterruptEndsTheWait(filled: Boolean, wait: (TestQueue<String>) -> Unit) {
        val queue = newQueue<String>(1)

        if (filled) {
            queue.put("a")
        }

        val waiter = TestThread {
            assertFailsWith<InterruptedException> { wait(queue) }
            assertFalse(currentThread().isInterrupted())
        }

        spinFor(BlockedDelay)
        waiter.interrupt()

        assertNull(waiter.join())
        assertEquals(if (filled) listOf("a") else emptyList(), queue.toList())

        // The queue still hands elements over
        if (filled) {
            assertEquals("a", queue.poll())
        }

        val join = startThread { assertEquals("b", queue.take()) }
        queue.put("b")

        assertNull(join())
    }

    @Test
    fun anInterruptedThreadDoesNotWait() {
        val queue = newQueue<String>(1)

        val thread = TestThread {
            val waitsOnAnEmptyQueue = listOf(
                { val _ = queue.take() },
                { val _ = queue.pollFor(LongTimeout) },
            )

            val waitsOnAFullQueue = listOf(
                { queue.put("b") },
                { val _ = queue.offerFor("b", LongTimeout) },
            )

            waitsOnAnEmptyQueue.forEach { assertInterruptFails(it) }
            queue.put("a")
            waitsOnAFullQueue.forEach { assertInterruptFails(it) }

            // What never waits does not look at the flag
            currentThread().interrupt()

            assertFalse(queue.offer("b"))
            assertEquals("a", queue.peek())
            assertEquals("a", queue.poll())
            assertTrue(interrupted())
        }

        assertNull(thread.join())
        assertEquals(0, queue.size)
    }

    @Test
    fun anInterruptedThreadGivesUpAWaitThatWouldNotLast() {
        val queue = newQueue<String>(2)

        queue.put("a")

        val thread = TestThread {
            // Each of these could go through without waiting. The ones with a time limit
            // always look at the flag first.
            assertInterruptFails { val _ = queue.offerFor("b", LongTimeout) }
            assertInterruptFails { val _ = queue.pollFor(LongTimeout) }

            if (untimedWaitsLookAtTheInterruptFirst) {
                assertInterruptFails { queue.put("b") }
                assertInterruptFails { val _ = queue.take() }
            } else {
                currentThread().interrupt()

                queue.put("b")
                assertEquals("a", queue.take())
                assertTrue(interrupted())
                assertEquals("b", queue.take())
                queue.put("a")
            }
        }

        assertNull(thread.join())
        assertEquals(listOf("a"), queue.toList())
    }

    // Interrupts the calling thread, which must make the wait fail and clear the flag
    private fun assertInterruptFails(wait: () -> Unit) {
        currentThread().interrupt()

        assertFailsWith<InterruptedException> { wait() }
        assertFalse(interrupted())
    }
}

/**
 * Tests the public [LinkedBlockingQueue] on real threads.
 */
class LinkedBlockingQueueStressTest : BlockingQueueContractTest() {

    override fun <E : Any> newQueue(capacity: Int): TestQueue<E> =
        LinkedBlockingQueue<E>(capacity).asTestQueue()
}

/**
 * Tests the public [LinkedBlockingDeque] as a blocking queue, on real threads.
 */
class LinkedBlockingDequeAsQueueStressTest : BlockingQueueContractTest() {

    override val untimedWaitsLookAtTheInterruptFirst: Boolean
        get() = false

    override fun <E : Any> newQueue(capacity: Int): TestQueue<E> =
        LinkedBlockingDeque<E>(capacity).asTestQueue()
}

/**
 * Tests the public [ArrayBlockingQueue] on real threads.
 */
class ArrayBlockingQueueStressTest : BlockingQueueContractTest() {

    override fun <E : Any> newQueue(capacity: Int): TestQueue<E> =
        ArrayBlockingQueue<E>(capacity).asTestQueue()
}

/**
 * Tests the public [ArrayBlockingQueue] on real threads when it is fair.
 */
class FairArrayBlockingQueueStressTest : BlockingQueueContractTest() {

    override fun <E : Any> newQueue(capacity: Int): TestQueue<E> =
        ArrayBlockingQueue<E>(capacity, true).asTestQueue()
}
