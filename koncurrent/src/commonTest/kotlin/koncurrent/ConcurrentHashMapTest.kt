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

private const val ENTRY_COUNT = 10_000

/**
 * Tests the public [ConcurrentHashMap], whatever backs it on the platform.
 */
open class ConcurrentHashMapTest {

    protected open fun <K : Any, V : Any> newMap(): ConcurrentHashMap<K, V> = ConcurrentHashMap()

    @Test
    fun emptyMapHoldsNothing() {
        val map = newMap<String, Int>()

        assertEquals(0, map.size)
        assertTrue(map.isEmpty())
        assertNull(map["a"])
        assertFalse(map.containsKey("a"))
        assertFalse(map.containsValue(1))
        assertNull(map.remove("a"))
        assertEquals<Map<String, Int>>(emptyMap(), map)
        assertEquals("{}", map.toString())
    }

    @Test
    fun putGetAndRemove() {
        val map = newMap<String, Int>()

        assertNull(map.put("a", 1))
        assertEquals(1, map.put("a", 2))
        map["b"] = 3

        assertEquals(2, map["a"])
        assertEquals(2, map.size)
        assertTrue(map.containsKey("b"))
        assertTrue(map.containsValue(3))

        assertEquals(2, map.remove("a"))
        assertNull(map.remove("a"))
        assertEquals(1, map.size)

        map.clear()
        assertTrue(map.isEmpty())
    }

    @Test
    fun mapGrowsFromAnyInitialCapacity() {
        val maps = listOf<ConcurrentHashMap<Int, Int>>(
            ConcurrentHashMap(),
            ConcurrentHashMap(0),
            ConcurrentHashMap(64),
        )

        for (map in maps) {
            repeat(ENTRY_COUNT) { map[it] = -it }

            assertEquals(ENTRY_COUNT, map.size)
            repeat(ENTRY_COUNT) { assertEquals(-it, map[it]) }
        }
    }

    @Test
    fun negativeInitialCapacityIsRejected() {
        assertFailsWith<IllegalArgumentException> { ConcurrentHashMap<String, Int>(-1) }
    }

    @Test
    fun putAllCopiesTheMappings() {
        val source = (0..<ENTRY_COUNT).associateWith { "v$it" }
        val map = newMap<Int, String>()

        map.putAll(source)

        assertEquals<Map<Int, String>>(source, map)
    }

    @Test
    fun equalsHashCodeAndToStringMatchHashMap() {
        val source = hashMapOf("a" to 1, "b" to 2, "c" to 3)
        val map = newMap<String, Int>()
        map.putAll(source)

        assertEquals<Map<String, Int>>(source, map)
        assertEquals<Map<String, Int>>(map, source)
        assertEquals(source.hashCode(), map.hashCode())
        assertFalse(map == hashMapOf("a" to 1, "b" to 2))

        map.clear()
        map["a"] = 1
        assertEquals("{a=1}", map.toString())
    }

    ///////////////////////////////////////////////////////////////////////////
    // Atomic updates
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun putIfAbsentKeepsTheCurrentValue() {
        val map = newMap<String, Int>()

        assertNull(map.putIfAbsent("a", 1))
        assertEquals(1, map.putIfAbsent("a", 2))
        assertEquals(1, map["a"])
    }

    @Test
    fun removeWithValueOnlyRemovesThatValue() {
        val map = newMap<String, Int>()
        map["a"] = 1

        assertFalse(map.remove("a", 2))
        assertEquals(1, map["a"])
        assertTrue(map.remove("a", 1))
        assertFalse(map.remove("a", 1))
        assertTrue(map.isEmpty())
    }

    @Test
    fun replaceOnlyAppliesToAPresentKey() {
        val map = newMap<String, Int>()

        assertNull(map.replace("a", 1))
        assertFalse(map.replace("a", 1, 2))
        assertTrue(map.isEmpty())

        map["a"] = 1
        assertEquals(1, map.replace("a", 2))
        assertFalse(map.replace("a", 1, 3))
        assertTrue(map.replace("a", 2, 3))
        assertEquals(3, map["a"])
    }

    @Test
    fun computeIfAbsentOnlyComputesAnAbsentKey() {
        val map = newMap<String, Int>()
        var calls = 0

        assertEquals(1, map.computeIfAbsent("a") { calls++; 1 })
        assertEquals(1, map.computeIfAbsent("a") { calls++; 2 })

        assertEquals(1, calls)
        assertEquals(1, map["a"])
    }

    @Test
    fun computeIfPresentOnlyComputesAPresentKey() {
        val map = newMap<String, Int>()

        assertNull(map.computeIfPresent("a") { _, _ -> 1 })
        assertTrue(map.isEmpty())

        map["a"] = 1
        assertEquals(3, map.computeIfPresent("a") { key, value -> value + key.length + 1 })
        assertEquals(3, map["a"])

        assertNull(map.computeIfPresent("a") { _, _ -> null })
        assertFalse(map.containsKey("a"))
    }

    @Test
    fun computeInsertsUpdatesAndRemoves() {
        val map = newMap<String, Int>()

        assertNull(map.compute("a") { _, value -> value })
        assertTrue(map.isEmpty())

        assertEquals(1, map.compute("a") { _, value -> (value ?: 0) + 1 })
        assertEquals(2, map.compute("a") { _, value -> (value ?: 0) + 1 })
        assertEquals(2, map["a"])

        assertNull(map.compute("a") { _, _ -> null })
        assertTrue(map.isEmpty())
    }

    @Test
    fun mergeInsertsCombinesAndRemoves() {
        val map = newMap<String, Int>()

        assertEquals(1, map.merge("a", 1) { old, new -> old + new })
        assertEquals(3, map.merge("a", 2) { old, new -> old + new })
        assertEquals(3, map["a"])

        assertNull(map.merge("a", 1) { _, _ -> null })
        assertTrue(map.isEmpty())
    }

    @Test
    fun failingComputationLeavesTheMapUsable() {
        val map = newMap<String, Int>()

        assertFailsWith<IllegalStateException> { map.computeIfAbsent("a") { error("failed") } }
        assertFalse(map.containsKey("a"))

        assertEquals(1, map.computeIfAbsent("a") { 1 })
    }

    ///////////////////////////////////////////////////////////////////////////
    // Views
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun keysReflectTheMapAndRemoveFromIt() {
        val map = newMap<String, Int>()
        map["a"] = 1
        map["b"] = 2
        val keys: MutableSet<String> = map.keys

        assertEquals(setOf("a", "b"), keys)
        assertEquals(setOf("a", "b").hashCode(), keys.hashCode())
        assertTrue("a" in keys)

        assertTrue(keys.remove("a"))
        assertFalse(map.containsKey("a"))

        map["c"] = 3
        assertEquals(setOf("b", "c"), keys.toSet())
        assertFailsWith<UnsupportedOperationException> { keys.add("d") }
    }

    @Test
    fun valuesAndEntriesReflectTheMap() {
        val map = newMap<String, Int>()
        map["a"] = 1
        map["b"] = 2

        assertEquals(listOf(1, 2), map.values.sorted())
        assertEquals(setOf("a" to 1, "b" to 2), map.entries.map { it.key to it.value }.toSet())

        map.entries.first { it.key == "a" }.setValue(3)
        assertEquals(3, map["a"])

        assertTrue(map.values.remove(2))
        assertFalse(map.containsKey("b"))
    }

    @Test
    fun iterationSurvivesUpdatesOfTheMap() {
        val map = newMap<Int, Int>()
        repeat(ENTRY_COUNT) { map[it] = it }
        var seen = 0

        for (key in map.keys) {
            seen++
            map.remove(key)
            map.remove(ENTRY_COUNT - 1 - key)
        }

        assertTrue(seen in ENTRY_COUNT / 2..ENTRY_COUNT)
        assertTrue(map.isEmpty())
    }

    @Test
    fun iteratorRemovesTheMappingItGave() {
        val map = newMap<Int, Int>()
        repeat(ENTRY_COUNT) { map[it] = it }
        val iterator = map.entries.iterator()

        while (iterator.hasNext()) {
            if (iterator.next().key % 2 == 0) {
                iterator.remove()
            }
        }

        assertEquals(ENTRY_COUNT / 2, map.size)
        assertTrue(map.keys.all { it % 2 == 1 })
    }
}
