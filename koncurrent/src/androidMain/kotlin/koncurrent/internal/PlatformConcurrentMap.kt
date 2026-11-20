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

import android.annotation.SuppressLint
import android.os.Build
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiFunction
import java.util.function.Function

/**
 * Whether the platform map has atomic computations, which it has from API 24.
 */
internal val HasPlatformComputations: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

/**
 * The platform map. Its atomic computations exist from API 24, so it must not be loaded below.
 */
@SuppressLint("NewApi")
internal class PlatformConcurrentMap<K : Any, V : Any> :
    ConcurrentHashMap<K, V>,
    ConcurrentMap<K, V> {

    constructor() : super()

    constructor(initialCapacity: Int) : super(initialCapacity)

    @IgnorableReturnValue
    override fun computeIfAbsent(key: K, mappingFunction: (K) -> V): V =
        super<ConcurrentHashMap>.computeIfAbsent(key, Function { mappingFunction(it) })

    @IgnorableReturnValue
    override fun computeIfPresent(key: K, remappingFunction: (K, V) -> V?): V? =
        super<ConcurrentHashMap>.computeIfPresent(key, BiFunction(remappingFunction))

    @IgnorableReturnValue
    override fun compute(key: K, remappingFunction: (K, V?) -> V?): V? =
        super<ConcurrentHashMap>.compute(key, BiFunction(remappingFunction))

    @IgnorableReturnValue
    override fun merge(key: K, value: V, remappingFunction: (V, V) -> V?): V? =
        super<ConcurrentHashMap>.merge(key, value, BiFunction(remappingFunction))
}
