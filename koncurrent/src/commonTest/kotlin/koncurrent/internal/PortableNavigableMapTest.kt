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

import koncurrent.SortedMapContractTest
import koncurrent.TestSortedMap
import koncurrent.TestSortedSet

private fun SortedEntry<Int, String>?.toPair(): Pair<Int, String>? =
    this?.let { it.key to it.value }

/**
 * Returns the port of the sorted map as a sorted map under test.
 */
internal fun PortableNavigableMap<Int, String>.asTestSortedMap(): TestSortedMap {
    val map = this

    return object : TestSortedMap {

        override val size: Int
            get() = map.size

        override fun isEmpty() = map.isEmpty()

        override fun get(key: Int) = map.get(key)

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

        override fun lowerKey(key: Int) = map.nearKey(key, LOWER)

        override fun floorKey(key: Int) = map.nearKey(key, FLOOR)

        override fun ceilingKey(key: Int) = map.nearKey(key, CEILING)

        override fun higherKey(key: Int) = map.nearKey(key, HIGHER)

        override fun lowerEntry(key: Int) = map.nearEntry(key, LOWER).toPair()

        override fun floorEntry(key: Int) = map.nearEntry(key, FLOOR).toPair()

        override fun ceilingEntry(key: Int) = map.nearEntry(key, CEILING).toPair()

        override fun higherEntry(key: Int) = map.nearEntry(key, HIGHER).toPair()

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

        override fun keySet() = map.keySet().asTestSortedSet()

        override fun descendingKeySet() = map.descendingMap().keySet().asTestSortedSet()

        override fun entries(): List<Pair<Int, String>> {
            return map.entryIterator().asSequence().map { it.key to it.value }.toList()
        }

        override fun values() = map.valueIterator().asSequence().toList()

        override fun keyIterator() = map.keyIterator()
    }
}

/**
 * Returns the port of the sorted set as a sorted set under test.
 */
internal fun PortableNavigableSet<Int>.asTestSortedSet(): TestSortedSet {
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

        override fun lower(element: Int) = set.near(element, LOWER)

        override fun floor(element: Int) = set.near(element, FLOOR)

        override fun ceiling(element: Int) = set.near(element, CEILING)

        override fun higher(element: Int) = set.near(element, HIGHER)

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
 * Tests [PortableNavigableMap] on one thread, on the JVM too where it backs nothing. There the
 * same suite runs on the map of the JDK, which checks what the tests expect.
 */
class PortableNavigableMapTest : SortedMapContractTest() {

    override fun newMap(comparator: Comparator<Int>?): TestSortedMap =
        PortableNavigableMap<Int, String>(comparator).asTestSortedMap()
}
