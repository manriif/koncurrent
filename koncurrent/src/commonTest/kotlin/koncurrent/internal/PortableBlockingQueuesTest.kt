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
package koncurrent.internal

import koncurrent.DequeContractTest
import koncurrent.IteratorView
import koncurrent.PriorityQueueContractTest
import koncurrent.QueueContractTest
import koncurrent.TestBlockingDeque
import koncurrent.TestDeque
import koncurrent.TestQueue
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The port of a blocking queue as a queue under test.
 */
internal fun <E : Any> PortableBlockingQueue<E>.asTestQueue(): TestQueue<E> = TestQueue(
    collection = this,
    offer = { offer(it) },
    offerFor = { element, timeout ->
        offer(element, timeout.inWholeNanoseconds)
    },
    put = { put(it) },
    poll = { poll() },
    pollFor = { poll(it.inWholeNanoseconds) },
    take = { take() },
    peek = { peek() },
    remove = { remove() },
    element = { element() },
    remainingCapacity = { remainingCapacity() },
    drainAllTo = { drainTo(it, Int.MAX_VALUE) },
    drainTo = { collection, maxElements ->
        drainTo(collection, maxElements)
    },
)

/**
 * Tests [PortableLinkedBlockingQueue], on the JVM too where it backs nothing.
 */
@Suppress("unused")
class PortableLinkedBlockingQueueTest : QueueContractTest() {

    override fun <E : Any> newQueue(capacity: Int): TestQueue<E> =
        PortableLinkedBlockingQueue<E>(capacity).asTestQueue()
}

/**
 * The port of the blocking deque as a deque under test.
 */
internal fun <E : Any> PortableLinkedBlockingDeque<E>.asTestDeque(): TestDeque<E> = TestDeque(
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

internal fun <E : Any> PortableLinkedBlockingDeque<E>.asTestBlockingDeque(): TestBlockingDeque<E> =
    TestBlockingDeque(
        deque = asTestDeque(),
        putFirst = { putFirst(it) },
        putLast = { putLast(it) },
        offerFirstFor = { element, timeout ->
            offerFirst(element, timeout.inWholeNanoseconds)
        },
        offerLastFor = { element, timeout ->
            offerLast(element, timeout.inWholeNanoseconds)
        },
        takeFirst = { takeFirst() },
        takeLast = { takeLast() },
        pollFirstFor = { pollFirst(it.inWholeNanoseconds) },
        pollLastFor = { pollLast(it.inWholeNanoseconds) },
        remainingCapacity = { remainingCapacity() },
    )

/**
 * Tests the ends of [PortableLinkedBlockingDeque], on the JVM too where it backs nothing.
 */
class PortableLinkedBlockingDequeTest : DequeContractTest() {

    override fun <E : Any> newDeque(): TestDeque<E> =
        PortableLinkedBlockingDeque<E>().asTestDeque()
}

/**
 * Tests [PortableLinkedBlockingDeque] as a blocking queue.
 */
@Suppress("unused")
class PortableLinkedBlockingDequeAsQueueTest : QueueContractTest() {

    override fun <E : Any> newQueue(capacity: Int): TestQueue<E> =
        PortableLinkedBlockingDeque<E>(capacity).asTestQueue()
}

/**
 * Tests [PortableArrayBlockingQueue], on the JVM too where it backs nothing.
 */
@Suppress("unused")
class PortableArrayBlockingQueueTest : QueueContractTest() {

    override val iteratorView: IteratorView
        get() = Snapshot

    override fun <E : Any> newQueue(capacity: Int): TestQueue<E> =
        PortableArrayBlockingQueue<E>(capacity).asTestQueue()
}

/**
 * Tests [PortablePriorityBlockingQueue], on the JVM too where it backs nothing. It starts with
 * room for one element, to grow as often as it can.
 */
class PortablePriorityBlockingQueueTest : PriorityQueueContractTest() {

    override fun <E : Any> newQueue(comparator: Comparator<in E>?): TestQueue<E> =
        PortablePriorityBlockingQueue(1, comparator).asTestQueue()

    @Test
    fun constructorOrdersTheElements() {
        val natural = PortablePriorityBlockingQueue(listOf(3, 1, 2, 5, 4), null)
        val reversed = PortablePriorityBlockingQueue(listOf(3, 1, 2, 5, 4), reverseOrder())

        assertEquals(listOf(1, 2, 3, 4, 5), generateSequence { natural.poll() }.toList())
        assertEquals(listOf(5, 4, 3, 2, 1), generateSequence { reversed.poll() }.toList())
        assertEquals(0, PortablePriorityBlockingQueue(emptyList<Int>(), null).size)
    }
}
