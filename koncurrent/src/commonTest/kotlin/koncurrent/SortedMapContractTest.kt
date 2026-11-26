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

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SEED = 20261124
private const val LARGE_COUNT = 10_000
private const val OPERATION_COUNT = 30_000
private const val KEY_RANGE = 400
private const val VIEW_PERIOD = 500

/**
 * A sorted map of numbers under test, whatever its type. Its entries are given as pairs.
 */
internal interface TestSortedMap {

    val size: Int

    fun isEmpty(): Boolean

    fun get(key: Int): String?

    fun containsKey(key: Int): Boolean

    fun containsValue(value: String): Boolean

    fun put(key: Int, value: String): String?

    fun putIfAbsent(key: Int, value: String): String?

    fun remove(key: Int): String?

    fun remove(key: Int, value: String): Boolean

    fun replace(key: Int, value: String): String?

    fun replace(key: Int, oldValue: String, newValue: String): Boolean

    fun clear()

    fun firstKey(): Int

    fun lastKey(): Int

    fun lowerKey(key: Int): Int?

    fun floorKey(key: Int): Int?

    fun ceilingKey(key: Int): Int?

    fun higherKey(key: Int): Int?

    fun lowerEntry(key: Int): Pair<Int, String>?

    fun floorEntry(key: Int): Pair<Int, String>?

    fun ceilingEntry(key: Int): Pair<Int, String>?

    fun higherEntry(key: Int): Pair<Int, String>?

    fun firstEntry(): Pair<Int, String>?

    fun lastEntry(): Pair<Int, String>?

    fun pollFirstEntry(): Pair<Int, String>?

    fun pollLastEntry(): Pair<Int, String>?

    fun subMap(from: Int, fromInclusive: Boolean, to: Int, toInclusive: Boolean): TestSortedMap

    fun headMap(to: Int, inclusive: Boolean): TestSortedMap

    fun tailMap(from: Int, inclusive: Boolean): TestSortedMap

    fun descendingMap(): TestSortedMap

    fun keySet(): TestSortedSet

    fun descendingKeySet(): TestSortedSet

    fun entries(): List<Pair<Int, String>>

    fun values(): List<String>

    fun keyIterator(): MutableIterator<Int>
}

/**
 * A sorted set of numbers under test, whatever its type.
 */
internal interface TestSortedSet {

    val size: Int

    fun contains(element: Int): Boolean

    fun add(element: Int): Boolean

    fun remove(element: Int): Boolean

    fun clear()

    fun first(): Int

    fun last(): Int

    fun lower(element: Int): Int?

    fun floor(element: Int): Int?

    fun ceiling(element: Int): Int?

    fun higher(element: Int): Int?

    fun pollFirst(): Int?

    fun pollLast(): Int?

    fun subSet(from: Int, fromInclusive: Boolean, to: Int, toInclusive: Boolean): TestSortedSet

    fun headSet(to: Int, inclusive: Boolean): TestSortedSet

    fun tailSet(from: Int, inclusive: Boolean): TestSortedSet

    fun descendingSet(): TestSortedSet

    fun iterator(): MutableIterator<Int>

    fun descendingIterator(): MutableIterator<Int>

    fun toList(): List<Int> = iterator().asSequence().toList()
}

private fun Map.Entry<Int, String>?.toPair(): Pair<Int, String>? = this?.let { it.key to it.value }

/**
 * Returns a public map as a sorted map under test.
 */
internal fun ConcurrentNavigableMap<Int, String>.asTestSortedMap(): TestSortedMap {
    val map = this

    return object : TestSortedMap {

        override val size: Int
            get() = map.size

        override fun isEmpty() = map.isEmpty()

        override fun get(key: Int) = map[key]

        override fun containsKey(key: Int) = map.containsKey(key)

        override fun containsValue(value: String) = map.containsValue(value)

        override fun put(key: Int, value: String) = map.put(key, value)

        override fun putIfAbsent(key: Int, value: String) = map.putIfAbsent(key, value)

        override fun remove(key: Int) = map.remove(key)

        override fun remove(key: Int, value: String) = map.remove(key, value)

        override fun replace(key: Int, value: String) = map.replace(key, value)

        override fun replace(key: Int, oldValue: String, newValue: String) =
            map.replace(key, oldValue, newValue)

        override fun clear() = map.clear()

        override fun firstKey() = map.firstKey()

        override fun lastKey() = map.lastKey()

        override fun lowerKey(key: Int) = map.lowerKey(key)

        override fun floorKey(key: Int) = map.floorKey(key)

        override fun ceilingKey(key: Int) = map.ceilingKey(key)

        override fun higherKey(key: Int) = map.higherKey(key)

        override fun lowerEntry(key: Int) = map.lowerEntry(key).toPair()

        override fun floorEntry(key: Int) = map.floorEntry(key).toPair()

        override fun ceilingEntry(key: Int) = map.ceilingEntry(key).toPair()

        override fun higherEntry(key: Int) = map.higherEntry(key).toPair()

        override fun firstEntry() = map.firstEntry().toPair()

        override fun lastEntry() = map.lastEntry().toPair()

        override fun pollFirstEntry() = map.pollFirstEntry().toPair()

        override fun pollLastEntry() = map.pollLastEntry().toPair()

        override fun subMap(from: Int, fromInclusive: Boolean, to: Int, toInclusive: Boolean) =
            map.subMap(from, fromInclusive, to, toInclusive).asTestSortedMap()

        override fun headMap(to: Int, inclusive: Boolean) =
            map.headMap(to, inclusive).asTestSortedMap()

        override fun tailMap(from: Int, inclusive: Boolean) =
            map.tailMap(from, inclusive).asTestSortedMap()

        override fun descendingMap() = map.descendingMap().asTestSortedMap()

        override fun keySet() = map.navigableKeySet().asTestSortedSet()

        override fun descendingKeySet() = map.descendingKeySet().asTestSortedSet()

        override fun entries(): List<Pair<Int, String>> {
            return map.entries.iterator().asSequence().map { it.key to it.value }.toList()
        }

        override fun values() = map.values.iterator().asSequence().toList()

        override fun keyIterator() = map.keys.iterator()
    }
}

/**
 * Returns a public set as a sorted set under test.
 */
internal fun NavigableSet<Int>.asTestSortedSet(): TestSortedSet {
    val set = this

    return object : TestSortedSet {

        override val size: Int
            get() = set.size

        override fun contains(element: Int) = set.contains(element)

        override fun add(element: Int) = set.add(element)

        override fun remove(element: Int) = set.remove(element)

        override fun clear() = set.clear()

        override fun first() = set.first()

        override fun last() = set.last()

        override fun lower(element: Int) = set.lower(element)

        override fun floor(element: Int) = set.floor(element)

        override fun ceiling(element: Int) = set.ceiling(element)

        override fun higher(element: Int) = set.higher(element)

        override fun pollFirst() = set.pollFirst()

        override fun pollLast() = set.pollLast()

        override fun subSet(from: Int, fromInclusive: Boolean, to: Int, toInclusive: Boolean) =
            set.subSet(from, fromInclusive, to, toInclusive).asTestSortedSet()

        override fun headSet(to: Int, inclusive: Boolean) =
            set.headSet(to, inclusive).asTestSortedSet()

        override fun tailSet(from: Int, inclusive: Boolean) =
            set.tailSet(from, inclusive).asTestSortedSet()

        override fun descendingSet() = set.descendingSet().asTestSortedSet()

        override fun iterator() = set.iterator()

        override fun descendingIterator() = set.descendingIterator()
    }
}

/**
 * Tests what one thread sees of a sorted map, of its views and of its key sets.
 */
abstract class SortedMapContractTest {

    internal abstract fun newMap(comparator: Comparator<Int>? = null): TestSortedMap

    private fun mapOf(vararg keys: Int): TestSortedMap {
        val map = newMap()

        keys.forEach { assertNull(map.put(it, "v$it")) }
        return map
    }

    private fun TestSortedMap.keys(): List<Int> = entries().map { it.first }

    @Test
    fun emptyMapHoldsNothing() {
        val map = newMap()

        assertEquals(0, map.size)
        assertTrue(map.isEmpty())
        assertNull(map.get(1))
        assertFalse(map.containsKey(1))
        assertFalse(map.containsValue("a"))
        assertNull(map.remove(1))
        assertNull(map.firstEntry())
        assertNull(map.lastEntry())
        assertNull(map.pollFirstEntry())
        assertNull(map.pollLastEntry())
        assertNull(map.lowerKey(1))
        assertNull(map.ceilingEntry(1))
        assertFailsWith<NoSuchElementException> { map.firstKey() }
        assertFailsWith<NoSuchElementException> { map.lastKey() }
        assertEquals(emptyList(), map.entries())
        assertEquals(emptyList(), map.keySet().toList())
        assertFailsWith<NoSuchElementException> { map.keyIterator().next() }
    }

    @Test
    fun entriesStayInTheOrderOfTheirKeys() {
        val map = mapOf(5, 1, 9, 3, 7)

        assertEquals(5, map.size)
        assertEquals(listOf(1, 3, 5, 7, 9), map.keys())
        assertEquals(listOf("v1", "v3", "v5", "v7", "v9"), map.values())
        assertEquals(1 to "v1", map.firstEntry())
        assertEquals(9 to "v9", map.lastEntry())
        assertEquals(1, map.firstKey())
        assertEquals(9, map.lastKey())
        assertTrue(map.containsKey(7))
        assertTrue(map.containsValue("v7"))
        assertFalse(map.containsValue("v8"))
    }

    @Test
    fun updatesTellWhatTheyDid() {
        val map = mapOf(1)

        assertEquals("v1", map.put(1, "a"))
        assertEquals("a", map.putIfAbsent(1, "b"))
        assertNull(map.putIfAbsent(2, "b"))
        assertEquals("a", map.get(1))

        assertFalse(map.remove(1, "x"))
        assertTrue(map.remove(1, "a"))
        assertFalse(map.containsKey(1))

        assertNull(map.replace(1, "c"))
        assertEquals("b", map.replace(2, "c"))
        assertFalse(map.replace(2, "x", "d"))
        assertTrue(map.replace(2, "c", "d"))
        assertEquals(listOf(2 to "d"), map.entries())

        assertEquals("d", map.remove(2))
        assertTrue(map.isEmpty())
    }

    @Test
    fun neighboursOfAKeyAreFound() {
        val map = mapOf(10, 20, 30)

        assertEquals(10, map.lowerKey(20))
        assertEquals(20, map.floorKey(20))
        assertEquals(20, map.ceilingKey(20))
        assertEquals(30, map.higherKey(20))

        assertEquals(20, map.lowerKey(25))
        assertEquals(20, map.floorKey(25))
        assertEquals(30, map.ceilingKey(25))
        assertEquals(30, map.higherKey(25))

        assertNull(map.lowerKey(10))
        assertNull(map.floorKey(5))
        assertNull(map.ceilingKey(35))
        assertNull(map.higherKey(30))
        assertEquals(30, map.lowerKey(99))
        assertEquals(10, map.higherKey(-99))

        assertEquals(10 to "v10", map.lowerEntry(20))
        assertEquals(20 to "v20", map.floorEntry(20))
        assertEquals(20 to "v20", map.ceilingEntry(20))
        assertEquals(30 to "v30", map.higherEntry(20))
    }

    @Test
    fun pollTakesTheEnds() {
        val map = mapOf(2, 1, 3, 4)

        assertEquals(1 to "v1", map.pollFirstEntry())
        assertEquals(4 to "v4", map.pollLastEntry())
        assertEquals(listOf(2, 3), map.keys())
        assertEquals(2 to "v2", map.pollFirstEntry())
        assertEquals(3 to "v3", map.pollFirstEntry())
        assertNull(map.pollLastEntry())
    }

    @Test
    fun comparatorOrdersTheKeys() {
        val map = newMap(compareByDescending { it })

        listOf(1, 3, 2).forEach { assertNull(map.put(it, "v$it")) }

        assertEquals(listOf(3, 2, 1), map.keys())
        assertEquals(3, map.firstKey())
        assertEquals(1, map.lastKey())
        assertEquals(3, map.lowerKey(2))
        assertEquals(1, map.higherKey(2))
        assertEquals(listOf(3, 2), map.headMap(1, false).keys())
    }

    @Test
    fun mapGrowsAndShrinksThroughManyKeys() {
        val map = newMap()
        val random = Random(SEED)
        val keys = List(LARGE_COUNT) { it }.shuffled(random)

        keys.forEach { assertNull(map.put(it, "v$it")) }

        assertEquals(LARGE_COUNT, map.size)
        assertEquals(List(LARGE_COUNT) { it }, map.keys())
        assertEquals(LARGE_COUNT - 1, map.lastKey())

        keys.filter { it % 2 == 1 }.forEach { assertEquals("v$it", map.remove(it)) }

        assertEquals(LARGE_COUNT / 2, map.size)
        assertEquals(List(LARGE_COUNT / 2) { it * 2 }, map.keys())
        assertEquals(4_000, map.floorKey(4_001))
        assertEquals(4_002, map.higherKey(4_001))
        assertEquals("v6000", map.get(6_000))

        keys.filter { it % 2 == 0 }.forEach { assertEquals("v$it", map.remove(it)) }

        assertTrue(map.isEmpty())
        assertNull(map.put(7, "v7"))
        assertEquals(listOf(7), map.keys())
    }

    @Test
    fun subMapIsAViewBetweenItsBounds() {
        val map = mapOf(10, 20, 30, 40, 50)
        val part = map.subMap(20, true, 40, false)

        assertEquals(listOf(20, 30), part.keys())
        assertEquals(2, part.size)
        assertEquals(20, part.firstKey())
        assertEquals(30, part.lastKey())
        assertNull(part.get(40))
        assertFalse(part.containsKey(10))
        assertNull(part.remove(50))

        // The closest keys stop at the bounds
        assertNull(part.lowerKey(20))
        assertEquals(30, part.lowerKey(99))
        assertEquals(20, part.ceilingKey(-99))
        assertNull(part.higherKey(30))
        assertNull(part.ceilingKey(40))

        // A change of one shows in the other
        assertNull(part.put(25, "v25"))
        assertEquals("v25", map.get(25))
        assertEquals("v30", map.remove(30))
        assertEquals(listOf(20, 25), part.keys())

        assertFailsWith<IllegalArgumentException> { part.put(40, "x") }
        assertFailsWith<IllegalArgumentException> { part.putIfAbsent(5, "x") }

        assertEquals(20 to "v20", part.pollFirstEntry())
        assertEquals(25 to "v25", part.pollLastEntry())
        assertNull(part.pollFirstEntry())
        assertTrue(part.isEmpty())
        assertEquals(listOf(10, 40, 50), map.keys())
    }

    @Test
    fun headMapAndTailMapAreOpenOnOneSide() {
        val map = mapOf(10, 20, 30, 40)

        assertEquals(listOf(10, 20), map.headMap(30, false).keys())
        assertEquals(listOf(10, 20, 30), map.headMap(30, true).keys())
        assertEquals(listOf(30, 40), map.tailMap(30, true).keys())
        assertEquals(listOf(40), map.tailMap(30, false).keys())
        assertEquals(emptyList(), map.tailMap(40, false).keys())

        val tail = map.tailMap(25, true)

        tail.clear()
        assertEquals(listOf(10, 20), map.keys())
        assertEquals(0, tail.size)
    }

    @Test
    fun viewsOfAViewStayInItsBounds() {
        val map = mapOf(10, 20, 30, 40, 50)
        val part = map.subMap(20, true, 40, true)

        assertEquals(listOf(30, 40), part.tailMap(30, true).keys())
        assertEquals(listOf(20), part.headMap(30, false).keys())
        assertEquals(listOf(30), part.subMap(25, true, 35, true).keys())

        assertFailsWith<IllegalArgumentException> { part.subMap(10, true, 30, true) }
        assertFailsWith<IllegalArgumentException> { part.tailMap(50, true) }
        assertFailsWith<IllegalArgumentException> { part.headMap(10, true) }
        assertFailsWith<IllegalArgumentException> { map.subMap(40, true, 20, true) }
    }

    @Test
    fun descendingMapReversesEverything() {
        val map = mapOf(10, 20, 30, 40)
        val reverse = map.descendingMap()

        assertEquals(listOf(40, 30, 20, 10), reverse.keys())
        assertEquals(40, reverse.firstKey())
        assertEquals(10, reverse.lastKey())
        assertEquals(30, reverse.lowerKey(20))
        assertEquals(10, reverse.higherKey(20))
        // In the reverse order, the keys before 35 are the greater ones
        assertEquals(40, reverse.floorKey(35))
        assertEquals(30, reverse.ceilingKey(35))

        // The bounds of a view are given in its order
        assertEquals(listOf(40, 30), reverse.headMap(20, false).keys())
        assertEquals(listOf(20, 10), reverse.tailMap(20, true).keys())
        assertEquals(listOf(30, 20), reverse.subMap(30, true, 10, false).keys())
        assertFailsWith<IllegalArgumentException> { reverse.subMap(10, true, 30, true) }

        assertEquals(40 to "v40", reverse.pollFirstEntry())
        assertEquals(10 to "v10", reverse.pollLastEntry())
        assertEquals(listOf(20, 30), map.keys())
        assertEquals(listOf(20, 30), reverse.descendingMap().keys())
        assertEquals(listOf(30, 20), map.descendingKeySet().toList())
    }

    @Test
    fun keySetIsASortedSetOfTheKeys() {
        val map = mapOf(10, 20, 30, 40)
        val keys = map.keySet()

        assertEquals(4, keys.size)
        assertEquals(listOf(10, 20, 30, 40), keys.toList())
        assertEquals(listOf(40, 30, 20, 10), keys.descendingIterator().asSequence().toList())
        assertEquals(10, keys.first())
        assertEquals(40, keys.last())
        assertEquals(10, keys.lower(20))
        assertEquals(20, keys.floor(25))
        assertEquals(30, keys.ceiling(25))
        assertEquals(30, keys.higher(20))
        assertTrue(keys.contains(30))
        assertFailsWith<UnsupportedOperationException> { keys.add(5) }

        assertEquals(listOf(20, 30), keys.subSet(20, true, 40, false).toList())
        assertEquals(listOf(10), keys.headSet(20, false).toList())
        assertEquals(listOf(30, 40), keys.tailSet(20, false).toList())
        assertEquals(listOf(40, 30, 20, 10), keys.descendingSet().toList())

        // Removing a key removes its entry
        assertTrue(keys.remove(20))
        assertFalse(keys.remove(20))
        assertEquals(10, keys.pollFirst())
        assertEquals(40, keys.pollLast())
        assertEquals(listOf(30 to "v30"), map.entries())

        keys.clear()
        assertTrue(map.isEmpty())
        assertNull(keys.pollFirst())
        assertFailsWith<NoSuchElementException> { keys.first() }
    }

    @Test
    fun iteratorsRemoveAndGoOnWhenTheMapChanges() {
        val map = newMap()

        repeat(200) { assertNull(map.put(it, "v$it")) }

        val iterator = map.keyIterator()

        assertFailsWith<IllegalStateException> { iterator.remove() }

        // Removes the even keys, while the map loses and gets other keys
        while (iterator.hasNext()) {
            val key = iterator.next()

            if (key % 2 == 0) {
                iterator.remove()
            }

            // The iterator may or may not show these two changes. Both leave the same map.
            if (key == 50) {
                assertEquals("v150", map.remove(150))
                assertNull(map.put(1_001, "v1001"))
            }
        }

        assertEquals(101, map.size)
        assertFalse(map.containsKey(0))
        assertTrue(map.containsKey(1))
        assertFalse(map.containsKey(150))
        assertEquals(1_001, map.lastKey())
    }

    /**
     * Runs random operations on the map and on a plain map that is sorted on demand. Now and
     * then it takes a view with random bounds, in either order, and compares what it gives.
     */
    @Test
    fun randomOperationsMatchAPlainMap() {
        val map = newMap()
        val model = HashMap<Int, String>()
        val random = Random(SEED)

        repeat(OPERATION_COUNT) { step ->
            val key = random.nextInt(KEY_RANGE)
            val value = "v$step"

            when (random.nextInt(12)) {
                0, 1, 2 -> assertEquals(model.put(key, value), map.put(key, value))
                3 -> assertEquals(model.remove(key), map.remove(key))
                4 -> assertEquals(model[key], map.get(key))

                5 -> {
                    val expected = model[key]

                    assertEquals(expected, map.putIfAbsent(key, value))

                    if (expected == null) {
                        model[key] = value
                    }
                }

                6 -> assertEquals(model.keys.filter { it < key }.maxOrNull(), map.lowerKey(key))
                7 -> assertEquals(model.keys.filter { it <= key }.maxOrNull(), map.floorKey(key))
                8 -> assertEquals(model.keys.filter { it >= key }.minOrNull(), map.ceilingKey(key))
                9 -> assertEquals(model.keys.filter { it > key }.minOrNull(), map.higherKey(key))

                10 -> {
                    val first = model.keys.minOrNull()

                    assertEquals(first?.let { it to model.getValue(it) }, map.pollFirstEntry())
                    model.remove(first)
                }

                else -> {
                    val last = model.keys.maxOrNull()

                    assertEquals(last?.let { it to model.getValue(it) }, map.pollLastEntry())
                    model.remove(last)
                }
            }

            assertEquals(model.size, map.size)

            if (step % VIEW_PERIOD == 0) {
                val sorted = model.entries.sortedBy { it.key }

                assertEquals(sorted.map { it.key to it.value }, map.entries())
                assertViewMatches(map, model, random)
            }
        }
    }

    // Compares a view of the map, with random bounds and in a random order, with what the
    // plain map holds between those bounds
    private fun assertViewMatches(map: TestSortedMap, model: Map<Int, String>, random: Random) {
        val low = random.nextInt(KEY_RANGE)
        val high = low + random.nextInt(KEY_RANGE - low)
        val lowInclusive = random.nextBoolean()
        val highInclusive = random.nextBoolean()
        val descending = random.nextBoolean()

        val ascendingView = map.subMap(low, lowInclusive, high, highInclusive)
        val view = if (descending) ascendingView.descendingMap() else ascendingView

        val inside = model.keys
            .filter { it > low || (it == low && lowInclusive) }
            .filter { it < high || (it == high && highInclusive) }
            .sorted()

        val expected = if (descending) inside.reversed() else inside

        assertEquals(expected.map { it to model.getValue(it) }, view.entries())
        assertEquals(expected.size, view.size)
        assertEquals(expected.isEmpty(), view.isEmpty())
        assertEquals(expected.firstOrNull(), view.firstEntry()?.first)
        assertEquals(expected.lastOrNull(), view.lastEntry()?.first)
        assertEquals(expected, view.keySet().toList())
        assertEquals(expected.reversed(), view.descendingKeySet().toList())

        val key = random.nextInt(KEY_RANGE)

        // In the order of the view, what is before a key is what comes earlier in it
        val before = expected.takeWhile { if (descending) it > key else it < key }
        val after = expected.dropWhile { if (descending) it >= key else it <= key }
        val present = key in inside

        assertEquals(before.lastOrNull(), view.lowerKey(key))
        assertEquals(after.firstOrNull(), view.higherKey(key))
        assertEquals(if (present) key else before.lastOrNull(), view.floorKey(key))
        assertEquals(if (present) key else after.firstOrNull(), view.ceilingKey(key))
        assertEquals(if (present) model.getValue(key) else null, view.get(key))
    }
}

/**
 * Tests the public [ConcurrentSkipListMap] on one thread.
 */
class ConcurrentSkipListMapTest : SortedMapContractTest() {

    override fun newMap(comparator: Comparator<Int>?): TestSortedMap =
        ConcurrentSkipListMap<Int, String>(comparator).asTestSortedMap()

    @Test
    fun constructorCopiesAMap() {
        val map = ConcurrentSkipListMap(mapOf("b" to 2, "a" to 1, "c" to 3))

        assertEquals(listOf("a", "b", "c"), map.keys.toList())
        assertEquals(listOf(1, 2, 3), map.values.toList())
        assertNull(map.comparator())
        assertEquals("{a=1, b=2, c=3}", map.toString())
    }

    @Test
    fun mapComparesAsAMap() {
        val map = ConcurrentSkipListMap<String, Int>()

        map["b"] = 2
        map["a"] = 1

        assertEquals(mapOf("a" to 1, "b" to 2), map)
        assertEquals(map, mapOf("a" to 1, "b" to 2))
        assertEquals(mapOf("a" to 1, "b" to 2).hashCode(), map.hashCode())
        assertNotEquals(mapOf("a" to 1), map)
        assertFalse(map.equals(mapOf(1 to 1, 2 to 2)))
    }

    @Test
    fun entriesAreCopiesThatCannotChangeTheMap() {
        val map = ConcurrentSkipListMap<String, Int>()

        map["a"] = 1

        val first = checkNotNull(map.firstEntry())
        val listed = map.entries.first()

        assertEquals("a", first.key)
        assertEquals(1, first.value)
        assertEquals("a=1", first.toString())
        assertFailsWith<UnsupportedOperationException> { listed.setValue(2) }
        assertEquals(1, map["a"])

        assertTrue(map.entries.remove(listed))
        assertTrue(map.isEmpty())
    }

    @Test
    fun keysThatCannotBeComparedAreRefused() {
        val map = ConcurrentSkipListMap<Name, Int>()

        assertFailsWith<ClassCastException> { map[Name("a")] = 1 }
        assertTrue(map.isEmpty())
    }

    @Test
    fun valuesAndEntriesRemoveFromTheMap() {
        val map = ConcurrentSkipListMap(mapOf(1 to "a", 2 to "b", 3 to "a"))

        assertTrue(map.values.remove("a"))
        assertEquals(listOf(2, 3), map.keys.toList())

        val values = map.values.iterator()

        assertEquals("b", values.next())
        values.remove()
        assertEquals(listOf(3), map.keys.toList())

        map.putAll(mapOf(5 to "e", 4 to "d"))
        assertEquals(listOf("a", "d", "e"), map.values.toList())
        assertEquals(3, map.entries.size)
    }
}

/**
 * Tests the public [ConcurrentSkipListSet] on one thread.
 */
class ConcurrentSkipListSetTest {

    @Test
    fun elementsStayInOrderAndOnce() {
        val set = ConcurrentSkipListSet(listOf(3, 1, 2, 3))

        assertFalse(set.add(1))
        assertTrue(set.add(0))
        assertEquals(listOf(0, 1, 2, 3), set.toList())
        assertEquals(4, set.size)
        assertEquals(0, set.first())
        assertEquals(3, set.last())
        assertNull(set.comparator())
        assertEquals("[0, 1, 2, 3]", set.toString())

        assertTrue(set.remove(2))
        assertFalse(set.remove(2))
        assertFalse(2 in set)
        assertEquals(setOf(0, 1, 3), set)
        assertEquals(setOf(0, 1, 3).hashCode(), set.hashCode())
        assertFalse(set.equals(setOf("a", "b", "c")))
    }

    @Test
    fun neighboursAndEndsAreFound() {
        val set = ConcurrentSkipListSet<Int>()

        set.addAll(listOf(10, 20, 30))

        assertEquals(10, set.lower(20))
        assertEquals(20, set.floor(20))
        assertEquals(30, set.ceiling(25))
        assertNull(set.higher(30))

        assertEquals(10, set.pollFirst())
        assertEquals(30, set.pollLast())
        assertEquals(listOf(20), set.toList())
        assertEquals(20, set.pollFirst())
        assertNull(set.pollFirst())
        assertFailsWith<NoSuchElementException> { set.first() }
    }

    @Test
    fun viewsReadAndChangeTheSet() {
        val set = ConcurrentSkipListSet(listOf(10, 20, 30, 40))

        assertEquals(listOf(20, 30), set.subSet(20, 40).toList())
        assertEquals(listOf(10), set.headSet(20).toList())
        assertEquals(listOf(20, 30, 40), set.tailSet(20).toList())
        assertEquals(listOf(20, 30, 40), set.subSet(20, true, 40, true).toList())
        assertEquals(listOf(40, 30, 20, 10), set.descendingSet().toList())
        assertEquals(listOf(40, 30, 20, 10), set.descendingIterator().asSequence().toList())

        val tail = set.tailSet(30)

        assertTrue(tail.add(35))
        assertFailsWith<IllegalArgumentException> { tail.add(5) }
        assertTrue(35 in set)

        tail.clear()
        assertEquals(listOf(10, 20), set.toList())
    }

    @Test
    fun comparatorOrdersTheElements() {
        val set = ConcurrentSkipListSet<String>(compareBy { it.length })

        assertTrue(set.add("ccc"))
        assertTrue(set.add("a"))
        assertTrue(set.add("bb"))

        // An element that compares as equal to another one is the same element
        assertFalse(set.add("zz"))
        assertEquals(listOf("a", "bb", "ccc"), set.toList())
    }

    @Test
    fun elementsThatCannotBeComparedAreRefused() {
        val set = ConcurrentSkipListSet<Name>()

        assertFailsWith<ClassCastException> { set.add(Name("a")) }
    }
}
