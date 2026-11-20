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
private const val KEY_COUNT = 20_000
private const val COUNTER_COUNT = 64
private const val ROUND_COUNT = 20_000

/**
 * Stress tests of the public [ConcurrentHashMap] on real threads.
 */
open class ConcurrentHashMapStressTest {

    protected open fun <K : Any, V : Any> newMap(): ConcurrentHashMap<K, V> = ConcurrentHashMap()

    @Test
    fun computeIfAbsentComputesEachKeyOnceAcrossThreads() {
        val map = newMap<Int, Int>()
        val computations = AtomicInt(0)

        runConcurrently(THREAD_COUNT) {
            repeat(KEY_COUNT) { key ->
                val value = map.computeIfAbsent(key) {
                    val _ = computations.incrementAndFetch()
                    -key
                }

                assertEquals(-key, value)
            }
        }

        assertEquals(KEY_COUNT, computations.load())
        assertEquals(KEY_COUNT, map.size)
    }

    @Test
    fun computeAppliesEachCallOnceAcrossThreads() {
        val map = newMap<Int, Int>()
        val calls = AtomicInt(0)

        runConcurrently(THREAD_COUNT) {
            repeat(ROUND_COUNT) { round ->
                map.compute(round % COUNTER_COUNT) { _, count ->
                    val _ = calls.incrementAndFetch()
                    (count ?: 0) + 1
                }
            }
        }

        assertEquals(THREAD_COUNT * ROUND_COUNT, calls.load())
        assertEquals(THREAD_COUNT * ROUND_COUNT, map.values.sum())
    }

    @Test
    fun mergeAppliesEachCallOnceAcrossThreads() {
        val map = newMap<Int, Int>()

        runConcurrently(THREAD_COUNT) {
            repeat(ROUND_COUNT) { round ->
                map.merge(round % COUNTER_COUNT, 1) { count, one -> count + one }
            }
        }

        assertEquals(COUNTER_COUNT, map.size)
        assertEquals(THREAD_COUNT * ROUND_COUNT, map.values.sum())
    }

    @Test
    fun eachKeyIsInsertedAndRemovedOnceAcrossThreads() {
        val map = newMap<Int, Int>()
        val inserted = AtomicInt(0)
        val removed = AtomicInt(0)

        runConcurrently(THREAD_COUNT) { thread ->
            repeat(KEY_COUNT) { key ->
                if (map.putIfAbsent(key, thread) == null) {
                    val _ = inserted.incrementAndFetch()
                }
            }
        }

        assertEquals(KEY_COUNT, inserted.load())

        runConcurrently(THREAD_COUNT) { thread ->
            repeat(KEY_COUNT) { key ->
                if (map.remove(key, thread)) {
                    val _ = removed.incrementAndFetch()
                }
            }
        }

        assertEquals(KEY_COUNT, removed.load())
        assertTrue(map.isEmpty())
    }
}
