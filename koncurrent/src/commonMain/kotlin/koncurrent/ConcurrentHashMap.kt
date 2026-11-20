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
 * A hash map that any number of threads can read and update at the same time.
 *
 * Lookups never block. An update only locks the bin of its key, so updates of different keys
 * rarely wait for each other. Keys and values cannot be `null`.
 *
 * The functions that update one key are atomic: [put], [remove], [putIfAbsent], [replace],
 * [computeIfAbsent], [computeIfPresent], [compute] and [merge]. The bulk functions are not.
 * Other threads may see a [putAll] or a [clear] half done. [size], [isEmpty] and
 * [containsValue] give a transient state while other threads update the map.
 *
 * [keys], [values] and [entries] are views of the map. Their iterators are weakly consistent.
 * They never throw [ConcurrentModificationException]. They visit once each element that
 * existed when they were created, and may or may not reflect later updates.
 *
 * The standard `getOrPut` is not atomic on this map. Use [computeIfAbsent].
 *
 * A function given to [computeIfAbsent], [computeIfPresent], [compute] or [merge] must not
 * update this map. Other updates of the same bin wait for it, so it should be short.
 *
 * ### Platforms
 *
 * - On the JVM this is `java.util.concurrent.ConcurrentHashMap`.
 * - On Android this wraps the platform map from API 24, and a port of it below. The platform
 *   map has no atomic computation before API 24.
 * - Elsewhere this is a port of the `ConcurrentHashMap` of JSR-166.
 *
 * @param K the type of the keys
 * @param V the type of the values
 */
public expect class ConcurrentHashMap<K : Any, V : Any> : MutableMap<K, V> {

    override val size: Int

    /**
     * A view of the keys of this map. It supports removals, which remove the mapping of the
     * key. It does not support additions.
     *
     * Common code cannot name the type of this view. Declare it as a [MutableSet].
     */
    override val keys: KeySetView<K, V>

    override val values: MutableCollection<V>
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>

    /**
     * Creates an empty map with the default initial table size of 16 bins.
     */
    public constructor()

    /**
     * Creates an empty map that can hold [initialCapacity] mappings without resizing.
     *
     * @throws IllegalArgumentException if [initialCapacity] is negative
     */
    public constructor(initialCapacity: Int)

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

    /**
     * Maps [key] to [value] unless the key already has a value. The check and the update are
     * one atomic operation.
     *
     * @return the value that the key already had, or `null` if [value] was stored
     */
    @IgnorableReturnValue
    public fun putIfAbsent(key: K, value: V): V?

    /**
     * Removes the mapping of [key] only if its value equals [value]. The check and the removal
     * are one atomic operation.
     *
     * @return whether the mapping was removed
     */
    @IgnorableReturnValue
    public fun remove(key: K, value: V): Boolean

    /**
     * Maps [key] to [newValue] only if its value equals [oldValue]. The check and the update
     * are one atomic operation.
     *
     * @return whether the value was replaced
     */
    @IgnorableReturnValue
    public fun replace(key: K, oldValue: V, newValue: V): Boolean

    /**
     * Maps [key] to [value] only if the key already has a value. The check and the update are
     * one atomic operation.
     *
     * @return the value that was replaced, or `null` if the key had none
     */
    @IgnorableReturnValue
    public fun replace(key: K, value: V): V?

    /**
     * The view of the keys of a [ConcurrentHashMap]. See [ConcurrentHashMap.keys].
     *
     * Its iterators are weakly consistent, as those of the map.
     *
     * @param K the type of the keys
     * @param V the type of the values of the map
     */
    public class KeySetView<K : Any, V : Any> : MutableSet<K> {

        override val size: Int

        override fun isEmpty(): Boolean

        override fun contains(element: K): Boolean

        override fun containsAll(elements: Collection<K>): Boolean

        override fun iterator(): MutableIterator<K>

        /**
         * Not supported. A key cannot be added without a value.
         *
         * @throws UnsupportedOperationException always
         */
        @IgnorableReturnValue
        override fun add(element: K): Boolean

        /**
         * Not supported. A key cannot be added without a value.
         *
         * @throws UnsupportedOperationException always
         */
        @IgnorableReturnValue
        override fun addAll(elements: Collection<K>): Boolean

        @IgnorableReturnValue
        override fun remove(element: K): Boolean

        @IgnorableReturnValue
        override fun removeAll(elements: Collection<K>): Boolean

        @IgnorableReturnValue
        override fun retainAll(elements: Collection<K>): Boolean

        override fun clear()
    }
}

///////////////////////////////////////////////////////////////////////////
// Atomic computations
///////////////////////////////////////////////////////////////////////////

// These are extensions because the Java members take java.util.function types.

/**
 * Returns the value of [key]. If the key has none, calls [mappingFunction], maps the key to
 * its result and returns it.
 *
 * The whole call is atomic. Among the threads that ask for the same absent key at once, only
 * one calls [mappingFunction], and the others get its result. A key that already has a value
 * is returned without locking.
 *
 * Use it to create a costly object once per key.
 *
 * [mappingFunction] must not update this map. If it throws, the exception is rethrown and the
 * key keeps no value.
 *
 * @return the value that the key had, or the computed one
 */
@IgnorableReturnValue
public expect fun <K : Any, V : Any> ConcurrentHashMap<K, V>.computeIfAbsent(
    key: K,
    mappingFunction: (K) -> V,
): V

/**
 * If [key] has a value, calls [remappingFunction] with the key and that value, and maps the key
 * to the result. A `null` result removes the mapping.
 *
 * The whole call is atomic, and [remappingFunction] is called at most once.
 *
 * [remappingFunction] must not update this map. If it throws, the exception is rethrown and
 * the mapping is left unchanged.
 *
 * @return the new value of the key, or `null` if it has none
 */
@IgnorableReturnValue
public expect fun <K : Any, V : Any> ConcurrentHashMap<K, V>.computeIfPresent(
    key: K,
    remappingFunction: (K, V) -> V?,
): V?

/**
 * Calls [remappingFunction] with [key] and its value, or `null` if it has none, and maps the
 * key to the result. A `null` result removes the mapping.
 *
 * The whole call is atomic, and [remappingFunction] is called exactly once.
 *
 * [remappingFunction] must not update this map. If it throws, the exception is rethrown and
 * the mapping is left unchanged.
 *
 * @return the new value of the key, or `null` if it has none
 */
@IgnorableReturnValue
public expect fun <K : Any, V : Any> ConcurrentHashMap<K, V>.compute(
    key: K,
    remappingFunction: (K, V?) -> V?,
): V?

/**
 * Maps [key] to [value] if the key has no value. Otherwise calls [remappingFunction] with the
 * current value and [value], and maps the key to the result. A `null` result removes the
 * mapping.
 *
 * The whole call is atomic, and [remappingFunction] is called at most once. Use it to
 * accumulate, for example `counts.merge(word, 1) { count, one -> count + one }`.
 *
 * [remappingFunction] must not update this map. If it throws, the exception is rethrown and
 * the mapping is left unchanged.
 *
 * @return the new value of the key, or `null` if it has none
 */
@IgnorableReturnValue
public expect fun <K : Any, V : Any> ConcurrentHashMap<K, V>.merge(
    key: K,
    value: V,
    remappingFunction: (V, V) -> V?,
): V?
