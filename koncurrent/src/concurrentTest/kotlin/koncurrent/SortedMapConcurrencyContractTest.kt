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
import kotlin.concurrent.atomics.AtomicIntArray
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetchAt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val THREAD_COUNT = 8
private const val KEY_COUNT = 5_000
private const val SHARED_KEY_COUNT = 2_000
private const val ROUND_COUNT = 20_000
private const val KEY_RANGE = 1_000
private const val LOW = 300
private const val HIGH = 700

/**
 * Tests what several threads see of a sorted map.
 */
abstract class SortedMapConcurrencyContractTest {

    internal abstract fun newMap(): TestSortedMap

    @Test
    fun threadsThatPutTheirOwnKeysLoseNone() {
        val map = newMap()

        runConcurrently(THREAD_COUNT) { thread ->
            // The keys of the threads are interleaved, so that they meet in the same places
            repeat(KEY_COUNT) { assertNull(map.put(it * THREAD_COUNT + thread, "v")) }
        }

        assertEquals(THREAD_COUNT * KEY_COUNT, map.size)
        assertEquals(List(THREAD_COUNT * KEY_COUNT) { it }, map.keySet().toList())

        runConcurrently(THREAD_COUNT) { thread ->
            repeat(KEY_COUNT) { assertEquals("v", map.remove(it * THREAD_COUNT + thread)) }
        }

        assertTrue(map.isEmpty())
    }

    @Test
    fun aKeyIsAddedByOneThreadOnly() {
        val map = newMap()
        val additions = AtomicInt(0)

        runConcurrently(THREAD_COUNT) { thread ->
            repeat(SHARED_KEY_COUNT) { key ->
                if (map.putIfAbsent(key, "v$thread") == null) {
                    val _ = additions.incrementAndFetch()
                }
            }
        }

        assertEquals(SHARED_KEY_COUNT, additions.load())
        assertEquals(SHARED_KEY_COUNT, map.size)
    }

    @Test
    fun anEntryIsPolledByOneThreadOnlyAndInOrder() {
        val map = newMap()
        val polled = AtomicIntArray(SHARED_KEY_COUNT)

        repeat(SHARED_KEY_COUNT) { assertNull(map.put(it, "v$it")) }

        runConcurrently(THREAD_COUNT) { thread ->
            var previous = if (thread % 2 == 0) -1 else SHARED_KEY_COUNT

            while (true) {
                // Half of the threads take from the first key, the others from the last one
                val entry = if (thread % 2 == 0) map.pollFirstEntry() else map.pollLastEntry()
                val key = entry?.first ?: break

                assertTrue(if (thread % 2 == 0) key > previous else key < previous)
                previous = key

                val _ = polled.incrementAndFetchAt(key)
            }
        }

        repeat(SHARED_KEY_COUNT) { assertEquals(1, polled.loadAt(it)) }
        assertTrue(map.isEmpty())
    }

    @Test
    fun readersSeeSortedKeysWhileOthersChangeThem() {
        val map = newMap()

        runConcurrently(THREAD_COUNT) { thread ->
            val random = Random(thread)

            if (thread % 2 == 0) {
                repeat(ROUND_COUNT) {
                    val key = random.nextInt(KEY_RANGE)

                    if (random.nextBoolean()) {
                        val _ = map.put(key, "v")
                    } else {
                        val _ = map.remove(key)
                    }
                }
            } else {
                repeat(ROUND_COUNT / 100) {
                    val key = random.nextInt(KEY_RANGE)
                    val ceiling = map.ceilingKey(key)
                    val lower = map.lowerKey(key)

                    assertTrue(ceiling == null || ceiling >= key)
                    assertTrue(lower == null || lower < key)
                    assertAscending(map.keySet().toList())
                    assertAscending(map.descendingKeySet().toList().asReversed())
                }
            }
        }
    }

    /**
     * The even keys stay in the map. The odd ones come and go between them, which moves the
     * even ones around in what holds them. A reader must find each even key all the same, with
     * its own value and its neighbours.
     */
    @Test
    fun readersAlwaysFindTheKeysThatStay() {
        val map = newMap()

        for (key in 0..<KEY_RANGE step 2) {
            assertNull(map.put(key, "v$key"))
        }

        runConcurrently(THREAD_COUNT) { thread ->
            val random = Random(thread)

            repeat(ROUND_COUNT) {
                val even = random.nextInt(KEY_RANGE / 2) * 2
                val odd = even + 1

                if (thread % 4 == 0) {
                    if (random.nextBoolean()) {
                        val _ = map.put(odd, "v$odd")
                    } else {
                        val _ = map.remove(odd)
                    }
                } else {
                    assertEquals("v$even", map.get(even))
                    assertEquals(even, map.floorKey(even))
                    assertEquals(even, map.ceilingKey(even))
                    assertEquals(even to "v$even", map.floorEntry(even))

                    val lower = map.lowerKey(even)
                    val higher = map.higherEntry(even)
                    val floor = map.floorKey(odd)
                    val ceiling = map.ceilingKey(odd)
                    val oddValue = map.get(odd)

                    // The odd key before, or else the even one before it
                    if (even == 0) {
                        assertNull(lower)
                    } else {
                        assertTrue(lower == even - 1 || lower == even - 2)
                    }

                    // The odd key after, or else the even one after it, with its own value
                    if (even < KEY_RANGE - 2) {
                        assertTrue(higher?.first == odd || higher?.first == even + 2)
                        assertTrue(ceiling == odd || ceiling == even + 2)
                    }

                    assertTrue(higher == null || higher.second == "v${higher.first}")
                    assertTrue(floor == odd || floor == even)
                    assertTrue(oddValue == null || oddValue == "v$odd")

                    assertEquals(0 to "v0", map.firstEntry())
                    assertTrue(map.lastKey() >= KEY_RANGE - 2)
                }
            }
        }

        for (key in 0..<KEY_RANGE step 2) {
            assertEquals("v$key", map.get(key))
        }
    }

    @Test
    fun aViewStaysBetweenItsBoundsWhileTheMapChanges() {
        val map = newMap()
        val view = map.subMap(LOW, true, HIGH, false)

        runConcurrently(THREAD_COUNT) { thread ->
            val random = Random(thread)

            if (thread % 2 == 0) {
                repeat(ROUND_COUNT) {
                    val key = random.nextInt(KEY_RANGE)

                    if (random.nextBoolean()) {
                        val _ = map.put(key, "v")
                    } else {
                        val _ = map.remove(key)
                    }
                }
            } else {
                repeat(ROUND_COUNT / 100) {
                    val keys = view.keySet().toList()

                    assertAscending(keys)
                    assertTrue(keys.all { it in LOW..<HIGH })

                    val first = view.firstEntry()?.first

                    assertTrue(first == null || first in LOW..<HIGH)
                    assertTrue(view.size <= HIGH - LOW)
                }
            }
        }
    }

    private fun assertAscending(keys: List<Int>) {
        for (index in 1..<keys.size) {
            assertTrue(keys[index - 1] < keys[index])
        }
    }
}

/**
 * Tests the public [ConcurrentSkipListMap] on real threads.
 */
class ConcurrentSkipListMapStressTest : SortedMapConcurrencyContractTest() {

    override fun newMap(): TestSortedMap = ConcurrentSkipListMap<Int, String>().asTestSortedMap()
}

/**
 * Tests the public [ConcurrentSkipListSet] on real threads.
 */
class ConcurrentSkipListSetStressTest {

    @Test
    fun anElementIsAddedAndRemovedByOneThreadOnly() {
        val set = ConcurrentSkipListSet<Int>()
        val additions = AtomicInt(0)
        val removals = AtomicInt(0)

        runConcurrently(THREAD_COUNT) {
            repeat(SHARED_KEY_COUNT) { element ->
                if (set.add(element)) {
                    val _ = additions.incrementAndFetch()
                }
            }
        }

        assertEquals(SHARED_KEY_COUNT, additions.load())
        assertEquals(List(SHARED_KEY_COUNT) { it }, set.iterator().asSequence().toList())

        runConcurrently(THREAD_COUNT) {
            repeat(SHARED_KEY_COUNT) { element ->
                if (set.remove(element)) {
                    val _ = removals.incrementAndFetch()
                }
            }
        }

        assertEquals(SHARED_KEY_COUNT, removals.load())
        assertTrue(set.isEmpty())
    }
}
