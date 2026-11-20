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
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val THREAD_COUNT = 8
private const val WRITER_COUNT = 4
private const val ELEMENT_COUNT = 1_000
private const val ROUND_COUNT = 2_000
private const val PAIR_SIZE = 2

/**
 * Tests what several threads see of a copy-on-write list.
 */
abstract class ListConcurrencyContractTest {

    internal abstract fun <E> newList(): TestList<E>

    @Test
    fun everyAdditionIsKeptAcrossThreads() {
        val list = newList<Int>().list

        runConcurrently(THREAD_COUNT) { thread ->
            repeat(ELEMENT_COUNT) { list += thread * ELEMENT_COUNT + it }
        }

        assertEquals(THREAD_COUNT * ELEMENT_COUNT, list.size)
        assertEquals((0..<THREAD_COUNT * ELEMENT_COUNT).toList(), list.sorted())
    }

    @Test
    fun addIfAbsentAddsEachElementOnceAcrossThreads() {
        val list = newList<Int>()
        val added = AtomicInt(0)

        runConcurrently(THREAD_COUNT) {
            repeat(ELEMENT_COUNT) { element ->
                if (list.addIfAbsent(element)) {
                    val _ = added.incrementAndFetch()
                }
            }
        }

        assertEquals(ELEMENT_COUNT, added.load())
        assertEquals((0..<ELEMENT_COUNT).toList(), list.list.sorted())
    }

    @Test
    fun everyRemovalHappensOnceAcrossThreads() {
        val list = newList<Int>().list
        val removed = AtomicInt(0)
        list.addAll(0..<ELEMENT_COUNT)

        runConcurrently(THREAD_COUNT) {
            repeat(ELEMENT_COUNT) { element ->
                if (list.remove(element)) {
                    val _ = removed.incrementAndFetch()
                }
            }
        }

        assertEquals(ELEMENT_COUNT, removed.load())
        assertTrue(list.isEmpty())
    }

    @Test
    fun readersSeeTheElementsOfOneUpdate() {
        val list = newList<Int>().list
        val writing = AtomicInt(WRITER_COUNT)
        val failed = AtomicBoolean(false)

        // The writers add and remove pairs of equal elements, so no update leaves half a pair
        runConcurrently(THREAD_COUNT) { thread ->
            if (thread < WRITER_COUNT) {
                repeat(ROUND_COUNT) { round ->
                    val pair = List(PAIR_SIZE) { thread * ROUND_COUNT + round }

                    list.addAll(pair)

                    if (round % 2 == 1) {
                        list.removeAll(pair)
                    }
                }

                val _ = writing.fetchAndAdd(-1)
            } else {
                while (writing.load() > 0 && !failed.load()) {
                    // Reads through the iterator. A copy function of the standard library
                    // reads the size and then the elements, in two steps.
                    val counts = HashMap<Int, Int>()
                    list.forEach { counts[it] = (counts[it] ?: 0) + 1 }

                    if (counts.values.any { it != PAIR_SIZE }) {
                        failed.store(true)
                    }
                }
            }
        }

        assertEquals(false, failed.load())
        assertEquals(WRITER_COUNT * ROUND_COUNT / 2 * PAIR_SIZE, list.size)
    }
}

/**
 * Tests the public [CopyOnWriteArrayList] on real threads.
 */
class CopyOnWriteArrayListStressTest : ListConcurrencyContractTest() {

    override fun <E> newList(): TestList<E> {
        val list = CopyOnWriteArrayList<E>()

        return TestList(list, { list.addIfAbsent(it) }, { list.addAllAbsent(it) })
    }
}
