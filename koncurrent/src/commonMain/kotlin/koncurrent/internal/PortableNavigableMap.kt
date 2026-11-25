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

private const val BATCH_SIZE = 32

private const val KEY_OUT_OF_RANGE = "key out of range"

/**
 * A sorted map on a [SortedTable], or a part of one: the keys between two bounds, in
 * ascending or in descending order. It backs the public `ConcurrentSkipListMap` and the views
 * that it returns, where the platform has none.
 *
 * A map and its views share one table, so a change of one shows in the others. A view refuses
 * a key that is out of its bounds.
 *
 * [low] and [high] are the bounds, in the ascending order of the table whatever the order of
 * the view. A bound that is `null` is open. The flags tell whether a key that equals its bound
 * is in the view.
 */
internal class PortableNavigableMap<K : Any, V : Any> private constructor(
    private val table: SortedTable<K, V>,
    private val low: K?,
    private val lowInclusive: Boolean,
    private val high: K?,
    private val highInclusive: Boolean,
    private val descending: Boolean,
) {

    val size: Int
        get() = if (isWhole) table.size else table.count(low, lowInclusive, high, highInclusive)

    private val isWhole: Boolean
        get() = low == null && high == null

    /**
     * Creates an empty map, in the order of [comparator] or in the natural order of its keys.
     */
    constructor(comparator: Comparator<in K>?) : this(
        table = SortedTable(comparator),
        low = null,
        lowInclusive = false,
        high = null,
        highInclusive = false,
        descending = false,
    )

    /**
     * Returns the comparator of this view, which is the reverse of the one of the map when the
     * view is descending. Returns `null` for the natural order of the keys.
     */
    fun comparator(): Comparator<in K>? {
        if (!descending) {
            return table.comparator
        }

        return Comparator { first, second -> table.compare(second, first) }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Map
    ///////////////////////////////////////////////////////////////////////////

    fun isEmpty(): Boolean = lowestEntry() == null

    fun get(key: K): V? = if (inBounds(key)) table.get(key) else null

    fun containsKey(key: K): Boolean = get(key) != null

    fun containsValue(value: V): Boolean {
        for (entry in entryIterator()) {
            if (entry.value == value) {
                return true
            }
        }

        return false
    }

    /**
     * @throws IllegalArgumentException if [key] is out of the bounds of this view, as for the
     * other functions that add or replace
     */
    fun put(key: K, value: V): V? {
        checkInBounds(key)
        return table.put(key, value, onlyIfAbsent = false)
    }

    fun putIfAbsent(key: K, value: V): V? {
        checkInBounds(key)
        return table.put(key, value, onlyIfAbsent = true)
    }

    fun remove(key: K): V? = if (inBounds(key)) table.remove(key) else null

    fun remove(key: K, value: V): Boolean = inBounds(key) && table.remove(key, value)

    fun replace(key: K, value: V): V? {
        checkInBounds(key)
        return table.replace(key, value)
    }

    fun replace(key: K, oldValue: V, newValue: V): Boolean {
        checkInBounds(key)
        return table.replace(key, oldValue, newValue)
    }

    fun clear() {
        if (isWhole) {
            table.clear()
            return
        }

        val keys = keyIterator()

        while (keys.hasNext()) {
            val _ = table.remove(keys.next())
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Neighbours, in the order of this view
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Returns the entry that is the closest to [key] in the way that [relation] tells, in the
     * order of this view: [LOWER], [FLOOR], [CEILING] or [HIGHER].
     */
    fun nearEntry(key: K, relation: Int): SortedEntry<K, V>? {
        val ascending = if (descending) HIGHER - relation else relation
        val before = ascending == LOWER || ascending == FLOOR

        if (tooLow(key)) {
            return if (before) null else lowestEntry()
        }

        if (tooHigh(key)) {
            return if (before) highestEntry() else null
        }

        return table.near(key, ascending)?.takeIf { inBounds(it.key) }
    }

    fun nearKey(key: K, relation: Int): K? = nearEntry(key, relation)?.key

    fun firstEntry(): SortedEntry<K, V>? = if (descending) highestEntry() else lowestEntry()

    fun lastEntry(): SortedEntry<K, V>? = if (descending) lowestEntry() else highestEntry()

    /**
     * @throws NoSuchElementException if this view is empty
     */
    fun firstKey(): K = firstEntry()?.key ?: throw NoSuchElementException()

    /**
     * @throws NoSuchElementException if this view is empty
     */
    fun lastKey(): K = lastEntry()?.key ?: throw NoSuchElementException()

    fun pollFirstEntry(): SortedEntry<K, V>? = if (descending) pollHighest() else pollLowest()

    fun pollLastEntry(): SortedEntry<K, V>? = if (descending) pollLowest() else pollHighest()

    private fun lowestEntry(): SortedEntry<K, V>? {
        val entry = when (low) {
            null -> table.first()
            else -> table.near(low, if (lowInclusive) CEILING else HIGHER)
        }

        return entry?.takeUnless { tooHigh(it.key) }
    }

    private fun highestEntry(): SortedEntry<K, V>? {
        val entry = when (high) {
            null -> table.last()
            else -> table.near(high, if (highInclusive) FLOOR else LOWER)
        }

        return entry?.takeUnless { tooLow(it.key) }
    }

    // A view with bounds finds its lowest entry, then removes it if it is still there, as the
    // sub map of the original does
    private fun pollLowest(): SortedEntry<K, V>? {
        if (isWhole) {
            return table.pollFirst()
        }

        while (true) {
            val entry = lowestEntry() ?: return null

            if (table.remove(entry.key, entry.value)) {
                return entry
            }
        }
    }

    private fun pollHighest(): SortedEntry<K, V>? {
        if (isWhole) {
            return table.pollLast()
        }

        while (true) {
            val entry = highestEntry() ?: return null

            if (table.remove(entry.key, entry.value)) {
                return entry
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Views
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Returns the part of this view from [fromKey] to [toKey], in its order.
     *
     * @throws IllegalArgumentException if [fromKey] is after [toKey], or if a key is out of
     * the bounds of this view
     */
    fun subMap(
        fromKey: K,
        fromInclusive: Boolean,
        toKey: K,
        toInclusive: Boolean,
    ): PortableNavigableMap<K, V> = part(fromKey, fromInclusive, toKey, toInclusive)

    fun headMap(toKey: K, inclusive: Boolean): PortableNavigableMap<K, V> =
        part(null, false, toKey, inclusive)

    fun tailMap(fromKey: K, inclusive: Boolean): PortableNavigableMap<K, V> =
        part(fromKey, inclusive, null, false)

    fun descendingMap(): PortableNavigableMap<K, V> =
        PortableNavigableMap(table, low, lowInclusive, high, highInclusive, !descending)

    // [from] and [to] are in the order of this view. A bound that is `null` is the one that
    // this view already has.
    private fun part(
        from: K?,
        fromInclusive: Boolean,
        to: K?,
        toInclusive: Boolean,
    ): PortableNavigableMap<K, V> {
        var newLow = if (descending) to else from
        var newLowInclusive = if (descending) toInclusive else fromInclusive
        var newHigh = if (descending) from else to
        var newHighInclusive = if (descending) fromInclusive else toInclusive

        if (low != null) {
            if (newLow == null) {
                newLow = low
                newLowInclusive = lowInclusive
            } else {
                val order = table.compare(newLow, low)

                require(order > 0 || (order == 0 && (lowInclusive || !newLowInclusive))) {
                    KEY_OUT_OF_RANGE
                }
            }
        }

        if (high != null) {
            if (newHigh == null) {
                newHigh = high
                newHighInclusive = highInclusive
            } else {
                val order = table.compare(newHigh, high)

                require(order < 0 || (order == 0 && (highInclusive || !newHighInclusive))) {
                    KEY_OUT_OF_RANGE
                }
            }
        }

        if (newLow != null && newHigh != null) {
            require(table.compare(newLow, newHigh) <= 0) { "inconsistent range" }
        }

        return PortableNavigableMap(
            table = table,
            low = newLow,
            lowInclusive = newLowInclusive,
            high = newHigh,
            highInclusive = newHighInclusive,
            descending = descending,
        )
    }

    private fun tooLow(key: K): Boolean {
        val low = low ?: return false
        val order = table.compare(key, low)

        return order < 0 || (order == 0 && !lowInclusive)
    }

    private fun tooHigh(key: K): Boolean {
        val high = high ?: return false
        val order = table.compare(key, high)

        return order > 0 || (order == 0 && !highInclusive)
    }

    private fun inBounds(key: K): Boolean = !tooLow(key) && !tooHigh(key)

    private fun checkInBounds(key: K) {
        require(inBounds(key)) { KEY_OUT_OF_RANGE }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Iterators, in the order of this view
    ///////////////////////////////////////////////////////////////////////////

    fun keyIterator(): MutableIterator<K> = ViewIterator { key, _ -> key }

    fun valueIterator(): MutableIterator<V> = ViewIterator { _, value -> value }

    fun entryIterator(): MutableIterator<SortedEntry<K, V>> = ViewIterator(::SortedEntry)

    /**
     * Goes through the entries of the view by batches. Each batch is copied with the lock held
     * once, and starts after the last key of the batch before. So the iterator never fails
     * when the map changes, and it may or may not show what changed since a batch was copied.
     */
    private inner class ViewIterator<T>(private val select: (K, V) -> T) : MutableIterator<T> {

        private val keys = arrayOfNulls<Any>(BATCH_SIZE)
        private val values = arrayOfNulls<Any>(BATCH_SIZE)
        private var batchSize = 0
        private var cursor = 0

        // No batch follows one that the table could not fill, nor a key that is out of bounds
        private var exhausted = false

        private var lastReturned: K? = null

        init {
            if (descending) {
                fill(high, highInclusive)
            } else {
                fill(low, lowInclusive)
            }
        }

        override fun hasNext(): Boolean = cursor < batchSize

        @Suppress("UNCHECKED_CAST")
        override fun next(): T {
            if (cursor >= batchSize) {
                throw NoSuchElementException()
            }

            val key = keys[cursor] as K
            val value = values[cursor] as V

            lastReturned = key

            if (++cursor == batchSize && !exhausted) {
                fill(key, inclusive = false)
            }

            return select(key, value)
        }

        override fun remove() {
            val key = checkNotNull(lastReturned)

            lastReturned = null

            val _ = table.remove(key)
        }

        // Copies the batch that starts at [from], or at the start of the table when it is
        // `null`, and cuts it at the first key that is out of the view
        @Suppress("UNCHECKED_CAST")
        private fun fill(from: K?, inclusive: Boolean) {
            val copied = if (descending) {
                table.copyDescending(from, inclusive, keys, values)
            } else {
                table.copyAscending(from, inclusive, keys, values)
            }

            var kept = 0

            while (kept < copied && inBounds(keys[kept] as K)) {
                kept++
            }

            exhausted = kept < BATCH_SIZE
            batchSize = kept
            cursor = 0
        }
    }
}
