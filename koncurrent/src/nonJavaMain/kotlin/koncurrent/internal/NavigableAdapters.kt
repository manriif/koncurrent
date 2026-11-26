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

import koncurrent.ConcurrentNavigableMap
import koncurrent.NavigableSet

/**
 * A [PortableNavigableMap] as the public interface. The public map and the views that it
 * returns are all this class, over the views of the port.
 */
internal class NavigableMapAdapter<K : Any, V : Any>(
    private val map: PortableNavigableMap<K, V>,
) : AbstractMutableMap<K, V>(), ConcurrentNavigableMap<K, V> {

    override val size: Int
        get() = map.size

    override val keys: NavigableSet<K>
        get() = navigableKeySet()

    override val values: MutableCollection<V>
        get() = Values()

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = Entries()

    override fun isEmpty(): Boolean = map.isEmpty()

    override fun containsKey(key: K): Boolean = map.containsKey(key)

    override fun containsValue(value: V): Boolean = map.containsValue(value)

    override fun get(key: K): V? = map.get(key)

    @IgnorableReturnValue
    override fun put(key: K, value: V): V? = map.put(key, value)

    @IgnorableReturnValue
    override fun remove(key: K): V? = map.remove(key)

    override fun clear(): Unit = map.clear()

    override fun comparator(): Comparator<in K>? = map.comparator()

    @IgnorableReturnValue
    override fun putIfAbsent(key: K, value: V): V? = map.putIfAbsent(key, value)

    @IgnorableReturnValue
    override fun remove(key: K, value: V): Boolean = map.remove(key, value)

    @IgnorableReturnValue
    override fun replace(key: K, oldValue: V, newValue: V): Boolean =
        map.replace(key, oldValue, newValue)

    @IgnorableReturnValue
    override fun replace(key: K, value: V): V? = map.replace(key, value)

    override fun firstKey(): K = map.firstKey()

    override fun lastKey(): K = map.lastKey()

    override fun lowerEntry(key: K): Map.Entry<K, V>? = map.nearEntry(key, LOWER)

    override fun lowerKey(key: K): K? = map.nearKey(key, LOWER)

    override fun floorEntry(key: K): Map.Entry<K, V>? = map.nearEntry(key, FLOOR)

    override fun floorKey(key: K): K? = map.nearKey(key, FLOOR)

    override fun ceilingEntry(key: K): Map.Entry<K, V>? = map.nearEntry(key, CEILING)

    override fun ceilingKey(key: K): K? = map.nearKey(key, CEILING)

    override fun higherEntry(key: K): Map.Entry<K, V>? = map.nearEntry(key, HIGHER)

    override fun higherKey(key: K): K? = map.nearKey(key, HIGHER)

    override fun firstEntry(): Map.Entry<K, V>? = map.firstEntry()

    override fun lastEntry(): Map.Entry<K, V>? = map.lastEntry()

    @IgnorableReturnValue
    override fun pollFirstEntry(): Map.Entry<K, V>? = map.pollFirstEntry()

    @IgnorableReturnValue
    override fun pollLastEntry(): Map.Entry<K, V>? = map.pollLastEntry()

    override fun descendingMap(): ConcurrentNavigableMap<K, V> =
        NavigableMapAdapter(map.descendingMap())

    override fun navigableKeySet(): NavigableSet<K> = NavigableSetAdapter(map.keySet())

    override fun descendingKeySet(): NavigableSet<K> =
        NavigableSetAdapter(map.descendingMap().keySet())

    override fun subMap(
        fromKey: K,
        fromInclusive: Boolean,
        toKey: K,
        toInclusive: Boolean,
    ): ConcurrentNavigableMap<K, V> = NavigableMapAdapter(
        map.subMap(fromKey, fromInclusive, toKey, toInclusive),
    )

    override fun headMap(toKey: K, inclusive: Boolean): ConcurrentNavigableMap<K, V> =
        NavigableMapAdapter(map.headMap(toKey, inclusive))

    override fun tailMap(fromKey: K, inclusive: Boolean): ConcurrentNavigableMap<K, V> =
        NavigableMapAdapter(map.tailMap(fromKey, inclusive))

    override fun subMap(fromKey: K, toKey: K): ConcurrentNavigableMap<K, V> =
        subMap(fromKey, true, toKey, false)

    override fun headMap(toKey: K): ConcurrentNavigableMap<K, V> = headMap(toKey, false)

    override fun tailMap(fromKey: K): ConcurrentNavigableMap<K, V> = tailMap(fromKey, true)

    // Looks both ways, as the original does. Keys of another type are not equal. Comparing
    // them throws on most platforms. On JS it does not, and such a key may seem to be here,
    // but then the keys of this map are not in the other one.
    @Suppress("UNCHECKED_CAST")
    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }

        if (other !is Map<*, *>) {
            return false
        }

        return try {
            super.equals(other) && all { (other as Map<Any?, Any?>)[it.key] == it.value }
        } catch (_: ClassCastException) {
            false
        } catch (_: NullPointerException) {
            false
        }
    }

    // Stated next to `equals`, though it is the one of the standard class
    @Suppress("RedundantOverride")
    override fun hashCode(): Int = super.hashCode()

    private inner class Values : AbstractMutableCollection<V>() {

        override val size: Int
            get() = map.size

        override fun isEmpty(): Boolean = map.isEmpty()

        override fun contains(element: V): Boolean = map.containsValue(element)

        override fun iterator(): MutableIterator<V> = map.valueIterator()

        override fun add(element: V): Boolean = throw UnsupportedOperationException()

        override fun clear(): Unit = map.clear()
    }

    private inner class Entries : AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {

        override val size: Int
            get() = map.size

        override fun isEmpty(): Boolean = map.isEmpty()

        override fun contains(element: MutableMap.MutableEntry<K, V>): Boolean =
            map.get(element.key) == element.value

        override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> =
            map.entryIterator()

        override fun add(element: MutableMap.MutableEntry<K, V>): Boolean =
            throw UnsupportedOperationException()

        override fun remove(element: MutableMap.MutableEntry<K, V>): Boolean =
            map.remove(element.key, element.value)

        override fun clear(): Unit = map.clear()
    }
}

/**
 * A [PortableNavigableSet] as the public interface. The public set, the key sets of the map
 * and their views are all this class.
 */
internal class NavigableSetAdapter<E : Any>(
    private val set: PortableNavigableSet<E>,
) : AbstractMutableSet<E>(), NavigableSet<E> {

    override val size: Int
        get() = set.size

    override fun isEmpty(): Boolean = set.isEmpty()

    override fun contains(element: E): Boolean = set.contains(element)

    override fun iterator(): MutableIterator<E> = set.iterator()

    @IgnorableReturnValue
    override fun add(element: E): Boolean = set.add(element)

    @IgnorableReturnValue
    override fun remove(element: E): Boolean = set.remove(element)

    override fun clear(): Unit = set.clear()

    override fun comparator(): Comparator<in E>? = set.comparator()

    override fun first(): E = set.first()

    override fun last(): E = set.last()

    override fun lower(element: E): E? = set.near(element, LOWER)

    override fun floor(element: E): E? = set.near(element, FLOOR)

    override fun ceiling(element: E): E? = set.near(element, CEILING)

    override fun higher(element: E): E? = set.near(element, HIGHER)

    @IgnorableReturnValue
    override fun pollFirst(): E? = set.pollFirst()

    @IgnorableReturnValue
    override fun pollLast(): E? = set.pollLast()

    override fun descendingSet(): NavigableSet<E> = NavigableSetAdapter(set.descendingSet())

    override fun descendingIterator(): MutableIterator<E> = set.descendingIterator()

    override fun subSet(
        fromElement: E,
        fromInclusive: Boolean,
        toElement: E,
        toInclusive: Boolean,
    ): NavigableSet<E> = NavigableSetAdapter(
        set.subSet(fromElement, fromInclusive, toElement, toInclusive),
    )

    override fun headSet(toElement: E, inclusive: Boolean): NavigableSet<E> =
        NavigableSetAdapter(set.headSet(toElement, inclusive))

    override fun tailSet(fromElement: E, inclusive: Boolean): NavigableSet<E> =
        NavigableSetAdapter(set.tailSet(fromElement, inclusive))

    // Looks both ways, as the original does, for the same reason as the map
    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }

        if (other !is Set<*>) {
            return false
        }

        return try {
            super.equals(other) && other.containsAll(this)
        } catch (_: ClassCastException) {
            false
        } catch (_: NullPointerException) {
            false
        }
    }

    // Stated next to `equals`, though it is the one of the standard class
    @Suppress("RedundantOverride")
    override fun hashCode(): Int = super.hashCode()
}
