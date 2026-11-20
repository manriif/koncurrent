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

import koncurrent.CollidingKey
import koncurrent.OrderedCollidingKey
import koncurrent.runConcurrently
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicIntArray
import kotlin.concurrent.atomics.incrementAndFetchAt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val THREAD_COUNT = 8
private const val KEYS_PER_THREAD = 25_000
private const val SHARED_KEY_COUNT = 512
private const val OPERATION_COUNT = 50_000
private const val COUNTER_COUNT = 16
private const val STABLE_KEY_COUNT = 2_000

/**
 * Stress tests of [PortableConcurrentHashMap] on real threads.
 */
class PortableConcurrentHashMapStressTest {

    @Test
    fun concurrentInsertionsOfDistinctKeysAreAllKept() {
        val map = PortableConcurrentHashMap<Int, Int>()

        // The table grows from its default size, so the threads resize it together
        runConcurrently(THREAD_COUNT) { thread ->
            repeat(KEYS_PER_THREAD) { index ->
                val key = thread * KEYS_PER_THREAD + index

                assertNull(map.put(key, -key))
            }
        }

        assertEquals(THREAD_COUNT * KEYS_PER_THREAD, map.size)
        assertEquals(THREAD_COUNT * KEYS_PER_THREAD, map.keys.count())
        repeat(THREAD_COUNT * KEYS_PER_THREAD) { assertEquals(-it, map[it]) }
    }

    @Test
    fun concurrentRemovalsOfDistinctKeysEmptyTheMap() {
        val map = PortableConcurrentHashMap<Int, Int>()
        repeat(THREAD_COUNT * KEYS_PER_THREAD) { map[it] = it }

        runConcurrently(THREAD_COUNT) { thread ->
            repeat(KEYS_PER_THREAD) { index ->
                val key = thread * KEYS_PER_THREAD + index

                assertEquals(key, map.remove(key))
            }
        }

        assertTrue(map.isEmpty())
        assertEquals(0, map.size)
        assertFalse(map.keys.iterator().hasNext())
    }

    @Test
    fun concurrentUpdatesOfSharedKeysStayConsistent() {
        assertSharedKeysStayConsistent(List(SHARED_KEY_COUNT) { it })
    }

    @Test
    fun concurrentUpdatesOfCollidingKeysStayConsistent() {
        // A few bins hold all the keys, as trees
        val keys = List(SHARED_KEY_COUNT) { CollidingKey(it, hash = (it % 12) * 64) }
        val map = assertSharedKeysStayConsistent(keys)

        assertTreeInvariants(map)
    }

    @Test
    fun concurrentUpdatesOfOrderedCollidingKeysStayConsistent() {
        val keys = List(SHARED_KEY_COUNT) { OrderedCollidingKey(it, hash = it % 4) }
        val map = assertSharedKeysStayConsistent(keys)

        assertTreeInvariants(map)
    }

    @Test
    fun concurrentMergesCountEveryCall() {
        val map = PortableConcurrentHashMap<Int, Int>()

        runConcurrently(THREAD_COUNT) {
            repeat(OPERATION_COUNT) { map.merge(it % COUNTER_COUNT, 1, Int::plus) }
        }

        assertEquals(COUNTER_COUNT, map.size)
        assertEquals(THREAD_COUNT * OPERATION_COUNT, map.values.sum())
    }

    @Test
    fun concurrentComputesCountEveryCall() {
        val map = PortableConcurrentHashMap<Int, Int>()

        // A count that reaches 4 is removed, so the bins also empty and fill again
        runConcurrently(THREAD_COUNT) {
            repeat(OPERATION_COUNT) { operation ->
                map.compute(operation % COUNTER_COUNT) { _, count ->
                    if (count == 3) null else (count ?: 0) + 1
                }
            }
        }

        val callsPerCounter = THREAD_COUNT * OPERATION_COUNT / COUNTER_COUNT

        repeat(COUNTER_COUNT) { counter ->
            assertEquals((callsPerCounter % 4).takeIf { it != 0 }, map[counter])
        }
    }

    @Test
    fun concurrentReplacesNeverLoseAnUpdate() {
        val map = PortableConcurrentHashMap<Int, Int>()
        repeat(COUNTER_COUNT) { map[it] = 0 }

        runConcurrently(THREAD_COUNT) {
            repeat(OPERATION_COUNT) { operation ->
                val key = operation % COUNTER_COUNT

                do {
                    val count = map.getValue(key)
                } while (!map.replace(key, count, count + 1))
            }
        }

        assertEquals(THREAD_COUNT * OPERATION_COUNT, map.values.sum())
    }

    @Test
    fun computeIfAbsentRunsItsFunctionOncePerKey() {
        val map = PortableConcurrentHashMap<Int, Int>()
        val calls = AtomicIntArray(KEYS_PER_THREAD)

        runConcurrently(THREAD_COUNT) {
            repeat(KEYS_PER_THREAD) { key ->
                val value = map.computeIfAbsent(key) { calls.incrementAndFetchAt(it) + it }

                assertEquals(key + 1, value)
            }
        }

        assertEquals(KEYS_PER_THREAD, map.size)
        repeat(KEYS_PER_THREAD) { assertEquals(1, calls.loadAt(it)) }
    }

    @Test
    fun lookupsFindEveryKeyDuringResizes() {
        val map = PortableConcurrentHashMap<Int, Int>()
        val inserted = AtomicInt(0)
        val total = THREAD_COUNT * KEYS_PER_THREAD

        // One thread grows the map. The others look up the keys it has inserted so far.
        runConcurrently(THREAD_COUNT) { thread ->
            if (thread == 0) {
                repeat(total) { key ->
                    map[key] = -key
                    inserted.store(key + 1)
                }
            } else {
                val random = Random(thread)
                var count = 0

                while (count < total) {
                    count = inserted.load()

                    if (count > 0) {
                        val key = random.nextInt(count)

                        assertEquals(-key, map[key])
                    }
                }
            }
        }

        assertEquals(total, map.size)
    }

    @Test
    fun iterationsGiveEveryStableKeyOnceDuringUpdates() {
        val map = PortableConcurrentHashMap<Int, Int>()
        val running = AtomicInt(THREAD_COUNT / 2)
        repeat(STABLE_KEY_COUNT) { map[it] = it }

        // Half of the threads add and remove other keys, which also resizes the table
        runConcurrently(THREAD_COUNT) { thread ->
            if (thread < THREAD_COUNT / 2) {
                val start = STABLE_KEY_COUNT + thread * KEYS_PER_THREAD

                repeat(KEYS_PER_THREAD) { index ->
                    map[start + index] = index

                    if (index % 3 == 0) {
                        assertEquals(index, map.remove(start + index))
                    }
                }

                val _ = running.addAndFetch(-1)
            } else {
                do {
                    val seen = BooleanArray(STABLE_KEY_COUNT)

                    for (key in map.keys) {
                        if (key < STABLE_KEY_COUNT) {
                            assertFalse(seen[key], "key $key given twice")
                            seen[key] = true
                        }
                    }

                    assertTrue(seen.all { it }, "a stable key is missing")
                } while (running.load() > 0)
            }
        }

        assertEquals(map.size, map.keys.count())
    }

    @Test
    fun countStaysExactAfterConcurrentClears() {
        val map = PortableConcurrentHashMap<Int, Int>()

        runConcurrently(THREAD_COUNT) { thread ->
            val random = Random(thread)

            repeat(OPERATION_COUNT) { operation ->
                map[random.nextInt(SHARED_KEY_COUNT * 8)] = operation

                if (thread == 0 && operation % 1_000 == 0) {
                    map.clear()
                }
            }
        }

        assertEquals(map.keys.count(), map.size)
    }

    /**
     * Makes the threads add and remove [keys] at random. Then checks that each key is in the
     * returned map if and only if it was added once more than it was removed.
     */
    @IgnorableReturnValue
    private fun <K : Any> assertSharedKeysStayConsistent(
        keys: List<K>,
    ): PortableConcurrentHashMap<K, Int> {
        val map = PortableConcurrentHashMap<K, Int>()
        val deltas = List(THREAD_COUNT) { IntArray(keys.size) }

        runConcurrently(THREAD_COUNT) { thread ->
            val random = Random(thread)
            val delta = deltas[thread]

            repeat(OPERATION_COUNT) {
                val index = random.nextInt(keys.size)
                val key = keys[index]

                when (random.nextInt(4)) {
                    0 -> if (map.putIfAbsent(key, thread) == null) {
                        delta[index]++
                    }

                    1 -> if (map.remove(key) != null) {
                        delta[index]--
                    }

                    2 -> if (map.remove(key, thread)) {
                        delta[index]--
                    }

                    else -> assertTrue((map[key] ?: 0) in 0..<THREAD_COUNT)
                }
            }
        }

        var size = 0

        keys.forEachIndexed { index, key ->
            val count = deltas.sumOf { it[index] }

            assertTrue(count == 0 || count == 1, "key $key was added $count times")
            assertEquals(count == 1, map.containsKey(key))
            size += count
        }

        assertEquals(size, map.size)
        assertEquals(size, map.keys.count())

        return map
    }
}
