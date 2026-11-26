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
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * A deque under test, whatever its type. [collection] is the deque itself.
 */
internal class TestDeque<E : Any>(
    val collection: MutableCollection<E>,
    val offer: (E) -> Boolean,
    val poll: () -> E?,
    val peek: () -> E?,
    val remove: () -> E,
    val element: () -> E,
    val addFirst: (E) -> Unit,
    val addLast: (E) -> Unit,
    val offerFirst: (E) -> Boolean,
    val offerLast: (E) -> Boolean,
    val removeFirst: () -> E,
    val removeLast: () -> E,
    val pollFirst: () -> E?,
    val pollLast: () -> E?,
    val getFirst: () -> E,
    val getLast: () -> E,
    val peekFirst: () -> E?,
    val peekLast: () -> E?,
    val push: (E) -> Unit,
    val pop: () -> E,
    val descendingIterator: () -> MutableIterator<E>,
) {

    val size: Int
        get() = collection.size

    fun toList(): List<E> = collection.iterator().asSequence().toList()

    fun toDescendingList(): List<E> = descendingIterator().asSequence().toList()
}

internal fun <E : Any> Deque<E>.asTestDeque(): TestDeque<E> = TestDeque(
    collection = this,
    offer = { offer(it) },
    poll = { poll() },
    peek = { peek() },
    remove = { remove() },
    element = { element() },
    addFirst = { addFirst(it) },
    addLast = { addLast(it) },
    offerFirst = { offerFirst(it) },
    offerLast = { offerLast(it) },
    removeFirst = { removeFirst() },
    removeLast = { removeLast() },
    pollFirst = { pollFirst() },
    pollLast = { pollLast() },
    getFirst = { getFirst() },
    getLast = { getLast() },
    peekFirst = { peekFirst() },
    peekLast = { peekLast() },
    push = { push(it) },
    pop = { pop() },
    descendingIterator = { descendingIterator() },
)

/**
 * The ends of a blocking deque under test, with the functions that wait.
 */
internal class TestBlockingDeque<E : Any>(
    val deque: TestDeque<E>,
    val putFirst: (E) -> Unit,
    val putLast: (E) -> Unit,
    val offerFirstFor: (E, Duration) -> Boolean,
    val offerLastFor: (E, Duration) -> Boolean,
    val takeFirst: () -> E,
    val takeLast: () -> E,
    val pollFirstFor: (Duration) -> E?,
    val pollLastFor: (Duration) -> E?,
    val remainingCapacity: () -> Int,
)

internal fun <E : Any> BlockingDeque<E>.asTestBlockingDeque(): TestBlockingDeque<E> =
    TestBlockingDeque(
        deque = asTestDeque(),
        putFirst = { putFirst(it) },
        putLast = { putLast(it) },
        offerFirstFor = { element, timeout ->
            offerFirst(element, timeout)
        },
        offerLastFor = { element, timeout ->
            offerLast(element, timeout)
        },
        takeFirst = { takeFirst() },
        takeLast = { takeLast() },
        pollFirstFor = { pollFirst(it) },
        pollLastFor = { pollLast(it) },
        remainingCapacity = { remainingCapacity() },
    )

/**
 * Tests what one thread sees of a deque without a capacity.
 */
abstract class DequeContractTest {

    internal abstract fun <E : Any> newDeque(): TestDeque<E>

    internal open val iteratorView: IteratorView
        get() = Live

    private fun dequeOf(vararg elements: String): TestDeque<String> {
        val deque = newDeque<String>()

        elements.forEach { deque.addLast(it) }
        return deque
    }

    @Test
    fun emptyDequeHoldsNothing() {
        val deque = newDeque<String>()

        assertEquals(0, deque.size)
        assertTrue(deque.collection.isEmpty())
        assertNull(deque.pollFirst())
        assertNull(deque.pollLast())
        assertNull(deque.peekFirst())
        assertNull(deque.peekLast())
        assertNull(deque.poll())
        assertNull(deque.peek())
        assertFailsWith<NoSuchElementException> { deque.removeFirst() }
        assertFailsWith<NoSuchElementException> { deque.removeLast() }
        assertFailsWith<NoSuchElementException> { deque.getFirst() }
        assertFailsWith<NoSuchElementException> { deque.getLast() }
        assertFailsWith<NoSuchElementException> { deque.remove() }
        assertFailsWith<NoSuchElementException> { deque.element() }
        assertFailsWith<NoSuchElementException> { deque.pop() }
        assertFalse(deque.collection.iterator().hasNext())
        assertFalse(deque.descendingIterator().hasNext())
        assertFailsWith<NoSuchElementException> { deque.descendingIterator().next() }
        assertEquals("[]", deque.collection.toString())
    }

    @Test
    fun elementsAreAddedAndTakenAtBothEnds() {
        val deque = newDeque<String>()

        deque.addFirst("b")
        deque.addFirst("a")
        deque.addLast("c")
        assertTrue(deque.offerLast("d"))
        assertTrue(deque.offerFirst("z"))

        assertEquals(listOf("z", "a", "b", "c", "d"), deque.toList())
        assertEquals(5, deque.size)
        assertEquals("z", deque.getFirst())
        assertEquals("z", deque.peekFirst())
        assertEquals("d", deque.getLast())
        assertEquals("d", deque.peekLast())

        assertEquals("z", deque.removeFirst())
        assertEquals("d", deque.removeLast())
        assertEquals("a", deque.pollFirst())
        assertEquals("c", deque.pollLast())
        assertEquals(listOf("b"), deque.toList())
        assertEquals("b", deque.peekFirst())
        assertEquals("b", deque.peekLast())

        assertEquals("b", deque.pollLast())
        assertNull(deque.pollFirst())
        assertEquals(0, deque.size)
    }

    @Test
    fun queueFunctionsAddAtTheTailAndTakeFromTheHead() {
        val deque = newDeque<String>()

        assertTrue(deque.collection.add("a"))
        assertTrue(deque.offer("b"))
        deque.addLast("c")

        assertEquals("a", deque.peek())
        assertEquals("a", deque.element())
        assertEquals("a", deque.poll())
        assertEquals("b", deque.remove())
        assertEquals(listOf("c"), deque.toList())
    }

    @Test
    fun stackFunctionsWorkAtTheHead() {
        val deque = newDeque<String>()

        deque.push("a")
        deque.push("b")
        deque.push("c")

        assertEquals(listOf("c", "b", "a"), deque.toList())
        assertEquals("c", deque.peek())
        assertEquals("c", deque.pop())
        assertEquals("b", deque.pop())
        assertEquals("a", deque.getLast())
        assertEquals("a", deque.pop())
        assertFailsWith<NoSuchElementException> { deque.pop() }
    }

    @Test
    fun dequeRefillsAfterItWasEmptiedFromEitherEnd() {
        val deque = newDeque<Int>()

        repeat(20) { round ->
            deque.addFirst(round)
            deque.addLast(round + 1)

            if (round % 2 == 0) {
                assertEquals(round, deque.pollFirst())
                assertEquals(round + 1, deque.pollFirst())
            } else {
                assertEquals(round + 1, deque.pollLast())
                assertEquals(round, deque.pollLast())
            }

            assertNull(deque.peekFirst())
            assertNull(deque.peekLast())
        }

        assertEquals(0, deque.size)
    }

    @Test
    fun removeTakesTheFirstOccurrence() {
        val deque = dequeOf("a", "b", "a", "c")

        assertTrue("a" in deque.collection)
        assertTrue(deque.collection.remove("a"))
        assertEquals(listOf("b", "a", "c"), deque.toList())
        assertFalse(deque.collection.remove("x"))

        // In the middle, then the last one, then the first one
        assertTrue(deque.collection.remove("a"))
        assertTrue(deque.collection.remove("c"))
        assertEquals("b", deque.peekLast())
        assertTrue(deque.collection.remove("b"))
        assertEquals(0, deque.size)
        assertNull(deque.peekFirst())
        assertNull(deque.peekLast())

        deque.addFirst("d")
        deque.addLast("e")
        assertEquals(listOf("d", "e"), deque.toList())
        assertEquals(listOf("e", "d"), deque.toDescendingList())
    }

    @Test
    fun clearEmptiesTheDeque() {
        val deque = dequeOf("a", "b", "c")

        deque.collection.clear()

        assertEquals(0, deque.size)
        assertNull(deque.peekFirst())
        assertNull(deque.peekLast())
        assertEquals(emptyList(), deque.toDescendingList())

        deque.addLast("d")
        deque.addFirst("c")
        assertEquals(listOf("c", "d"), deque.toList())
    }

    @Test
    fun bulkOperationsAddAndRemove() {
        val deque = dequeOf("a")

        assertTrue(deque.collection.addAll(listOf("b", "c", "d")))
        assertFalse(deque.collection.addAll(emptyList()))
        assertEquals(listOf("a", "b", "c", "d"), deque.toList())
        assertEquals(listOf("d", "c", "b", "a"), deque.toDescendingList())
        assertTrue(deque.collection.containsAll(listOf("a", "d")))

        assertTrue(deque.collection.removeAll(listOf("a", "d", "x")))
        assertEquals(listOf("b", "c"), deque.toList())
        assertTrue(deque.collection.retainAll(listOf("c")))
        assertEquals("[c]", deque.collection.toString())
        assertEquals("c", deque.peekFirst())
        assertEquals("c", deque.peekLast())

        assertFailsWith<IllegalArgumentException> { deque.collection.addAll(deque.collection) }
    }

    @Test
    fun descendingIteratorGoesFromTheTailToTheHead() {
        val deque = dequeOf("a", "b", "c", "d", "e")
        val iterator = deque.descendingIterator()

        assertFailsWith<IllegalStateException> { iterator.remove() }

        // The tail, one in the middle, then the head
        assertEquals("e", iterator.next())
        iterator.remove()
        assertFailsWith<IllegalStateException> { iterator.remove() }
        assertEquals("d", iterator.next())
        assertEquals("c", iterator.next())
        iterator.remove()
        assertEquals("b", iterator.next())
        assertEquals("a", iterator.next())
        iterator.remove()

        assertFalse(iterator.hasNext())
        assertFailsWith<NoSuchElementException> { iterator.next() }
        assertEquals(listOf("b", "d"), deque.toList())
        assertEquals(listOf("d", "b"), deque.toDescendingList())
    }

    @Test
    fun iteratorRemovesAtBothEndsAndInTheMiddle() {
        val deque = dequeOf("a", "b", "c", "d", "e")
        val iterator = deque.collection.iterator()

        assertEquals("a", iterator.next())
        iterator.remove()
        assertEquals("b", iterator.next())
        assertEquals("c", iterator.next())
        iterator.remove()
        assertEquals("d", iterator.next())
        assertEquals("e", iterator.next())
        iterator.remove()

        assertFalse(iterator.hasNext())
        assertEquals(listOf("b", "d"), deque.toList())
        assertEquals("b", deque.peekFirst())
        assertEquals("d", deque.peekLast())

        deque.addFirst("a")
        deque.addLast("e")
        assertEquals(listOf("a", "b", "d", "e"), deque.toList())
    }

    @Test
    fun iteratorsGoOnWhenTheDequeChanges() {
        val ascending = dequeOf("a", "b", "c")
        val forward = ascending.collection.iterator()

        // An iterator already holds the element that it returns next
        assertEquals("a", ascending.pollFirst())
        assertEquals("b", ascending.pollFirst())
        ascending.addLast("d")

        assertEquals("a", forward.next())
        assertRest(forward, live = listOf("c", "d"), snapshot = listOf("b", "c"))

        val descending = dequeOf("a", "b", "c")
        val backward = descending.descendingIterator()

        assertEquals("c", descending.pollLast())
        assertEquals("b", descending.pollLast())
        descending.addFirst("z")

        assertEquals("c", backward.next())
        assertRest(backward, live = listOf("a", "z"), snapshot = listOf("b", "a"))
    }

    // Checks what is left of an iterator whose deque changed since its creation
    private fun assertRest(iterator: Iterator<String>, live: List<String>, snapshot: List<String>) {
        val rest = iterator.asSequence().toList()

        when (iteratorView) {
            Live -> assertEquals(live, rest)
            Snapshot -> assertEquals(snapshot, rest)
            Unspecified -> assertTrue(rest == live || rest == snapshot)
        }
    }

    @Test
    fun iteratorEndsWhenTheDequeWasEmptied() {
        val deque = dequeOf("a", "b", "c")
        val iterator = deque.collection.iterator()

        deque.collection.clear()

        assertEquals("a", iterator.next())

        if (iteratorView == Live) {
            assertFalse(iterator.hasNext())
        }

        // Removing what is no longer there does nothing
        iterator.remove()
        assertEquals(0, deque.size)
    }
}

/**
 * Tests the ends of the public [LinkedBlockingDeque] on one thread.
 */
class LinkedBlockingDequeTest : DequeContractTest() {

    override fun <E : Any> newDeque(): TestDeque<E> = LinkedBlockingDeque<E>().asTestDeque()

    @Test
    fun fullDequeRefusesAnElementAtBothEnds() {
        val deque = LinkedBlockingDeque<String>(2)

        deque.putFirst("b")
        deque.putLast("c")

        assertEquals(0, deque.remainingCapacity())
        assertFalse(deque.offerFirst("a"))
        assertFalse(deque.offerLast("d"))
        assertFalse(deque.offerFirst("a", Duration.ZERO))
        assertFalse(deque.offerLast("d", Duration.ZERO))
        assertFailsWith<IllegalStateException> { deque.addFirst("a") }
        assertFailsWith<IllegalStateException> { deque.addLast("d") }
        assertFailsWith<IllegalStateException> { deque.push("a") }
        assertEquals(listOf("b", "c"), deque.toList())

        assertEquals("c", deque.takeLast())
        assertTrue(deque.offerFirst("a"))
        assertEquals("b", deque.pollLast(Duration.ZERO))
        assertEquals("a", deque.takeFirst())
        assertNull(deque.pollFirst(Duration.ZERO))
        assertEquals(2, deque.remainingCapacity())
    }

    @Test
    fun constructorCopiesTheElements() {
        val source = mutableListOf("a", "b")
        val deque = LinkedBlockingDeque(source)

        source += "c"

        assertEquals(listOf("a", "b"), deque.toList())
        assertEquals(Int.MAX_VALUE - 2, deque.remainingCapacity())
        assertFailsWith<IllegalArgumentException> { LinkedBlockingDeque<String>(0) }
    }
}

/**
 * Tests the public [LinkedBlockingDeque] as a blocking queue, on one thread.
 */
class LinkedBlockingDequeAsQueueTest : QueueContractTest() {

    override fun <E : Any> newQueue(capacity: Int): TestQueue<E> =
        LinkedBlockingDeque<E>(capacity).asTestQueue()
}

/**
 * Tests the public [ConcurrentLinkedDeque] on one thread.
 */
class ConcurrentLinkedDequeTest : DequeContractTest() {

    // The deque of the JDK keeps its iterators in step with it. The port iterates on a copy.
    override val iteratorView: IteratorView
        get() = Unspecified

    override fun <E : Any> newDeque(): TestDeque<E> = ConcurrentLinkedDeque<E>().asTestDeque()

    @Test
    fun constructorCopiesTheElements() {
        val source = mutableListOf("a", "b")
        val deque = ConcurrentLinkedDeque(source)

        source += "c"

        assertEquals(listOf("a", "b"), deque.toList())
        assertEquals("a", deque.peekFirst())
        assertEquals("b", deque.peekLast())
    }
}

/**
 * Tests the public [ConcurrentLinkedQueue] on one thread.
 */
class ConcurrentLinkedQueueTest {

    @Test
    fun emptyQueueHoldsNothing() {
        val queue = ConcurrentLinkedQueue<String>()

        assertEquals(0, queue.size)
        assertTrue(queue.isEmpty())
        assertNull(queue.poll())
        assertNull(queue.peek())
        assertFailsWith<NoSuchElementException> { queue.remove() }
        assertFailsWith<NoSuchElementException> { queue.element() }
        assertFalse(queue.iterator().hasNext())
        assertEquals("[]", queue.toString())
    }

    @Test
    fun elementsLeaveInTheOrderOfArrival() {
        val queue = ConcurrentLinkedQueue<String>()

        assertTrue(queue.offer("a"))
        assertTrue(queue.add("b"))
        assertTrue(queue.addAll(listOf("c", "d")))

        assertEquals(4, queue.size)
        assertEquals("a", queue.peek())
        assertEquals("a", queue.element())
        assertEquals("[a, b, c, d]", queue.toString())

        assertEquals("a", queue.poll())
        assertEquals("b", queue.remove())
        assertEquals(listOf("c", "d"), queue.toList())
    }

    @Test
    fun queueNeverRefusesAnElement() {
        val queue = ConcurrentLinkedQueue<Int>()

        repeat(10_000) { assertTrue(queue.offer(it)) }

        assertEquals(10_000, queue.size)
        repeat(10_000) { assertEquals(it, queue.poll()) }
        assertNull(queue.poll())
    }

    @Test
    fun elementsAreFoundAndRemoved() {
        val queue = ConcurrentLinkedQueue(listOf("a", "b", "a", "c"))

        assertTrue("c" in queue)
        assertFalse("x" in queue)
        assertTrue(queue.containsAll(listOf("a", "b")))

        // The first occurrence
        assertTrue(queue.remove("a"))
        assertFalse(queue.remove("x"))
        assertEquals(listOf("b", "a", "c"), queue.toList())

        assertTrue(queue.removeAll(listOf("a", "x")))
        assertTrue(queue.retainAll(listOf("c")))
        assertEquals(listOf("c"), queue.toList())

        queue.clear()
        assertTrue(queue.isEmpty())
    }

    @Test
    fun iteratorRemovesTheElementItReturned() {
        val queue = ConcurrentLinkedQueue(listOf("a", "b", "c"))
        val iterator = queue.iterator()

        assertFailsWith<IllegalStateException> { iterator.remove() }
        assertEquals("a", iterator.next())
        assertEquals("b", iterator.next())
        iterator.remove()
        assertEquals("c", iterator.next())

        assertFalse(iterator.hasNext())
        assertFailsWith<NoSuchElementException> { iterator.next() }
        assertEquals(listOf("a", "c"), queue.toList())
    }

    @Test
    fun addingAQueueToItselfFails() {
        val queue = ConcurrentLinkedQueue(listOf("a"))

        assertFailsWith<IllegalArgumentException> { queue.addAll(queue) }
    }

    @Test
    fun constructorCopiesTheElements() {
        val source = mutableListOf("a", "b")
        val queue = ConcurrentLinkedQueue(source)

        source += "c"

        assertEquals(listOf("a", "b"), queue.toList())
    }
}
