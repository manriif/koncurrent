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
/*
 * Part of the port of the ConcurrentHashMap of JSR-166, which was written by Doug Lea with
 * assistance from members of JCP JSR-166 Expert Group and released to the public domain, as
 * explained at http://creativecommons.org/publicdomain/zero/1.0/
 */
package koncurrent.internal

/**
 * The keys of a map as a set. Like the keys of a standard map, it does not support additions.
 */
internal class KeySetView<K : Any, V : Any>(
    private val map: PortableConcurrentHashMap<K, V>,
) : AbstractMutableSet<K>() {

    override val size: Int
        get() = map.size

    override fun isEmpty(): Boolean = map.isEmpty()

    override fun contains(element: K): Boolean = map.containsKey(element)

    override fun iterator(): MutableIterator<K> = KeyIterator(map)

    @IgnorableReturnValue
    override fun add(element: K): Boolean = throw UnsupportedOperationException()

    @IgnorableReturnValue
    override fun remove(element: K): Boolean = map.remove(element) != null

    override fun clear(): Unit = map.clear()
}

/**
 * The values of a map as a collection. It does not support additions.
 */
internal class ValuesView<K : Any, V : Any>(
    private val map: PortableConcurrentHashMap<K, V>,
) : AbstractMutableCollection<V>() {

    override val size: Int
        get() = map.size

    override fun isEmpty(): Boolean = map.isEmpty()

    override fun contains(element: V): Boolean = map.containsValue(element)

    override fun iterator(): MutableIterator<V> = ValueIterator(map)

    @IgnorableReturnValue
    override fun add(element: V): Boolean = throw UnsupportedOperationException()

    override fun clear(): Unit = map.clear()
}

/**
 * The mappings of a map as a set of entries.
 */
internal class EntrySetView<K : Any, V : Any>(
    private val map: PortableConcurrentHashMap<K, V>,
) : AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {

    override val size: Int
        get() = map.size

    override fun isEmpty(): Boolean = map.isEmpty()

    override fun contains(element: MutableMap.MutableEntry<K, V>): Boolean =
        map[element.key] == element.value

    override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> = EntryIterator(map)

    @IgnorableReturnValue
    override fun add(element: MutableMap.MutableEntry<K, V>): Boolean =
        map.put(element.key, element.value) == null

    @IgnorableReturnValue
    override fun remove(element: MutableMap.MutableEntry<K, V>): Boolean =
        map.remove(element.key, element.value)

    override fun clear(): Unit = map.clear()
}
