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
import kotlin.time.Duration.Companion.seconds

/**
 * Tests the blocking queues on a platform with one thread. A wait that could never end fails,
 * and a wait with a time limit answers at once.
 */
class SingleThreadQueuesTest {

    private fun queues(): List<BlockingQueue<String>> = listOf(
        LinkedBlockingQueue(1),
        ArrayBlockingQueue(1),
        LinkedBlockingDeque(1),
    )

    @Test
    fun takeFailsOnAnEmptyQueue() {
        (queues() + PriorityBlockingQueue()).forEach { queue ->
            assertFailsWith<IllegalStateException> { queue.take() }
            assertNull(queue.poll(1.seconds))
            assertEquals(0, queue.size)
        }
    }

    @Test
    fun putFailsOnAFullQueue() {
        queues().forEach { queue ->
            queue.put("a")

            assertFailsWith<IllegalStateException> { queue.put("b") }
            assertFalse(queue.offer("b", 1.seconds))
            assertEquals(listOf("a"), queue.toList())

            // The queue still works
            assertEquals("a", queue.take())
            queue.put("b")
            assertEquals("b", queue.poll())
        }
    }

    @Test
    fun endsOfADequeFailWhereTheyWouldWait() {
        val deque = LinkedBlockingDeque<String>(1)

        assertFailsWith<IllegalStateException> { deque.takeFirst() }
        assertFailsWith<IllegalStateException> { deque.takeLast() }
        assertNull(deque.pollFirst(1.seconds))
        assertNull(deque.pollLast(1.seconds))

        deque.putFirst("a")

        assertFailsWith<IllegalStateException> { deque.putFirst("b") }
        assertFailsWith<IllegalStateException> { deque.putLast("b") }
        assertFalse(deque.offerFirst("b", 1.seconds))
        assertFalse(deque.offerLast("b", 1.seconds))
        assertEquals(listOf("a"), deque.toList())
        assertEquals("a", deque.takeLast())
    }
}
