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
import kotlin.concurrent.atomics.decrementAndFetch
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

private const val PRODUCER_COUNT = 4
private const val CONSUMER_COUNT = 4
private const val ELEMENT_COUNT = 20_000
private const val SMALL_CAPACITY = 4

private val BlockedDelay = 50.milliseconds
private val ShortTimeout = 50.milliseconds
private val LongTimeout = 30.seconds

/**
 * Tests what several threads see of the two ends of a blocking deque that has a capacity.
 */
abstract class BlockingDequeContractTest {

    internal abstract fun <E : Any> newDeque(capacity: Int): TestBlockingDeque<E>

    @Test
    fun fullDequeRefusesAnElementAtBothEnds() {
        val ends = newDeque<String>(2)
        val deque = ends.deque

        ends.putFirst("b")
        ends.putLast("c")

        assertEquals(0, ends.remainingCapacity())
        assertFalse(deque.offerFirst("a"))
        assertFalse(deque.offerLast("d"))
        assertFalse(ends.offerFirstFor("a", Duration.ZERO))
        assertFalse(ends.offerLastFor("d", Duration.ZERO))
        assertFailsWith<IllegalStateException> { deque.addFirst("a") }
        assertFailsWith<IllegalStateException> { deque.addLast("d") }
        assertFailsWith<IllegalStateException> { deque.push("a") }
        assertFailsWith<IllegalStateException> { deque.collection.add("d") }
        assertEquals(listOf("b", "c"), deque.toList())

        assertEquals("c", ends.takeLast())
        assertTrue(ends.offerFirstFor("a", LongTimeout))
        assertEquals("b", ends.pollLastFor(LongTimeout))
        assertEquals("a", ends.takeFirst())
        assertNull(ends.pollFirstFor(Duration.ZERO))
        assertNull(ends.pollLastFor(Duration.ZERO))
        assertEquals(2, ends.remainingCapacity())
    }

    @Test
    fun addAllAppendsEverythingOrFillsTheDeque() {
        val ends = newDeque<String>(3)
        val deque = ends.deque

        deque.addLast("a")

        assertTrue(deque.collection.addAll(listOf("b", "c")))
        assertEquals(listOf("a", "b", "c"), deque.toList())
        assertEquals(listOf("c", "b", "a"), deque.toDescendingList())

        assertEquals("a", deque.pollFirst())
        assertFailsWith<IllegalStateException> { deque.collection.addAll(listOf("d", "e")) }
        assertEquals(listOf("b", "c", "d"), deque.toList())
    }

    @Test
    fun threadsExchangeEveryElementThroughBothEnds() {
        val ends = newDeque<Int>(SMALL_CAPACITY)
        val total = PRODUCER_COUNT * ELEMENT_COUNT
        val toTake = AtomicInt(total)
        val consumed = List(CONSUMER_COUNT) { ArrayList<Int>() }

        runConcurrently(PRODUCER_COUNT + CONSUMER_COUNT) { thread ->
            if (thread < PRODUCER_COUNT) {
                repeat(ELEMENT_COUNT) { sequence ->
                    val element = thread * ELEMENT_COUNT + sequence

                    when (sequence % 4) {
                        0 -> ends.putFirst(element)
                        1 -> ends.putLast(element)
                        2 -> assertTrue(ends.offerFirstFor(element, LongTimeout))
                        else -> assertTrue(ends.offerLastFor(element, LongTimeout))
                    }
                }
            } else {
                val taken = consumed[thread - PRODUCER_COUNT]

                while (toTake.decrementAndFetch() >= 0) {
                    taken += when (taken.size % 4) {
                        0 -> ends.takeFirst()
                        1 -> ends.takeLast()
                        2 -> assertNotNull(ends.pollFirstFor(LongTimeout))
                        else -> assertNotNull(ends.pollLastFor(LongTimeout))
                    }
                }
            }
        }

        assertEquals(List(total) { it }, consumed.flatten().sorted())
        assertEquals(0, ends.deque.size)
        assertEquals(SMALL_CAPACITY, ends.remainingCapacity())
    }

    private fun assertNotNull(element: Int?): Int = checkNotNull(element)

    @Test
    fun takesWaitForAnElementAtBothEnds() {
        val ends = newDeque<String>(SMALL_CAPACITY)

        val waits = listOf(
            { ends.takeFirst() },
            { ends.takeLast() },
            { ends.pollFirstFor(LongTimeout) },
            { ends.pollLastFor(LongTimeout) },
        )

        waits.forEach { wait ->
            val join = startThread { assertEquals("a", wait()) }

            spinFor(BlockedDelay)
            ends.putFirst("a")

            assertNull(join())
            assertEquals(0, ends.deque.size)
        }
    }

    @Test
    fun putsWaitForRoomAtBothEnds() {
        val ends = newDeque<String>(1)

        val waits = listOf(
            { ends.putFirst("b") },
            { ends.putLast("b") },
            { assertTrue(ends.offerFirstFor("b", LongTimeout)) },
            { assertTrue(ends.offerLastFor("b", LongTimeout)) },
        )

        waits.forEach { wait ->
            ends.putLast("a")

            val join = startThread(wait)

            spinFor(BlockedDelay)
            assertEquals(listOf("a"), ends.deque.toList())
            assertEquals("a", ends.takeFirst())

            assertNull(join())
            assertEquals("b", ends.deque.pollLast())
        }
    }

    @Test
    fun timedFunctionsGiveUpAtBothEnds() {
        val ends = newDeque<String>(1)
        var start = TimeSource.Monotonic.markNow()

        assertNull(ends.pollFirstFor(ShortTimeout))
        assertNull(ends.pollLastFor(ShortTimeout))
        assertTrue(start.elapsedNow() >= ShortTimeout * 2)

        ends.putFirst("a")
        start = TimeSource.Monotonic.markNow()

        assertFalse(ends.offerFirstFor("b", ShortTimeout))
        assertFalse(ends.offerLastFor("b", ShortTimeout))
        assertTrue(start.elapsedNow() >= ShortTimeout * 2)
        assertEquals(listOf("a"), ends.deque.toList())
    }

    @Test
    fun anInterruptEndsAWaitAtEitherEnd() {
        val waitsOnAnEmptyDeque = listOf<(TestBlockingDeque<String>) -> Unit>(
            { val _ = it.takeFirst() },
            { val _ = it.takeLast() },
            { val _ = it.pollFirstFor(LongTimeout) },
            { val _ = it.pollLastFor(LongTimeout) },
        )

        val waitsOnAFullDeque = listOf<(TestBlockingDeque<String>) -> Unit>(
            { it.putFirst("z") },
            { it.putLast("z") },
            { val _ = it.offerFirstFor("z", LongTimeout) },
            { val _ = it.offerLastFor("z", LongTimeout) },
        )

        waitsOnAnEmptyDeque.forEach { assertInterruptEndsTheWait(filled = false, it) }
        waitsOnAFullDeque.forEach { assertInterruptEndsTheWait(filled = true, it) }
    }

    /**
     * Makes a thread [wait] on a deque that is empty, or [filled], then interrupts it. The
     * wait must end with the exception, the flag cleared, and the deque as it was.
     */
    private fun assertInterruptEndsTheWait(
        filled: Boolean,
        wait: (TestBlockingDeque<String>) -> Unit,
    ) {
        val ends = newDeque<String>(1)

        if (filled) {
            ends.putFirst("a")
        }

        val waiter = TestThread {
            assertFailsWith<InterruptedException> { wait(ends) }
            assertFalse(currentThread().isInterrupted())
        }

        spinFor(BlockedDelay)
        waiter.interrupt()

        assertNull(waiter.join())
        assertEquals(if (filled) listOf("a") else emptyList(), ends.deque.toList())
    }

    @Test
    fun anInterruptedThreadOnlyFailsWhereItWouldWait() {
        val ends = newDeque<String>(2)

        ends.putFirst("a")

        val thread = TestThread {
            // None of these has to wait. Only the ones with a time limit look at the flag
            // first.
            currentThread().interrupt()

            assertFailsWith<InterruptedException> { val _ = ends.offerFirstFor("b", LongTimeout) }
            assertFalse(interrupted())

            currentThread().interrupt()

            assertFailsWith<InterruptedException> { val _ = ends.pollLastFor(LongTimeout) }
            assertFalse(interrupted())

            currentThread().interrupt()

            ends.putLast("b")
            assertEquals("a", ends.takeFirst())
            ends.putFirst("a")
            assertEquals("b", ends.takeLast())
            assertTrue(interrupted())

            // These have to wait, which the flag makes fail
            currentThread().interrupt()

            assertEquals("a", ends.takeFirst())
            assertFailsWith<InterruptedException> { val _ = ends.takeFirst() }
            assertFalse(interrupted())
        }

        assertNull(thread.join())
        assertEquals(0, ends.deque.size)
    }
}

/**
 * Tests the ends of the public [LinkedBlockingDeque] on real threads.
 */
class LinkedBlockingDequeStressTest : BlockingDequeContractTest() {

    override fun <E : Any> newDeque(capacity: Int): TestBlockingDeque<E> =
        LinkedBlockingDeque<E>(capacity).asTestBlockingDeque()
}
