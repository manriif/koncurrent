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
import koncurrent.Name
import koncurrent.TestDeque
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private const val SEED = 20261124
private const val OPERATION_COUNT = 60_000
private const val PHASE_LENGTH = 3_000
private const val BURST_SIZE = 5_000

/**
 * The port of the concurrent deque as a deque under test.
 */
internal fun <E : Any> PortableConcurrentDeque<E>.asTestDeque(): TestDeque<E> = TestDeque(
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
 * Tests [PortableConcurrentDeque] on one thread, on the JVM too where it backs nothing. The
 * ring is its own code, so it is also compared with the deque of the standard library.
 */
class PortableConcurrentDequeTest : DequeContractTest() {

    override val iteratorView: IteratorView
        get() = Snapshot

    override fun <E : Any> newDeque(): TestDeque<E> = PortableConcurrentDeque<E>().asTestDeque()

    /**
     * Runs random operations on the port and on an `ArrayDeque`. Phases that mostly add
     * alternate with phases that mostly remove, so that the ring grows, wraps around, closes
     * gaps from both sides and empties many times.
     */
    @Test
    fun ringBehavesAsTheDequeOfTheStandardLibrary() {
        val deque = PortableConcurrentDeque<Int>()
        val model = ArrayDeque<Int>()
        val random = Random(SEED)

        repeat(OPERATION_COUNT) { step ->
            val growing = (step / PHASE_LENGTH) % 2 == 0
            val adds = random.nextInt(10) < if (growing) 7 else 3

            if (adds) {
                if (random.nextBoolean()) {
                    deque.addLast(step)
                    model.addLast(step)
                } else {
                    deque.addFirst(step)
                    model.addFirst(step)
                }
            } else {
                when (random.nextInt(5)) {
                    0 -> assertEquals(model.removeFirstOrNull(), deque.pollFirst())
                    1 -> assertEquals(model.removeLastOrNull(), deque.pollLast())
                    2 -> assertEquals(model.firstOrNull(), deque.peekFirst())
                    3 -> assertEquals(model.lastOrNull(), deque.peekLast())

                    else -> if (model.isNotEmpty()) {
                        // Every element is its own step, so this removes one known place
                        val element = model[random.nextInt(model.size)]

                        assertTrue(deque.remove(element))
                        assertTrue(model.remove(element))
                    }
                }
            }

            assertEquals(model.size, deque.size)
        }

        assertEquals(model.toList(), deque.iterator().asSequence().toList())
        assertEquals(model.reversed(), deque.descendingIterator().asSequence().toList())
    }

    @Test
    fun ringServesAgainAfterABurst() {
        val deque = PortableConcurrentDeque<Int>()

        repeat(2) {
            repeat(BURST_SIZE) { deque.addLast(it) }
            assertEquals(BURST_SIZE, deque.size)

            repeat(BURST_SIZE) { assertEquals(it, deque.pollFirst()) }
            assertNull(deque.pollFirst())
        }

        repeat(BURST_SIZE) { deque.addFirst(it) }
        deque.clear()

        deque.addLast(1)
        deque.addFirst(0)
        assertEquals(listOf(0, 1), deque.iterator().asSequence().toList())
    }

    @Test
    fun constructorCopiesTheElements() {
        val deque = PortableConcurrentDeque(List(100) { it })

        assertEquals(100, deque.size)
        assertEquals(0, deque.peekFirst())
        assertEquals(99, deque.peekLast())
    }

    @Test
    fun iteratorRemovesTheVeryElementItReturned() {
        val first = Name("a")
        val second = Name("a")
        val deque = PortableConcurrentDeque(listOf(first, Name("b"), second))
        val iterator = deque.iterator()

        repeat(3) {
            val _ = iterator.next()
        }

        // The last one, not the first one, which equals it
        iterator.remove()

        assertEquals(2, deque.size)
        assertSame(first, deque.pollFirst())
        assertEquals(Name("b"), deque.pollFirst())
    }
}
