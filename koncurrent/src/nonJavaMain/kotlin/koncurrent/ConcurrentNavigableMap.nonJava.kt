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

@SubclassOptInRequired(InternalForInheritanceKoncurrentApi::class)
public actual interface ConcurrentNavigableMap<K : Any, V : Any> : MutableMap<K, V> {

    actual override val keys: NavigableSet<K>

    public actual fun comparator(): Comparator<in K>?

    @IgnorableReturnValue
    public actual fun putIfAbsent(key: K, value: V): V?

    @IgnorableReturnValue
    public actual fun remove(key: K, value: V): Boolean

    @IgnorableReturnValue
    public actual fun replace(key: K, oldValue: V, newValue: V): Boolean

    @IgnorableReturnValue
    public actual fun replace(key: K, value: V): V?

    public actual fun firstKey(): K

    public actual fun lastKey(): K

    public actual fun lowerEntry(key: K): Map.Entry<K, V>?

    public actual fun lowerKey(key: K): K?

    public actual fun floorEntry(key: K): Map.Entry<K, V>?

    public actual fun floorKey(key: K): K?

    public actual fun ceilingEntry(key: K): Map.Entry<K, V>?

    public actual fun ceilingKey(key: K): K?

    public actual fun higherEntry(key: K): Map.Entry<K, V>?

    public actual fun higherKey(key: K): K?

    public actual fun firstEntry(): Map.Entry<K, V>?

    public actual fun lastEntry(): Map.Entry<K, V>?

    @IgnorableReturnValue
    public actual fun pollFirstEntry(): Map.Entry<K, V>?

    @IgnorableReturnValue
    public actual fun pollLastEntry(): Map.Entry<K, V>?

    public actual fun descendingMap(): ConcurrentNavigableMap<K, V>

    public actual fun navigableKeySet(): NavigableSet<K>

    public actual fun descendingKeySet(): NavigableSet<K>

    public actual fun subMap(
        fromKey: K,
        fromInclusive: Boolean,
        toKey: K,
        toInclusive: Boolean,
    ): ConcurrentNavigableMap<K, V>

    public actual fun headMap(toKey: K, inclusive: Boolean): ConcurrentNavigableMap<K, V>

    public actual fun tailMap(fromKey: K, inclusive: Boolean): ConcurrentNavigableMap<K, V>

    public actual fun subMap(fromKey: K, toKey: K): ConcurrentNavigableMap<K, V>

    public actual fun headMap(toKey: K): ConcurrentNavigableMap<K, V>

    public actual fun tailMap(fromKey: K): ConcurrentNavigableMap<K, V>
}
