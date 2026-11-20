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
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val ENTRY_COUNT = 50_000
private const val COLLIDING_KEY_COUNT = 200
private const val OPERATION_COUNT = 40_000
private const val SEED = 20261116

/**
 * Tests the [MutableMap] contract and the atomic updates of [PortableConcurrentHashMap].
 */
class PortableConcurrentHashMapTest {

    @Test
    fun emptyMapHoldsNothing() {
        val map = PortableConcurrentHashMap<String, Int>()

        assertEquals(0, map.size)
        assertTrue(map.isEmpty())
        assertNull(map["a"])
        assertFalse(map.containsKey("a"))
        assertFalse(map.containsValue(1))
        assertNull(map.remove("a"))
        assertEquals(emptyMap(), map)
        assertEquals("{}", map.toString())
    }

    @Test
    fun putAssociatesAndReturnsThePreviousValue() {
        val map = PortableConcurrentHashMap<String, Int>()

        assertNull(map.put("a", 1))
        assertNull(map.put("b", 2))
        assertEquals(1, map.put("a", 3))

        assertEquals(3, map["a"])
        assertEquals(2, map["b"])
        assertEquals(2, map.size)
        assertFalse(map.isEmpty())
        assertTrue(map.containsKey("a"))
        assertTrue(map.containsValue(2))
        assertFalse(map.containsValue(1))
    }

    @Test
    fun removeDropsTheMappingAndReturnsItsValue() {
        val map = PortableConcurrentHashMap<String, Int>()
        map["a"] = 1
        map["b"] = 2

        assertEquals(1, map.remove("a"))
        assertNull(map.remove("a"))

        assertNull(map["a"])
        assertEquals(2, map["b"])
        assertEquals(1, map.size)
    }

    @Test
    fun mapGrowsThroughResizes() {
        val map = PortableConcurrentHashMap<Int, String>()

        repeat(ENTRY_COUNT) { map[it] = "v$it" }

        assertEquals(ENTRY_COUNT, map.size)
        repeat(ENTRY_COUNT) { assertEquals("v$it", map[it]) }
        assertNull(map[ENTRY_COUNT])

        repeat(ENTRY_COUNT) { assertEquals("v$it", map.remove(it)) }
        assertTrue(map.isEmpty())
    }

    @Test
    fun initialCapacitySizesTheTable() {
        val map = PortableConcurrentHashMap<Int, Int>(initialCapacity = 1_000)

        repeat(1_000) { map[it] = it }

        // 1000 mappings need 2048 bins at most 0.75 full, and no resize happened
        assertEquals(2_048, map.table?.size)
        assertEquals(1_000, map.size)

        assertFailsWith<IllegalArgumentException> { PortableConcurrentHashMap<Int, Int>(-1) }
    }

    @Test
    fun putAllAndClear() {
        val source = (0..<1_000).associateWith { "v$it" }
        val map = PortableConcurrentHashMap<Int, String>()

        map.putAll(source)
        assertEquals(source, map)

        map.clear()
        assertTrue(map.isEmpty())
        assertEquals(0, map.size)
        assertNull(map[1])

        map.putAll(source)
        assertEquals(source, map)
    }

    @Test
    fun equalsHashCodeAndToStringMatchHashMap() {
        val source = hashMapOf("a" to 1, "b" to 2, "c" to 3)
        val map = PortableConcurrentHashMap<String, Int>()
        map.putAll(source)

        assertEquals<Map<String, Int>>(source, map)
        assertEquals<Map<String, Int>>(map, source)
        assertEquals(source.hashCode(), map.hashCode())
        assertFalse(map == hashMapOf("a" to 1, "b" to 2))
        assertFalse(map == hashMapOf("a" to 1, "b" to 2, "c" to 4))

        map.clear()
        map["a"] = 1
        assertEquals("{a=1}", map.toString())
    }

    ///////////////////////////////////////////////////////////////////////////
    // Atomic updates
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun putIfAbsentKeepsTheCurrentValue() {
        val map = PortableConcurrentHashMap<String, Int>()

        assertNull(map.putIfAbsent("a", 1))
        assertEquals(1, map.putIfAbsent("a", 2))
        assertEquals(1, map["a"])
    }

    @Test
    fun removeWithValueOnlyRemovesThatValue() {
        val map = PortableConcurrentHashMap<String, Int>()
        map["a"] = 1

        assertFalse(map.remove("a", 2))
        assertFalse(map.remove("b", 1))
        assertEquals(1, map["a"])

        assertTrue(map.remove("a", 1))
        assertTrue(map.isEmpty())
    }

    @Test
    fun replaceOnlyAppliesToAPresentKey() {
        val map = PortableConcurrentHashMap<String, Int>()
        map["a"] = 1

        assertNull(map.replace("b", 1))
        assertFalse(map.replace("b", 1, 2))
        assertFalse(map.replace("a", 2, 3))
        assertFalse(map.containsKey("b"))

        assertTrue(map.replace("a", 1, 2))
        assertEquals(2, map.replace("a", 3))
        assertEquals(3, map["a"])
        assertEquals(1, map.size)
    }

    @Test
    fun computeIfAbsentOnlyComputesAnAbsentKey() {
        val map = PortableConcurrentHashMap<String, Int>()
        var calls = 0

        assertEquals(1, map.computeIfAbsent("a") { ++calls })
        assertEquals(1, map.computeIfAbsent("a") { ++calls })
        assertEquals(1, calls)

        assertEquals(1, map["a"])
        assertEquals(1, map.size)
    }

    @Test
    fun computeIfPresentOnlyComputesAPresentKey() {
        val map = PortableConcurrentHashMap<String, Int>()
        map["a"] = 1

        val absent = map.computeIfPresent("b") { _, value ->
            value + 1
        }

        val present = map.computeIfPresent("a") { _, value ->
            value + 1
        }

        assertNull(absent)
        assertEquals(2, present)
        assertEquals(2, map["a"])

        val removed = map.computeIfPresent("a") { _, _ ->
            null
        }

        assertNull(removed)
        assertTrue(map.isEmpty())
    }

    @Test
    fun computeInsertsUpdatesAndRemoves() {
        val map = PortableConcurrentHashMap<String, Int>()

        val increment = { _: String, value: Int? ->
            (value ?: 0) + 1
        }

        assertEquals(1, map.compute("a", increment))
        assertEquals(2, map.compute("a", increment))
        assertEquals(2, map["a"])

        val removed = map.compute("a") { _, _ ->
            null
        }

        val absent = map.compute("b") { _, _ ->
            null
        }

        assertNull(removed)
        assertNull(absent)
        assertTrue(map.isEmpty())
    }

    @Test
    fun mergeInsertsCombinesAndRemoves() {
        val map = PortableConcurrentHashMap<String, Int>()

        assertEquals(1, map.merge("a", 1, Int::plus))
        assertEquals(3, map.merge("a", 2, Int::plus))
        assertEquals(3, map["a"])

        val removed = map.merge("a", 1) { _, _ ->
            null
        }

        assertNull(removed)
        assertTrue(map.isEmpty())
    }

    @Test
    fun failingFunctionLeavesTheMapUsable() {
        val map = PortableConcurrentHashMap<String, Int>()
        map["a"] = 1

        assertFailsWith<IllegalStateException> { map.computeIfAbsent("b") { error("b") } }

        assertFailsWith<IllegalStateException> {
            map.compute("a") { _, _ ->
                error("a")
            }
        }

        assertFailsWith<IllegalStateException> {
            map.compute("c") { _, _ ->
                error("c")
            }
        }

        assertFailsWith<IllegalStateException> {
            map.merge("a", 1) { _, _ ->
                error("a")
            }
        }

        assertEquals<Map<String, Int>>(mapOf("a" to 1), map)

        // The bins are not left locked or reserved
        map["a"] = 2
        map["b"] = 3
        map["c"] = 4
        assertEquals<Map<String, Int>>(mapOf("a" to 2, "b" to 3, "c" to 4), map)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Colliding keys
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun keysWithTheSameHashShareATreeBin() {
        val keys = List(COLLIDING_KEY_COUNT) { CollidingKey(it, hash = 7) }

        assertCollidingKeysWork(keys)
    }

    @Test
    fun orderedKeysWithTheSameHashShareATreeBin() {
        val keys = List(COLLIDING_KEY_COUNT) { OrderedCollidingKey(it, hash = 7) }

        assertCollidingKeysWork(keys)
    }

    @Test
    fun treeBinOrdersItsKeysByHash() {
        // Multiples of 64 share the first bin of a table of 64 bins
        val keys = List(COLLIDING_KEY_COUNT) { CollidingKey(it, hash = (it % 16) * 64) }

        assertCollidingKeysWork(keys)
    }

    @Test
    fun treeBinSplitsWhenTheTableGrows() {
        val map = PortableConcurrentHashMap<Any, Int>()
        val keys = List(COLLIDING_KEY_COUNT) { CollidingKey(it, hash = (it % 16) * 64) }

        keys.forEach { map[it] = it.id }
        assertTrue(assertTreeInvariants(map) > 0)

        // Spread keys make the table grow, which splits the tree bin again and again
        repeat(ENTRY_COUNT) { map[it] = it }
        assertTreeInvariants(map)

        assertEquals(COLLIDING_KEY_COUNT + ENTRY_COUNT, map.size)
        keys.forEach { assertEquals(it.id, map[it]) }
        repeat(ENTRY_COUNT) { assertEquals(it, map[it]) }
    }

    @Test
    fun randomOperationsMatchHashMap() {
        val random = Random(SEED)
        val map = PortableConcurrentHashMap<CollidingKey, Int>()
        val model = HashMap<CollidingKey, Int>()

        repeat(OPERATION_COUNT) { operation ->
            val id = random.nextInt(COLLIDING_KEY_COUNT)
            val key = CollidingKey(id, hash = (id % 6) * 64)
            val value = random.nextInt(4)

            when (random.nextInt(10)) {
                0, 1, 2 -> assertEquals(model.put(key, value), map.put(key, value))
                3, 4, 5 -> assertEquals(model.remove(key), map.remove(key))
                6 -> assertEquals(model[key], map[key])
                7 -> assertEquals(model.removeValue(key, value), map.remove(key, value))
                8 -> assertEquals(model.putAbsent(key, value), map.putIfAbsent(key, value))
                else -> assertEquals(model.mergeSum(key, value), map.merge(key, value, Int::plus))
            }

            assertEquals(model.size, map.size)

            if (operation % 500 == 0) {
                assertTreeInvariants(map)
                assertEquals<Map<CollidingKey, Int>>(model, map)
            }
        }

        assertEquals<Map<CollidingKey, Int>>(model, map)
    }

    private fun assertCollidingKeysWork(keys: List<CollidingKey>) {
        val map = PortableConcurrentHashMap<CollidingKey, Int>()

        keys.forEach { assertNull(map.put(it, it.id)) }

        assertTrue(assertTreeInvariants(map) > 0)
        assertEquals(keys.size, map.size)
        keys.forEach { assertEquals(it.id, map[it]) }
        assertNull(map[CollidingKey(-1, keys[0].hashCode())])
        assertEquals(keys.toSet(), map.keys.toSet())

        keys.forEach { assertEquals(it.id, map.put(it, -it.id)) }
        keys.forEach { assertEquals(-it.id, map[it]) }

        // Removes the keys in an order unrelated to their insertion
        keys.shuffled(Random(SEED)).forEachIndexed { index, key ->
            assertEquals(-key.id, map.remove(key))
            assertNull(map[key])
            assertEquals(keys.size - index - 1, map.size)

            if (index % 10 == 0) {
                assertTreeInvariants(map)
            }
        }

        assertTrue(map.isEmpty())
        assertEquals(0, assertTreeInvariants(map))
    }
}

///////////////////////////////////////////////////////////////////////////
// Model
///////////////////////////////////////////////////////////////////////////

private fun <K> HashMap<K, Int>.putAbsent(key: K, value: Int): Int? {
    val current = this[key]

    if (current == null) {
        this[key] = value
    }

    return current
}

private fun <K> HashMap<K, Int>.removeValue(key: K, value: Int): Boolean {
    if (this[key] != value) {
        return false
    }

    remove(key)
    return true
}

private fun <K> HashMap<K, Int>.mergeSum(key: K, value: Int): Int {
    val sum = (this[key] ?: 0) + value

    this[key] = sum
    return sum
}
