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

import koncurrent.internal.PortableConcurrentHashMap
import kotlin.concurrent.Volatile

public actual class ConcurrentHashMap<K : Any, V : Any> private constructor(
    internal val map: PortableConcurrentHashMap<K, V>,
) : MutableMap<K, V> by map {

    @Volatile
    private var keysView: KeySetView<K, V>? = null

    actual override val keys: KeySetView<K, V>
        get() = keysView ?: KeySetView<K, V>(map.keys).also { keysView = it }

    public actual constructor() : this(PortableConcurrentHashMap())

    public actual constructor(initialCapacity: Int) : this(
        PortableConcurrentHashMap(initialCapacity),
    )

    // The lookups of the port are inlined here, which spares a call

    actual override fun get(key: K): V? = map.findValue(key)

    actual override fun containsKey(key: K): Boolean = map.findValue(key) != null

    @IgnorableReturnValue
    public actual fun putIfAbsent(key: K, value: V): V? = map.putIfAbsent(key, value)

    @IgnorableReturnValue
    public actual fun remove(key: K, value: V): Boolean = map.remove(key, value)

    @IgnorableReturnValue
    public actual fun replace(key: K, oldValue: V, newValue: V): Boolean =
        map.replace(key, oldValue, newValue)

    @IgnorableReturnValue
    public actual fun replace(key: K, value: V): V? = map.replace(key, value)

    override fun equals(other: Any?): Boolean = map == other

    override fun hashCode(): Int = map.hashCode()

    override fun toString(): String = map.toString()

    public actual class KeySetView<K : Any, @Suppress("unused") V : Any> internal constructor(
        private val keys: MutableSet<K>,
    ) : MutableSet<K> by keys {

        override fun equals(other: Any?): Boolean = keys == other

        override fun hashCode(): Int = keys.hashCode()

        override fun toString(): String = keys.toString()
    }
}

///////////////////////////////////////////////////////////////////////////
// Atomic computations
///////////////////////////////////////////////////////////////////////////

@IgnorableReturnValue
public actual fun <K : Any, V : Any> ConcurrentHashMap<K, V>.computeIfAbsent(
    key: K,
    mappingFunction: (K) -> V,
): V = map.findValue(key) ?: map.computeAbsent(key, mappingFunction)

@IgnorableReturnValue
public actual fun <K : Any, V : Any> ConcurrentHashMap<K, V>.computeIfPresent(
    key: K,
    remappingFunction: (K, V) -> V?,
): V? = map.computeIfPresent(key, remappingFunction)

@IgnorableReturnValue
public actual fun <K : Any, V : Any> ConcurrentHashMap<K, V>.compute(
    key: K,
    remappingFunction: (K, V?) -> V?,
): V? = map.compute(key, remappingFunction)

@IgnorableReturnValue
public actual fun <K : Any, V : Any> ConcurrentHashMap<K, V>.merge(
    key: K,
    value: V,
    remappingFunction: (V, V) -> V?,
): V? = map.merge(key, value, remappingFunction)
