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

/**
 * The keys of a [PortableNavigableMap] as a sorted set. It backs the public
 * `ConcurrentSkipListSet`, and the key sets of the sorted map, where the platform has none.
 *
 * [marker] is the value that an added element is mapped to. A key set has none, since a key
 * cannot be added to a map without its value.
 */
internal class PortableNavigableSet<E : Any>(
    private val map: PortableNavigableMap<E, Any>,
    private val marker: Any?,
) {

    val size: Int
        get() = map.size

    fun comparator(): Comparator<in E>? = map.comparator()

    fun isEmpty(): Boolean = map.isEmpty()

    fun contains(element: E): Boolean = map.containsKey(element)

    /**
     * @throws UnsupportedOperationException if this is the key set of a map
     */
    fun add(element: E): Boolean {
        val marker = marker ?: throw UnsupportedOperationException()

        return map.putIfAbsent(element, marker) == null
    }

    fun remove(element: E): Boolean = map.remove(element) != null

    fun clear(): Unit = map.clear()

    fun iterator(): MutableIterator<E> = map.keyIterator()

    fun descendingIterator(): MutableIterator<E> = map.descendingMap().keyIterator()

    /**
     * @throws NoSuchElementException if the set is empty
     */
    fun first(): E = map.firstKey()

    /**
     * @throws NoSuchElementException if the set is empty
     */
    fun last(): E = map.lastKey()

    /**
     * Returns the element that is the closest to [element] in the way that [relation] tells:
     * [LOWER], [FLOOR], [CEILING] or [HIGHER].
     */
    fun near(element: E, relation: Int): E? = map.nearKey(element, relation)

    fun pollFirst(): E? = map.pollFirstEntry()?.key

    fun pollLast(): E? = map.pollLastEntry()?.key

    fun subSet(
        fromElement: E,
        fromInclusive: Boolean,
        toElement: E,
        toInclusive: Boolean,
    ): PortableNavigableSet<E> = PortableNavigableSet(
        map = map.subMap(fromElement, fromInclusive, toElement, toInclusive),
        marker = marker,
    )

    fun headSet(toElement: E, inclusive: Boolean): PortableNavigableSet<E> =
        PortableNavigableSet(map.headMap(toElement, inclusive), marker)

    fun tailSet(fromElement: E, inclusive: Boolean): PortableNavigableSet<E> =
        PortableNavigableSet(map.tailMap(fromElement, inclusive), marker)

    fun descendingSet(): PortableNavigableSet<E> =
        PortableNavigableSet(map.descendingMap(), marker)
}

/**
 * Returns the keys of this map as a sorted set, to which nothing can be added.
 */
@Suppress("UNCHECKED_CAST")
internal fun <K : Any> PortableNavigableMap<K, *>.keySet(): PortableNavigableSet<K> =
    PortableNavigableSet(this as PortableNavigableMap<K, Any>, marker = null)
