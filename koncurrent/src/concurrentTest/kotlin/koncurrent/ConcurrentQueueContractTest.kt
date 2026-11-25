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
import kotlin.concurrent.atomics.AtomicIntArray
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetchAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val PRODUCER_COUNT = 4
private const val CONSUMER_COUNT = 4
private const val ELEMENT_COUNT = 20_000
private const val THREAD_COUNT = 8
private const val ROUND_COUNT = 20_000
private const val SHARED_COUNT = 2_000

/**
 * A queue without a capacity under test, whatever its type. [collection] is the queue itself.
 */
internal class TestConcurrentQueue(
    val collection: MutableCollection<Int>,
    val offer: (Int) -> Boolean,
    val poll: () -> Int?,
)

internal fun Queue<Int>.asTestConcurrentQueue(): TestConcurrentQueue = TestConcurrentQueue(
    collection = this,
    offer = { offer(it) },
    poll = { poll() },
)

/**
 * Tests what several threads see of a queue that never waits.
 */
abstract class ConcurrentQueueContractTest {

    internal abstract fun newQueue(): TestConcurrentQueue

    @Test
    fun everyElementIsTakenOnceAndInTheOrderOfItsProducer() {
        val queue = newQueue()
        val taken = AtomicIntArray(PRODUCER_COUNT * ELEMENT_COUNT)
        val left = AtomicInt(PRODUCER_COUNT * ELEMENT_COUNT)

        runConcurrently(PRODUCER_COUNT + CONSUMER_COUNT) { thread ->
            if (thread < PRODUCER_COUNT) {
                repeat(ELEMENT_COUNT) { assertTrue(queue.offer(thread * ELEMENT_COUNT + it)) }
            } else {
                // The last element that this thread took from each producer
                val last = IntArray(PRODUCER_COUNT) { -1 }

                while (left.load() > 0) {
                    val element = queue.poll() ?: continue
                    val producer = element / ELEMENT_COUNT

                    assertTrue(element > last[producer])
                    last[producer] = element

                    val _ = taken.incrementAndFetchAt(element)
                    val _ = left.addAndFetch(-1)
                }
            }
        }

        repeat(PRODUCER_COUNT * ELEMENT_COUNT) { assertEquals(1, taken.loadAt(it)) }
        assertEquals(0, queue.collection.size)
    }

    @Test
    fun threadsThatAddAndTakeLoseNothing() {
        val queue = newQueue()
        val taken = AtomicInt(0)

        runConcurrently(THREAD_COUNT) { thread ->
            repeat(ROUND_COUNT) {
                assertTrue(queue.offer(thread))

                if (queue.poll() != null) {
                    val _ = taken.incrementAndFetch()
                }
            }
        }

        // A thread may find the queue empty, when another one took its element first
        assertEquals(THREAD_COUNT * ROUND_COUNT, taken.load() + queue.collection.size)
    }

    @Test
    fun anElementIsRemovedByOneThreadOnly() {
        val queue = newQueue()
        val removed = AtomicInt(0)

        repeat(SHARED_COUNT) { assertTrue(queue.offer(it)) }

        runConcurrently(THREAD_COUNT) {
            repeat(SHARED_COUNT) { element ->
                if (queue.collection.remove(element)) {
                    val _ = removed.incrementAndFetch()
                }
            }
        }

        assertEquals(SHARED_COUNT, removed.load())
        assertEquals(0, queue.collection.size)
    }

    @Test
    fun iterationGoesOnWhileThreadsAddAndTake() {
        val queue = newQueue()

        repeat(SHARED_COUNT) { assertTrue(queue.offer(it)) }

        runConcurrently(THREAD_COUNT) { thread ->
            if (thread % 2 == 0) {
                repeat(SHARED_COUNT) {
                    assertTrue(queue.offer(SHARED_COUNT + it))
                    val _ = queue.poll()
                }
            } else {
                repeat(20) {
                    // The elements arrive in order, so an iterator sees them grow
                    var previous = -1

                    for (element in queue.collection) {
                        assertTrue(element >= 0)

                        if (element < SHARED_COUNT) {
                            assertTrue(element > previous)
                            previous = element
                        }
                    }
                }
            }
        }

        assertEquals(SHARED_COUNT, queue.collection.size)
    }
}

/**
 * Tests the public [ConcurrentLinkedQueue] on real threads.
 */
class ConcurrentLinkedQueueStressTest : ConcurrentQueueContractTest() {

    override fun newQueue(): TestConcurrentQueue =
        ConcurrentLinkedQueue<Int>().asTestConcurrentQueue()
}

/**
 * Tests the public [ConcurrentLinkedDeque] as a queue, on real threads.
 */
class ConcurrentLinkedDequeAsQueueStressTest : ConcurrentQueueContractTest() {

    override fun newQueue(): TestConcurrentQueue =
        ConcurrentLinkedDeque<Int>().asTestConcurrentQueue()
}

/**
 * Tests both ends of the public [ConcurrentLinkedDeque] on real threads.
 */
class ConcurrentLinkedDequeStressTest {

    @Test
    fun bothEndsServeThreadsAtOnce() {
        val deque = ConcurrentLinkedDeque<Int>()
        val taken = AtomicInt(0)

        runConcurrently(THREAD_COUNT) { thread ->
            repeat(ROUND_COUNT) {
                // Half of the threads work from the head to the tail, the others the other way
                val element = if (thread % 2 == 0) {
                    deque.addFirst(thread)
                    deque.pollLast()
                } else {
                    deque.addLast(thread)
                    deque.pollFirst()
                }

                if (element != null) {
                    val _ = taken.incrementAndFetch()
                }
            }
        }

        assertEquals(THREAD_COUNT * ROUND_COUNT, taken.load() + deque.size)
    }
}
