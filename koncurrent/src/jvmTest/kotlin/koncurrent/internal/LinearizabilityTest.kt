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

import koncurrent.CollidingKey
import kotlin.test.Test
import org.jetbrains.lincheck.datastructures.IntGen
import org.jetbrains.lincheck.datastructures.ModelCheckingOptions
import org.jetbrains.lincheck.datastructures.Operation
import org.jetbrains.lincheck.datastructures.Param
import org.jetbrains.lincheck.datastructures.StressOptions

private const val KEY = "key"
private const val VALUE = "value"

/**
 * Checks with Lincheck that the operations of [PortableConcurrentHashMap] are linearizable.
 * Lincheck runs random operations from several threads. Then it looks for an order in which the
 * same operations, run one after the other, give the same results.
 *
 * The size, the iterations and the bulk operations are left out. They are not atomic by design.
 *
 * [hashOf] gives the hash code of a key. [initialKeys] are in the map before the operations.
 * [actorsBefore] operations run before the threads start.
 */
@Param(name = KEY, gen = IntGen::class, conf = "0:7")
@Param(name = VALUE, gen = IntGen::class, conf = "1:3")
abstract class LinearizabilityTest(
    initialKeys: List<Int>,
    private val hashOf: (Int) -> Int,
    private val actorsBefore: Int,
    private val modelCheckingInvocations: Int,
) {

    internal val map = PortableConcurrentHashMap<CollidingKey, Int>()

    init {
        initialKeys.forEach { map[key(it)] = 0 }
    }

    @Operation
    fun get(@Param(name = KEY) key: Int): Int? = map[key(key)]

    @Operation
    fun containsKey(@Param(name = KEY) key: Int): Boolean = map.containsKey(key(key))

    @Operation
    fun put(@Param(name = KEY) key: Int, @Param(name = VALUE) value: Int): Int? =
        map.put(key(key), value)

    @Operation
    fun remove(@Param(name = KEY) key: Int): Int? = map.remove(key(key))

    @Operation
    fun putIfAbsent(@Param(name = KEY) key: Int, @Param(name = VALUE) value: Int): Int? =
        map.putIfAbsent(key(key), value)

    @Operation
    fun removeValue(@Param(name = KEY) key: Int, @Param(name = VALUE) value: Int): Boolean =
        map.remove(key(key), value)

    @Operation
    fun replace(@Param(name = KEY) key: Int, @Param(name = VALUE) value: Int): Int? =
        map.replace(key(key), value)

    @Operation
    fun replaceValue(
        @Param(name = KEY) key: Int,
        @Param(name = VALUE) oldValue: Int,
        @Param(name = VALUE) newValue: Int,
    ): Boolean = map.replace(key(key), oldValue, newValue)

    @Operation
    fun computeIfAbsent(@Param(name = KEY) key: Int, @Param(name = VALUE) value: Int): Int =
        map.computeIfAbsent(key(key)) { value }

    @Operation
    fun computeIfPresent(@Param(name = KEY) key: Int, @Param(name = VALUE) value: Int): Int? =
        map.computeIfPresent(key(key)) { _, current ->
            if (current == value) null else current + value
        }

    @Operation
    fun compute(@Param(name = KEY) key: Int, @Param(name = VALUE) value: Int): Int? =
        map.compute(key(key)) { _, current ->
            if (current == value) null else (current ?: 0) + value
        }

    @Operation
    fun merge(@Param(name = KEY) key: Int, @Param(name = VALUE) value: Int): Int? =
        map.merge(key(key), value) { current, added ->
            if (current == added) null else current + added
        }

    @Test
    fun modelChecking(): Unit = ModelCheckingOptions()
        .actorsBefore(actorsBefore)
        .iterations(100)
        .invocationsPerIteration(modelCheckingInvocations)
        .check(this::class)

    @Test
    fun stress(): Unit = StressOptions()
        .actorsBefore(actorsBefore)
        .iterations(100)
        .invocationsPerIteration(10_000)
        .check(this::class)

    private fun key(id: Int) = CollidingKey(id, hashOf(id))
}

/**
 * The keys share two bins, as lists.
 */
class ListBinsLinearizabilityTest : LinearizabilityTest(
    initialKeys = emptyList(),
    hashOf = { it % 2 },
    actorsBefore = 5,
    modelCheckingInvocations = 5_000,
)

/**
 * The initial keys fill the default table up to one mapping below its resize. So the first
 * insertion resizes the table while the other threads read and update it. Some of its bins
 * hold several nodes, which the resize splits. Model checking a resize takes long, so it gets
 * fewer invocations.
 */
class ResizeLinearizabilityTest : LinearizabilityTest(
    initialKeys = listOf(17, 33, 49, 65, 18, 34, 50, 19, 35, 20, 36),
    hashOf = { it },
    actorsBefore = 0,
    modelCheckingInvocations = 2_000,
) {

    init {
        check(map.table?.size == 16 && map.size == 11)
    }
}

/**
 * The keys share one bin. The initial keys make it a tree. Model checking a tree takes long, so
 * it gets fewer invocations.
 */
class TreeBinLinearizabilityTest : LinearizabilityTest(
    initialKeys = (0..<12).toList(),
    hashOf = { 5 },
    actorsBefore = 5,
    modelCheckingInvocations = 1_000,
) {

    init {
        check(assertTreeInvariants(map) == 1)
    }
}
