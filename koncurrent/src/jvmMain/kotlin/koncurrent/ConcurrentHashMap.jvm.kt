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

import java.util.function.BiFunction
import java.util.function.Function

@Suppress("JavaCollectionWithNullableTypeArgument")
public actual typealias ConcurrentHashMap<K, V> = java.util.concurrent.ConcurrentHashMap<K, V>

///////////////////////////////////////////////////////////////////////////
// Atomic computations
///////////////////////////////////////////////////////////////////////////

// The explicit Java function types select the members of the Java class

@IgnorableReturnValue
public actual fun <K : Any, V : Any> ConcurrentHashMap<K, V>.computeIfAbsent(
    key: K,
    mappingFunction: (K) -> V,
): V = computeIfAbsent(key, Function { mappingFunction(it) })

@IgnorableReturnValue
public actual fun <K : Any, V : Any> ConcurrentHashMap<K, V>.computeIfPresent(
    key: K,
    remappingFunction: (K, V) -> V?,
): V? = computeIfPresent(key, BiFunction(remappingFunction))

@IgnorableReturnValue
public actual fun <K : Any, V : Any> ConcurrentHashMap<K, V>.compute(
    key: K,
    remappingFunction: (K, V?) -> V?,
): V? = compute(key, BiFunction(remappingFunction))

@IgnorableReturnValue
public actual fun <K : Any, V : Any> ConcurrentHashMap<K, V>.merge(
    key: K,
    value: V,
    remappingFunction: (V, V) -> V?,
): V? = merge(key, value, BiFunction(remappingFunction))
