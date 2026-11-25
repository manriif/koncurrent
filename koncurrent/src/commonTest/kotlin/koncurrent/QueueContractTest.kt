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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val CAPACITY = 16

private val ShortTimeout = 20.milliseconds
private val LongTimeout = 30.seconds

/**
 * What an iterator shows of the changes that its queue went through after its creation.
 */
internal enum class IteratorView {

    /**
     * The iterator follows the queue.
     */
    Live,

    /**
     * The iterator goes through the elements as they were at its creation.
     */
    Snapshot,

    /**
     * One or the other, depending on the platform.
     */
    Unspecified,
}

/**
 * A blocking queue under test, whatever its type. [collection] is the queue itself.
 */
internal class TestQueue<E : Any>(
    val collection: MutableCollection<E>,
    val offer: (E) -> Boolean,
    val offerFor: (E, Duration) -> Boolean,
    val put: (E) -> Unit,
    val poll: () -> E?,
    val pollFor: (Duration) -> E?,
    val take: () -> E,
    val peek: () -> E?,
    val remove: () -> E,
    val element: () -> E,
    val remainingCapacity: () -> Int,
    val drainAllTo: (MutableCollection<in E>) -> Int,
    val drainTo: (MutableCollection<in E>, Int) -> Int,
) {

    val size: Int
        get() = collection.size

    fun toList(): List<E> = collection.iterator().asSequence().toList()
}

internal fun <E : Any> BlockingQueue<E>.asTestQueue(): TestQueue<E> = TestQueue(
    collection = this,
    offer = { offer(it) },
    offerFor = { element, timeout ->
        offer(element, timeout)
    },
    put = { put(it) },
    poll = { poll() },
    pollFor = { poll(it) },
    take = { take() },
    peek = { peek() },
    remove = { remove() },
    element = { element() },
    remainingCapacity = { remainingCapacity() },
    drainAllTo = { drainTo(it) },
    drainTo = { collection, maxElements ->
        drainTo(collection, maxElements)
    },
)

/**
 * Tests what one thread sees of a blocking queue that has a capacity and that keeps the order
 * of arrival. No test waits, so the suite also runs where there is one thread.
 */
abstract class QueueContractTest {

    internal abstract fun <E : Any> newQueue(capacity: Int): TestQueue<E>

    internal open val iteratorView: IteratorView
        get() = Live

    private fun queueOf(vararg elements: String): TestQueue<String> {
        val queue = newQueue<String>(CAPACITY)

        elements.forEach { assertTrue(queue.offer(it)) }
        return queue
    }

    @Test
    fun emptyQueueHoldsNothing() {
        val queue = newQueue<String>(CAPACITY)

        assertEquals(0, queue.size)
        assertTrue(queue.collection.isEmpty())
        assertEquals(CAPACITY, queue.remainingCapacity())
        assertNull(queue.poll())
        assertNull(queue.peek())
        assertFailsWith<NoSuchElementException> { queue.remove() }
        assertFailsWith<NoSuchElementException> { queue.element() }
        assertFalse(queue.collection.iterator().hasNext())
        assertFailsWith<NoSuchElementException> { queue.collection.iterator().next() }
        assertFalse("a" in queue.collection)
        assertEquals("[]", queue.collection.toString())
    }

    @Test
    fun capacityMustBePositive() {
        assertFailsWith<IllegalArgumentException> { newQueue<String>(0) }
        assertFailsWith<IllegalArgumentException> { newQueue<String>(-1) }
    }

    @Test
    fun elementsLeaveInTheOrderOfArrival() {
        val queue = newQueue<String>(CAPACITY)

        assertTrue(queue.offer("a"))
        assertTrue(queue.collection.add("b"))
        queue.put("c")
        assertTrue(queue.offerFor("d", LongTimeout))

        assertEquals(4, queue.size)
        assertEquals(CAPACITY - 4, queue.remainingCapacity())
        assertEquals("a", queue.peek())
        assertEquals("a", queue.element())
        assertEquals("a", queue.poll())
        assertEquals("b", queue.remove())
        assertEquals("c", queue.take())
        assertEquals("d", queue.pollFor(LongTimeout))
        assertNull(queue.poll())
        assertEquals(CAPACITY, queue.remainingCapacity())
    }

    @Test
    fun fullQueueRefusesAnElement() {
        val queue = newQueue<String>(2)

        queue.put("a")
        assertTrue(queue.offer("b"))

        assertEquals(0, queue.remainingCapacity())
        assertFalse(queue.offer("c"))
        assertFalse(queue.offerFor("c", Duration.ZERO))
        assertFalse(queue.offerFor("c", ShortTimeout))
        assertFailsWith<IllegalStateException> { queue.collection.add("c") }
        assertEquals(listOf("a", "b"), queue.toList())

        assertEquals("a", queue.poll())
        assertTrue(queue.offer("c"))
        assertEquals(listOf("b", "c"), queue.toList())
    }

    @Test
    fun timedPollGivesUpOnAnEmptyQueue() {
        val queue = newQueue<String>(CAPACITY)

        assertNull(queue.pollFor(Duration.ZERO))
        assertNull(queue.pollFor((-1).seconds))
        assertNull(queue.pollFor(ShortTimeout))

        queue.put("a")

        assertEquals("a", queue.pollFor(Duration.ZERO))
    }

    @Test
    fun queueRefillsAfterItWasEmptied() {
        val queue = newQueue<Int>(3)

        // Goes around the capacity many times, which an array has to wrap for
        repeat(50) { round ->
            assertTrue(queue.offer(round))
            assertTrue(queue.offer(round + 1))
            assertEquals(round, queue.poll())
            assertEquals(round + 1, queue.poll())
            assertNull(queue.poll())
        }

        assertEquals(3, queue.remainingCapacity())
    }

    @Test
    fun containsAndRemoveFindAnElement() {
        val queue = queueOf("a", "b", "c", "b")

        assertTrue("b" in queue.collection)
        assertFalse("x" in queue.collection)
        assertTrue(queue.collection.containsAll(listOf("a", "c")))
        assertFalse(queue.collection.containsAll(listOf("a", "x")))

        // The first one of the two
        assertTrue(queue.collection.remove("b"))
        assertEquals(listOf("a", "c", "b"), queue.toList())
        assertFalse(queue.collection.remove("x"))

        assertTrue(queue.collection.remove("a"))
        assertTrue(queue.collection.remove("b"))
        assertEquals(listOf("c"), queue.toList())
        assertEquals(CAPACITY - 1, queue.remainingCapacity())
    }

    @Test
    fun elementsAreAddedAfterTheLastOneWasRemoved() {
        val queue = queueOf("a", "b", "c")

        assertTrue(queue.collection.remove("c"))
        assertTrue(queue.offer("d"))
        assertEquals(listOf("a", "b", "d"), queue.toList())

        assertTrue(queue.collection.remove("a"))
        assertTrue(queue.collection.remove("b"))
        assertTrue(queue.collection.remove("d"))
        assertTrue(queue.offer("e"))
        assertEquals("e", queue.peek())
        assertEquals(listOf("e"), queue.toList())
    }

    @Test
    fun clearEmptiesTheQueue() {
        val queue = newQueue<String>(2)

        queue.put("a")
        queue.put("b")
        queue.collection.clear()

        assertEquals(0, queue.size)
        assertNull(queue.peek())
        assertEquals(2, queue.remainingCapacity())
        assertTrue(queue.offer("c"))
        assertTrue(queue.offer("d"))
        assertEquals(listOf("c", "d"), queue.toList())

        queue.collection.clear()
        queue.collection.clear()

        assertEquals(emptyList(), queue.toList())
    }

    @Test
    fun drainToMovesTheElements() {
        val queue = queueOf("a", "b", "c", "d", "e")
        val target = mutableListOf("x")

        assertEquals(0, queue.drainTo(target, 0))
        assertEquals(0, queue.drainTo(target, -1))
        assertEquals(2, queue.drainTo(target, 2))
        assertEquals(listOf("x", "a", "b"), target)
        assertEquals(listOf("c", "d", "e"), queue.toList())

        assertEquals(3, queue.drainAllTo(target))
        assertEquals(listOf("x", "a", "b", "c", "d", "e"), target)
        assertEquals(0, queue.size)
        assertEquals(CAPACITY, queue.remainingCapacity())
        assertEquals(0, queue.drainAllTo(target))

        assertTrue(queue.offer("f"))
        assertEquals(1, queue.drainTo(target, 10))
        assertEquals("f", target.last())
    }

    @Test
    fun drainToItselfFails() {
        val queue = queueOf("a", "b")

        assertFailsWith<IllegalArgumentException> { queue.drainAllTo(queue.collection) }
        assertFailsWith<IllegalArgumentException> { queue.drainTo(queue.collection, 1) }
        assertEquals(listOf("a", "b"), queue.toList())
    }

    @Test
    fun drainToKeepsTheQueueInOrderWhenTheTargetFails() {
        val queue = queueOf("a", "b", "c")
        val target = FailingCollection<String>(failAt = 2)

        assertFailsWith<IllegalStateException> { queue.drainAllTo(target) }

        // The queue must still work, with what it kept
        val left = queue.toList()

        assertEquals(left.size, queue.size)
        assertTrue(left.size <= 2)
        assertTrue(queue.offer("d"))
        assertEquals(left + "d", queue.toList())
    }

    @Test
    fun bulkOperationsAddAndRemove() {
        val queue = queueOf("a")

        assertTrue(queue.collection.addAll(listOf("b", "c", "d")))
        assertFalse(queue.collection.addAll(emptyList()))
        assertEquals(listOf("a", "b", "c", "d"), queue.toList())

        assertTrue(queue.collection.removeAll(listOf("b", "x")))
        assertFalse(queue.collection.removeAll(listOf("x")))
        assertEquals(listOf("a", "c", "d"), queue.toList())

        assertTrue(queue.collection.retainAll(listOf("c", "d", "x")))
        assertFalse(queue.collection.retainAll(listOf("c", "d")))
        assertEquals(listOf("c", "d"), queue.toList())
        assertEquals("[c, d]", queue.collection.toString())
    }

    @Test
    fun addingAQueueToItselfFails() {
        val queue = queueOf("a")

        assertFailsWith<IllegalArgumentException> { queue.collection.addAll(queue.collection) }
    }

    @Test
    fun addAllFailsOnceTheQueueIsFull() {
        val queue = newQueue<String>(2)

        assertFailsWith<IllegalStateException> { queue.collection.addAll(listOf("a", "b", "c")) }
        assertEquals(listOf("a", "b"), queue.toList())
    }

    @Test
    fun iteratorRemovesTheElementItReturned() {
        val queue = queueOf("a", "b", "c", "d", "e")
        val iterator = queue.collection.iterator()

        assertFailsWith<IllegalStateException> { iterator.remove() }

        // The head, one in the middle, then the tail
        assertEquals("a", iterator.next())
        iterator.remove()
        assertFailsWith<IllegalStateException> { iterator.remove() }
        assertEquals("b", iterator.next())
        assertEquals("c", iterator.next())
        iterator.remove()
        assertEquals("d", iterator.next())
        assertEquals("e", iterator.next())
        iterator.remove()

        assertFalse(iterator.hasNext())
        assertFailsWith<NoSuchElementException> { iterator.next() }
        assertEquals(listOf("b", "d"), queue.toList())
        assertEquals(2, queue.size)

        assertTrue(queue.offer("f"))
        assertEquals(listOf("b", "d", "f"), queue.toList())
    }

    @Test
    fun iteratorGoesOnWhenTheQueueChanges() {
        val queue = queueOf("a", "b", "c")
        val iterator = queue.collection.iterator()

        // The iterator already holds the element that it returns next
        assertEquals("a", queue.poll())
        assertEquals("b", queue.poll())
        assertTrue(queue.offer("d"))

        assertTrue(iterator.hasNext())
        assertEquals("a", iterator.next())

        val rest = iterator.asSequence().toList()

        when (iteratorView) {
            Live -> assertEquals(listOf("c", "d"), rest)
            Snapshot -> assertEquals(listOf("b", "c"), rest)
            Unspecified -> assertTrue(rest == listOf("c", "d") || rest == listOf("b", "c"))
        }
    }

    @Test
    fun iteratorEndsWhenTheQueueWasEmptied() {
        val queue = queueOf("a", "b", "c")
        val iterator = queue.collection.iterator()

        queue.collection.clear()

        assertEquals("a", iterator.next())

        if (iteratorView == Live) {
            assertFalse(iterator.hasNext())
        }

        // Removing what is no longer there does nothing
        iterator.remove()
        assertEquals(0, queue.size)
    }

    @Test
    fun iteratorRemovesTheVeryElementItReturned() {
        val queue = newQueue<Name>(CAPACITY)
        val first = Name("a")
        val second = Name("a")

        queue.put(first)
        queue.put(Name("b"))
        queue.put(second)

        val iterator = queue.collection.iterator()

        repeat(3) {
            val _ = iterator.next()
        }

        // The last one, not the first one, which equals it
        iterator.remove()

        assertEquals(2, queue.size)
        assertSame(first, queue.poll())
        assertEquals(Name("b"), queue.poll())
    }

    @Test
    fun iteratorRemovesAnElementThatIsStillThere() {
        val queue = queueOf("a", "b", "c")
        val iterator = queue.collection.iterator()

        assertEquals("a", iterator.next())
        assertEquals("b", iterator.next())
        assertEquals("a", queue.poll())
        iterator.remove()

        assertEquals(listOf("c"), queue.toList())
        assertEquals("c", iterator.next())
    }
}

/**
 * An element that equals the other ones with its [text], while being another object.
 */
internal data class Name(val text: String)

/**
 * A collection that fails on its addition number [failAt], to test what a queue leaves behind.
 */
internal class FailingCollection<E>(private val failAt: Int) : AbstractMutableCollection<E>() {

    private val elements = ArrayList<E>()

    override val size: Int
        get() = elements.size

    override fun iterator(): MutableIterator<E> = elements.iterator()

    override fun add(element: E): Boolean {
        check(elements.size + 1 < failAt) { "Addition $failAt fails" }
        return elements.add(element)
    }
}

/**
 * Tests the public [LinkedBlockingQueue] on one thread.
 */
class LinkedBlockingQueueTest : QueueContractTest() {

    override fun <E : Any> newQueue(capacity: Int): TestQueue<E> =
        LinkedBlockingQueue<E>(capacity).asTestQueue()

    @Test
    fun queueWithoutCapacityIsNeverFull() {
        val queue = LinkedBlockingQueue<Int>()

        repeat(10_000) { assertTrue(queue.offer(it)) }

        assertEquals(Int.MAX_VALUE - 10_000, queue.remainingCapacity())
        assertEquals(10_000, queue.size)
        assertEquals(0, queue.peek())
    }

    @Test
    fun constructorCopiesTheElements() {
        val source = mutableListOf("a", "b")
        val queue = LinkedBlockingQueue(source)

        source += "c"

        assertEquals(listOf("a", "b"), queue.toList())
        assertEquals(Int.MAX_VALUE - 2, queue.remainingCapacity())
        assertEquals("a", queue.poll())
    }
}

/**
 * Tests the public [ArrayBlockingQueue] on one thread.
 */
class ArrayBlockingQueueTest : QueueContractTest() {

    // The queue of the JDK keeps its iterators in step with it. The port iterates on a copy.
    override val iteratorView: IteratorView
        get() = Unspecified

    override fun <E : Any> newQueue(capacity: Int): TestQueue<E> =
        ArrayBlockingQueue<E>(capacity).asTestQueue()

    @Test
    fun constructorCopiesTheElements() {
        val source = mutableListOf("a", "b")
        val queue = ArrayBlockingQueue(3, false, source)

        source += "c"

        assertEquals(listOf("a", "b"), queue.toList())
        assertEquals(1, queue.remainingCapacity())

        assertTrue(queue.offer("c"))
        assertFalse(queue.offer("d"))
        assertEquals("a", queue.poll())
    }

    @Test
    fun constructorFillsTheQueue() {
        val queue = ArrayBlockingQueue(2, true, listOf("a", "b"))

        assertEquals(0, queue.remainingCapacity())
        assertEquals("a", queue.poll())

        assertTrue(queue.offer("c"))
        assertEquals(listOf("b", "c"), queue.toList())
    }

    @Test
    fun constructorRefusesMoreElementsThanTheCapacity() {
        assertFailsWith<IllegalArgumentException> {
            ArrayBlockingQueue(1, false, listOf("a", "b"))
        }

        assertFailsWith<IllegalArgumentException> {
            ArrayBlockingQueue(0, false, emptyList<String>())
        }
    }
}

/**
 * Tests the public [ArrayBlockingQueue] on one thread when it is fair.
 */
class FairArrayBlockingQueueTest : QueueContractTest() {

    override val iteratorView: IteratorView
        get() = Unspecified

    override fun <E : Any> newQueue(capacity: Int): TestQueue<E> =
        ArrayBlockingQueue<E>(capacity, true).asTestQueue()
}
