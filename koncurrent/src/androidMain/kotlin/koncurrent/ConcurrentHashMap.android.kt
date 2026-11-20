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
@file:JvmName("ConcurrentHashMapKt")

package koncurrent

import koncurrent.internal.ConcurrentMap
import koncurrent.internal.HasPlatformComputations
import koncurrent.internal.PlatformConcurrentMap
import koncurrent.internal.PortableConcurrentHashMap

// The platform map has no atomic computation below API 24, so the port replaces it there
public actual class ConcurrentHashMap<K : Any, V : Any> internal constructor(
    internal val map: ConcurrentMap<K, V>,
) : MutableMap<K, V> by map {

    @Volatile
    private var keysView: KeySetView<K, V>? = null

    actual override val keys: KeySetView<K, V>
        get() = keysView ?: KeySetView<K, V>(map.keys).also { keysView = it }

    public actual constructor() : this(
        if (HasPlatformComputations) PlatformConcurrentMap() else PortableConcurrentHashMap(),
    )

    public actual constructor(initialCapacity: Int) : this(
        if (HasPlatformComputations) {
            PlatformConcurrentMap(initialCapacity)
        } else {
            PortableConcurrentHashMap(initialCapacity)
        },
    )

    @IgnorableReturnValue
    public actual fun putIfAbsent(key: K, value: V): V? = map.putIfAbsent(key, value)

    @IgnorableReturnValue
    public actual fun remove(key: K, value: V): Boolean = map.remove(key, value)

    @IgnorableReturnValue
    public actual fun replace(key: K, oldValue: V, newValue: V): Boolean =
        map.replace(key, oldValue, newValue)

    @IgnorableReturnValue
    public actual fun replace(key: K, value: V): V? = map.replace(key, value)

    // The computations are members here, so that a lambda never selects the ones that this
    // class inherits from java.util.Map. Those are not atomic, and they do not exist below
    // API 24.

    /**
     * Returns the value of [key], after computing it with [mappingFunction] if the key has
     * none. The call is atomic. See [koncurrent.computeIfAbsent].
     */
    @IgnorableReturnValue
    public fun computeIfAbsent(key: K, mappingFunction: (K) -> V): V =
        map.computeIfAbsent(key, mappingFunction)

    /**
     * Replaces the value of [key] with the result of [remappingFunction], if the key has one.
     * The call is atomic. See [koncurrent.computeIfPresent].
     */
    @IgnorableReturnValue
    public fun computeIfPresent(key: K, remappingFunction: (K, V) -> V?): V? =
        map.computeIfPresent(key, remappingFunction)

    /**
     * Maps [key] to the result of [remappingFunction], which gets its value, or `null` if it
     * has none. The call is atomic. See [koncurrent.compute].
     */
    @IgnorableReturnValue
    public fun compute(key: K, remappingFunction: (K, V?) -> V?): V? =
        map.compute(key, remappingFunction)

    /**
     * Maps [key] to [value], or to the result of [remappingFunction] if the key has a value.
     * The call is atomic. See [koncurrent.merge].
     */
    @IgnorableReturnValue
    public fun merge(key: K, value: V, remappingFunction: (V, V) -> V?): V? =
        map.merge(key, value, remappingFunction)

    override fun equals(other: Any?): Boolean = map == other

    override fun hashCode(): Int = map.hashCode()

    override fun toString(): String = map.toString()

    @Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
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

// Common code calls these. They are the members above.

@IgnorableReturnValue
public actual fun <K : Any, V : Any> ConcurrentHashMap<K, V>.computeIfAbsent(
    key: K,
    mappingFunction: (K) -> V,
): V = computeIfAbsent(key, mappingFunction)

@IgnorableReturnValue
public actual fun <K : Any, V : Any> ConcurrentHashMap<K, V>.computeIfPresent(
    key: K,
    remappingFunction: (K, V) -> V?,
): V? = computeIfPresent(key, remappingFunction)

@IgnorableReturnValue
public actual fun <K : Any, V : Any> ConcurrentHashMap<K, V>.compute(
    key: K,
    remappingFunction: (K, V?) -> V?,
): V? = compute(key, remappingFunction)

@IgnorableReturnValue
public actual fun <K : Any, V : Any> ConcurrentHashMap<K, V>.merge(
    key: K,
    value: V,
    remappingFunction: (V, V) -> V?,
): V? = merge(key, value, remappingFunction)
