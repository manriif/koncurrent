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
 * A map that keeps its keys in order, that finds the closest ones to a given key, and that
 * several threads use at once.
 *
 * The order is the natural one of the keys, or the one of a [Comparator]. The keys, the values
 * and the entries are gone through from the first key to the last one.
 *
 * [subMap], [headMap], [tailMap] and [descendingMap] return views. A view reads and changes
 * the map that it comes from, and refuses a key that is out of its bounds.
 *
 * An entry that this map returns is a copy of one moment. Its `setValue` throws
 * [UnsupportedOperationException].
 *
 * On the JVM and Android this is `java.util.concurrent.ConcurrentNavigableMap`.
 *
 * @param K the type of the keys
 * @param V the type of the values
 */
@SubclassOptInRequired(InternalForInheritanceKoncurrentApi::class)
public expect interface ConcurrentNavigableMap<K : Any, V : Any> : MutableMap<K, V> {

    /**
     * The keys, in order. Removing a key removes its entry. Nothing can be added.
     */
    override val keys: NavigableSet<K>

    /**
     * Returns the comparator that orders this map, or `null` when it is in the natural order of
     * its keys.
     */
    public fun comparator(): Comparator<in K>?

    /**
     * Maps [key] to [value] unless the key already has a value. The check and the update are
     * one atomic operation.
     *
     * @return the value that the key already had, or `null` if [value] was put
     */
    @IgnorableReturnValue
    public fun putIfAbsent(key: K, value: V): V?

    /**
     * Removes [key] if it is mapped to [value], as one atomic operation.
     *
     * @return whether the entry was removed
     */
    @IgnorableReturnValue
    public fun remove(key: K, value: V): Boolean

    /**
     * Maps [key] to [newValue] if it is mapped to [oldValue], as one atomic operation.
     *
     * @return whether the value was replaced
     */
    @IgnorableReturnValue
    public fun replace(key: K, oldValue: V, newValue: V): Boolean

    /**
     * Maps [key] to [value] if the key has a value, as one atomic operation.
     *
     * @return the value that the key had, or `null` if it had none
     */
    @IgnorableReturnValue
    public fun replace(key: K, value: V): V?

    /**
     * Returns the first key.
     *
     * @throws NoSuchElementException if the map is empty
     */
    public fun firstKey(): K

    /**
     * Returns the last key.
     *
     * @throws NoSuchElementException if the map is empty
     */
    public fun lastKey(): K

    /**
     * Returns the entry of the last key before [key], or `null` if there is none.
     */
    public fun lowerEntry(key: K): Map.Entry<K, V>?

    /**
     * Returns the last key before [key], or `null` if there is none.
     */
    public fun lowerKey(key: K): K?

    /**
     * Returns the entry of [key] if the map has it, else the one of the last key before it, or
     * `null` if there is none.
     */
    public fun floorEntry(key: K): Map.Entry<K, V>?

    /**
     * Returns [key] if the map has it, else the last key before it, or `null` if there is
     * none.
     */
    public fun floorKey(key: K): K?

    /**
     * Returns the entry of [key] if the map has it, else the one of the first key after it, or
     * `null` if there is none.
     */
    public fun ceilingEntry(key: K): Map.Entry<K, V>?

    /**
     * Returns [key] if the map has it, else the first key after it, or `null` if there is
     * none.
     */
    public fun ceilingKey(key: K): K?

    /**
     * Returns the entry of the first key after [key], or `null` if there is none.
     */
    public fun higherEntry(key: K): Map.Entry<K, V>?

    /**
     * Returns the first key after [key], or `null` if there is none.
     */
    public fun higherKey(key: K): K?

    /**
     * Returns the entry of the first key, or `null` if the map is empty.
     */
    public fun firstEntry(): Map.Entry<K, V>?

    /**
     * Returns the entry of the last key, or `null` if the map is empty.
     */
    public fun lastEntry(): Map.Entry<K, V>?

    /**
     * Removes the entry of the first key and returns it, or returns `null` if the map is empty.
     */
    @IgnorableReturnValue
    public fun pollFirstEntry(): Map.Entry<K, V>?

    /**
     * Removes the entry of the last key and returns it, or returns `null` if the map is empty.
     */
    @IgnorableReturnValue
    public fun pollLastEntry(): Map.Entry<K, V>?

    /**
     * Returns a view of this map in the reverse order.
     */
    public fun descendingMap(): ConcurrentNavigableMap<K, V>

    /**
     * Returns the keys, in order. It is what [keys] is.
     */
    public fun navigableKeySet(): NavigableSet<K>

    /**
     * Returns the keys, in the reverse order.
     */
    public fun descendingKeySet(): NavigableSet<K>

    /**
     * Returns a view of the entries whose keys go from [fromKey] to [toKey]. Each flag tells
     * whether the view has a key that equals its bound.
     *
     * @throws IllegalArgumentException if [fromKey] is after [toKey], or if a bound is out of
     * the bounds of this map when it is itself a view
     */
    public fun subMap(
        fromKey: K,
        fromInclusive: Boolean,
        toKey: K,
        toInclusive: Boolean,
    ): ConcurrentNavigableMap<K, V>

    /**
     * Returns a view of the entries whose keys are before [toKey], and of that key when
     * [inclusive].
     *
     * @throws IllegalArgumentException if [toKey] is out of the bounds of this map when it is
     * itself a view
     */
    public fun headMap(toKey: K, inclusive: Boolean): ConcurrentNavigableMap<K, V>

    /**
     * Returns a view of the entries whose keys are after [fromKey], and of that key when
     * [inclusive].
     *
     * @throws IllegalArgumentException if [fromKey] is out of the bounds of this map when it is
     * itself a view
     */
    public fun tailMap(fromKey: K, inclusive: Boolean): ConcurrentNavigableMap<K, V>

    /**
     * Returns a view of the entries whose keys go from [fromKey] to just before [toKey].
     *
     * @throws IllegalArgumentException as [subMap] with its flags does
     */
    public fun subMap(fromKey: K, toKey: K): ConcurrentNavigableMap<K, V>

    /**
     * Returns a view of the entries whose keys are before [toKey].
     *
     * @throws IllegalArgumentException as [headMap] with its flag does
     */
    public fun headMap(toKey: K): ConcurrentNavigableMap<K, V>

    /**
     * Returns a view of the entries whose keys are [fromKey] or after it.
     *
     * @throws IllegalArgumentException as [tailMap] with its flag does
     */
    public fun tailMap(fromKey: K): ConcurrentNavigableMap<K, V>
}
