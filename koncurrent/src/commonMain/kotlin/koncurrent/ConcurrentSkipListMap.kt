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

/**
 * A [ConcurrentNavigableMap]: a map that keeps its keys in order and that several threads use
 * at once.
 *
 * It finds, adds and removes a key in a time that grows with the logarithm of its size. Its
 * keys are in their natural order, or in the order of the [Comparator] that it is given.
 *
 * Its iterators are weakly consistent. They never throw [ConcurrentModificationException],
 * and they may or may not show the entries added or removed since they were created.
 *
 * Keys and values cannot be `null`.
 *
 * ### Platforms
 *
 * - On the JVM and Android this is `java.util.concurrent.ConcurrentSkipListMap`, a lock-free
 *   skip list. Its size is counted by going through its entries.
 * - Elsewhere the entries are in sorted chunks. Kotlin cannot update a field of a node
 *   atomically, and a node for each entry is what costs there. A change takes one lock. A
 *   read of an entry takes none: it searches, then checks that no thread changed the chunks
 *   meanwhile.
 *
 * @param K the type of the keys
 * @param V the type of the values
 */
public expect class ConcurrentSkipListMap<K : Any, V : Any> : ConcurrentNavigableMap<K, V> {

    override val size: Int
    override val keys: NavigableSet<K>
    override val values: MutableCollection<V>
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>

    /**
     * Creates an empty map in the natural order of its keys. A key that is not [Comparable]
     * makes the map throw [ClassCastException].
     */
    public constructor()

    /**
     * Creates an empty map in the order of [comparator], or in the natural order of its keys
     * when it is `null`.
     */
    public constructor(comparator: Comparator<in K>?)

    /**
     * Creates a map in the natural order of its keys that holds the entries of [map].
     */
    public constructor(map: Map<out K, V>)

    override fun isEmpty(): Boolean

    override fun containsKey(key: K): Boolean

    override fun containsValue(value: V): Boolean

    override fun get(key: K): V?

    @IgnorableReturnValue
    override fun put(key: K, value: V): V?

    @IgnorableReturnValue
    override fun remove(key: K): V?

    override fun putAll(from: Map<out K, V>)

    override fun clear()

    override fun comparator(): Comparator<in K>?

    @IgnorableReturnValue
    override fun putIfAbsent(key: K, value: V): V?

    @IgnorableReturnValue
    override fun remove(key: K, value: V): Boolean

    @IgnorableReturnValue
    override fun replace(key: K, oldValue: V, newValue: V): Boolean

    @IgnorableReturnValue
    override fun replace(key: K, value: V): V?

    override fun firstKey(): K

    override fun lastKey(): K

    override fun lowerEntry(key: K): Map.Entry<K, V>?

    override fun lowerKey(key: K): K?

    override fun floorEntry(key: K): Map.Entry<K, V>?

    override fun floorKey(key: K): K?

    override fun ceilingEntry(key: K): Map.Entry<K, V>?

    override fun ceilingKey(key: K): K?

    override fun higherEntry(key: K): Map.Entry<K, V>?

    override fun higherKey(key: K): K?

    override fun firstEntry(): Map.Entry<K, V>?

    override fun lastEntry(): Map.Entry<K, V>?

    @IgnorableReturnValue
    override fun pollFirstEntry(): Map.Entry<K, V>?

    @IgnorableReturnValue
    override fun pollLastEntry(): Map.Entry<K, V>?

    override fun descendingMap(): ConcurrentNavigableMap<K, V>

    override fun navigableKeySet(): NavigableSet<K>

    override fun descendingKeySet(): NavigableSet<K>

    override fun subMap(
        fromKey: K,
        fromInclusive: Boolean,
        toKey: K,
        toInclusive: Boolean,
    ): ConcurrentNavigableMap<K, V>

    override fun headMap(toKey: K, inclusive: Boolean): ConcurrentNavigableMap<K, V>

    override fun tailMap(fromKey: K, inclusive: Boolean): ConcurrentNavigableMap<K, V>

    override fun subMap(fromKey: K, toKey: K): ConcurrentNavigableMap<K, V>

    override fun headMap(toKey: K): ConcurrentNavigableMap<K, V>

    override fun tailMap(fromKey: K): ConcurrentNavigableMap<K, V>
}
