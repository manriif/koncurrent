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

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val ELEMENT_COUNT = 1_000

private val ShortTimeout = 20.milliseconds
private val LongTimeout = 30.seconds

/**
 * Tests what one thread sees of a blocking queue that hands its smallest element over first.
 * No test waits, so the suite also runs where there is one thread.
 */
abstract class PriorityQueueContractTest {

    /**
     * Creates a queue in the order of [comparator], or in the natural order of its elements
     * when it is `null`.
     */
    internal abstract fun <E : Any> newQueue(comparator: Comparator<in E>?): TestQueue<E>

    private fun queueOf(vararg elements: Int): TestQueue<Int> {
        val queue = newQueue<Int>(null)

        elements.forEach { queue.put(it) }
        return queue
    }

    private fun <E : Any> TestQueue<E>.pollAll(): List<E> = generateSequence { poll() }.toList()

    @Test
    fun emptyQueueHoldsNothing() {
        val queue = newQueue<Int>(null)

        assertEquals(0, queue.size)
        assertTrue(queue.collection.isEmpty())
        assertEquals(Int.MAX_VALUE, queue.remainingCapacity())
        assertNull(queue.poll())
        assertNull(queue.peek())
        assertFailsWith<NoSuchElementException> { queue.remove() }
        assertFailsWith<NoSuchElementException> { queue.element() }
        assertFalse(queue.collection.iterator().hasNext())
        assertFailsWith<NoSuchElementException> { queue.collection.iterator().next() }
        assertFalse(1 in queue.collection)
        assertEquals("[]", queue.collection.toString())
    }

    @Test
    fun smallestElementLeavesFirst() {
        val queue = newQueue<Int>(null)
        val elements = List(ELEMENT_COUNT) { it }.shuffled(Random(42))

        // The queue grows many times on the way
        elements.forEachIndexed { index, element ->
            assertTrue(queue.offer(element))
            assertEquals(index + 1, queue.size)
            assertEquals(elements.take(index + 1).min(), queue.peek())
        }

        assertEquals(List(ELEMENT_COUNT) { it }, queue.pollAll())
        assertEquals(0, queue.size)
    }

    @Test
    fun everyWayToTakeGivesTheSmallestElement() {
        val queue = queueOf(5, 3, 8, 1, 9, 2)

        assertEquals(1, queue.peek())
        assertEquals(1, queue.element())
        assertEquals(1, queue.poll())
        assertEquals(2, queue.remove())
        assertEquals(3, queue.take())
        assertEquals(5, queue.pollFor(LongTimeout))
        assertEquals(listOf(8, 9), queue.pollAll())
    }

    @Test
    fun comparatorSetsTheOrder() {
        val descending = newQueue<Int>(reverseOrder())

        listOf(3, 9, 1, 7).forEach { descending.put(it) }
        assertEquals(listOf(9, 7, 3, 1), descending.pollAll())

        // These elements have no order of their own
        val names = newQueue<Name>(compareBy { it.text })

        listOf("c", "a", "b").forEach { names.put(Name(it)) }
        assertEquals(listOf(Name("a"), Name("b"), Name("c")), names.pollAll())
    }

    @Test
    fun elementWithoutAnOrderIsRefused() {
        val queue = newQueue<Name>(null)

        assertFailsWith<ClassCastException> { queue.offer(Name("a")) }
        assertFailsWith<ClassCastException> { queue.put(Name("a")) }
        assertFailsWith<ClassCastException> { queue.collection.add(Name("a")) }
        assertEquals(0, queue.size)
    }

    @Test
    fun queueIsNeverFull() {
        val queue = newQueue<Int>(null)

        repeat(ELEMENT_COUNT) { round ->
            when (round % 4) {
                0 -> assertTrue(queue.offer(round))
                1 -> queue.put(round)
                2 -> assertTrue(queue.offerFor(round, Duration.ZERO))
                else -> assertTrue(queue.collection.add(round))
            }
        }

        assertEquals(ELEMENT_COUNT, queue.size)
        assertEquals(Int.MAX_VALUE, queue.remainingCapacity())
    }

    @Test
    fun equalElementsAllLeave() {
        val queue = queueOf(2, 1, 2, 3, 1, 2)

        assertEquals(listOf(1, 1, 2, 2, 2, 3), queue.pollAll())
    }

    @Test
    fun removeAndContainsFindAnElement() {
        val queue = newQueue<Int>(null)
        val elements = List(100) { it }.shuffled(Random(7))

        elements.forEach { queue.put(it) }

        assertTrue(42 in queue.collection)
        assertFalse(100 in queue.collection)
        assertTrue(queue.collection.containsAll(listOf(0, 99)))

        // From anywhere in the heap, the last element and the smallest one included
        val removed = elements.filterIndexed { index, _ -> index % 3 == 0 } + listOf(0)

        removed.distinct().forEach { assertTrue(queue.collection.remove(it)) }
        assertFalse(queue.collection.remove(removed.first()))
        assertFalse(removed.first() in queue.collection)

        assertEquals((elements - removed.toSet()).sorted(), queue.pollAll())
    }

    @Test
    fun drainToGivesTheElementsInOrder() {
        val queue = queueOf(4, 2, 5, 1, 3)
        val target = mutableListOf(0)

        assertEquals(0, queue.drainTo(target, 0))
        assertEquals(2, queue.drainTo(target, 2))
        assertEquals(listOf(0, 1, 2), target)
        assertEquals(3, queue.size)

        assertEquals(3, queue.drainAllTo(target))
        assertEquals(listOf(0, 1, 2, 3, 4, 5), target)
        assertEquals(0, queue.drainAllTo(target))
        assertNull(queue.peek())
    }

    @Test
    fun drainToItselfFails() {
        val queue = queueOf(1, 2)

        assertFailsWith<IllegalArgumentException> { queue.drainAllTo(queue.collection) }
        assertFailsWith<IllegalArgumentException> { queue.drainTo(queue.collection, 1) }
        assertFailsWith<IllegalArgumentException> { queue.collection.addAll(queue.collection) }
        assertEquals(2, queue.size)
    }

    @Test
    fun drainToKeepsTheQueueInOrderWhenTheTargetFails() {
        val queue = queueOf(3, 1, 2)
        val target = FailingCollection<Int>(failAt = 2)

        assertFailsWith<IllegalStateException> { queue.drainAllTo(target) }
        assertEquals(listOf(1), target.toList())
        assertEquals(listOf(2, 3), queue.pollAll())
    }

    @Test
    fun iteratorGoesThroughTheElementsOfItsCreation() {
        val queue = queueOf(3, 1, 2)
        val iterator = queue.collection.iterator()

        queue.put(0)
        assertEquals(0, queue.poll())
        assertEquals(1, queue.poll())

        // In no particular order
        assertEquals(listOf(1, 2, 3), iterator.asSequence().toList().sorted())
        assertFailsWith<NoSuchElementException> { iterator.next() }
    }

    @Test
    fun iteratorRemovesTheElementItReturned() {
        val queue = newQueue<Int>(null)

        List(50) { it }.shuffled(Random(3)).forEach { queue.put(it) }

        val iterator = queue.collection.iterator()

        assertFailsWith<IllegalStateException> { iterator.remove() }

        while (iterator.hasNext()) {
            if (iterator.next() % 2 == 1) {
                iterator.remove()
                assertFailsWith<IllegalStateException> { iterator.remove() }
            }
        }

        assertEquals(List(25) { it * 2 }, queue.pollAll())
    }

    @Test
    fun iteratorRemovesTheVeryElementItReturned() {
        val queue = newQueue<Name>(compareBy { it.text })
        val names = List(3) { Name("a") }

        names.forEach { queue.put(it) }

        val iterator = queue.collection.iterator()
        val first = iterator.next()
        val second = iterator.next()

        // The second one, not the first one, which equals it
        iterator.remove()

        val left = queue.pollAll()

        assertEquals(2, left.size)
        assertTrue(left.any { it === first })
        assertTrue(left.none { it === second })

        // Removing what is no longer there does nothing
        val _ = iterator.next()
        iterator.remove()
        assertEquals(0, queue.size)
    }

    @Test
    fun clearEmptiesTheQueue() {
        val queue = queueOf(3, 1, 2)

        queue.collection.clear()
        queue.collection.clear()

        assertEquals(0, queue.size)
        assertNull(queue.peek())

        queue.put(5)
        queue.put(4)
        assertEquals(listOf(4, 5), queue.pollAll())
    }

    @Test
    fun bulkOperationsAddAndRemove() {
        val queue = queueOf(5)

        assertTrue(queue.collection.addAll(listOf(3, 1, 4, 2)))
        assertFalse(queue.collection.addAll(emptyList()))
        assertEquals(5, queue.size)

        assertTrue(queue.collection.removeAll(listOf(1, 5, 9)))
        assertFalse(queue.collection.removeAll(listOf(9)))
        assertEquals(2, queue.peek())

        assertTrue(queue.collection.retainAll(listOf(3, 4, 9)))
        assertFalse(queue.collection.retainAll(listOf(3, 4)))
        assertEquals(listOf(3, 4), queue.pollAll())
    }

    @Test
    fun timedPollGivesUpOnAnEmptyQueue() {
        val queue = newQueue<Int>(null)

        assertNull(queue.pollFor(Duration.ZERO))
        assertNull(queue.pollFor((-1).seconds))
        assertNull(queue.pollFor(ShortTimeout))

        queue.put(1)

        assertEquals(1, queue.pollFor(Duration.ZERO))
    }
}

/**
 * Tests the public [PriorityBlockingQueue] on one thread.
 */
class PriorityBlockingQueueTest : PriorityQueueContractTest() {

    override fun <E : Any> newQueue(comparator: Comparator<in E>?): TestQueue<E> {
        val queue = if (comparator == null) {
            PriorityBlockingQueue()
        } else {
            PriorityBlockingQueue(11, comparator)
        }

        return queue.asTestQueue()
    }

    @Test
    fun initialCapacityMustBePositive() {
        assertFailsWith<IllegalArgumentException> { PriorityBlockingQueue<Int>(0) }
        assertFailsWith<IllegalArgumentException> { PriorityBlockingQueue<Int>(-1) }
        assertFailsWith<IllegalArgumentException> { PriorityBlockingQueue<Int>(0, reverseOrder()) }
    }

    @Test
    fun queueGrowsPastItsInitialCapacity() {
        val queue = PriorityBlockingQueue<Int>(1)

        repeat(ELEMENT_COUNT) { queue.put(ELEMENT_COUNT - it) }

        assertEquals(ELEMENT_COUNT, queue.size)
        assertEquals(1, queue.poll())
        assertEquals(2, queue.poll())
    }

    @Test
    fun constructorOrdersTheElements() {
        val source = mutableListOf(3, 1, 2)
        val queue = PriorityBlockingQueue(source)

        source += 0

        assertEquals(3, queue.size)
        assertEquals(1, queue.poll())
        assertEquals(2, queue.poll())
        assertEquals(3, queue.poll())
        assertNull(queue.poll())
        assertEquals(0, PriorityBlockingQueue(emptyList<Int>()).size)
    }

    @Test
    fun constructorTakesTheOrderOfAnotherQueue() {
        val source = PriorityBlockingQueue<Int>(11, reverseOrder())

        source.addAll(listOf(1, 3, 2))

        val queue = PriorityBlockingQueue(source)

        assertEquals(3, source.size)
        assertEquals(3, queue.poll())
        assertEquals(2, queue.poll())
        queue.put(5)
        assertEquals(5, queue.poll())
        assertEquals(1, queue.poll())
    }
}
