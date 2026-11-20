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
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val THREAD_COUNT = 8
private const val ELEMENT_COUNT = 50_000

/**
 * Stress tests of [ConcurrentHashSet] on real threads.
 */
class ConcurrentHashSetStressTest {

    @Test
    fun eachElementIsAddedOnceAcrossThreads() {
        val set = ConcurrentHashSet<Int>()
        val added = AtomicInt(0)

        runConcurrently(THREAD_COUNT) {
            repeat(ELEMENT_COUNT) { element ->
                if (set.add(element)) {
                    val _ = added.incrementAndFetch()
                }
            }
        }

        assertEquals(ELEMENT_COUNT, added.load())
        assertEquals(ELEMENT_COUNT, set.size)
        assertEquals((0..<ELEMENT_COUNT).toSet(), set.toSet())
    }

    @Test
    fun eachElementIsRemovedOnceAcrossThreads() {
        val set = ConcurrentHashSet<Int>()
        val removed = AtomicInt(0)
        set.addAll(0..<ELEMENT_COUNT)

        runConcurrently(THREAD_COUNT) {
            repeat(ELEMENT_COUNT) { element ->
                if (set.remove(element)) {
                    val _ = removed.incrementAndFetch()
                }
            }
        }

        assertEquals(ELEMENT_COUNT, removed.load())
        assertTrue(set.isEmpty())
    }
}
