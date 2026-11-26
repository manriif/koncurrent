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

import org.jetbrains.lincheck.datastructures.IntGen
import org.jetbrains.lincheck.datastructures.ModelCheckingOptions
import org.jetbrains.lincheck.datastructures.Operation
import org.jetbrains.lincheck.datastructures.Param
import org.jetbrains.lincheck.datastructures.StressOptions
import kotlin.test.Test

private const val KEY = "key"

private const val THREAD_COUNT = 3
private const val ACTOR_COUNT = 3
private const val MODEL_CHECKING_INVOCATIONS = 300

/**
 * Checks with Lincheck that the operations of [PortableNavigableMap] are linearizable: those
 * of a key, the closest keys, both ends, the size of a part and the keys in order.
 *
 * The polls of a view with bounds are left out. They find an entry, then remove it if it is
 * still there, as the sub map of the original does, which is not one atomic operation.
 */
@Param(name = KEY, gen = IntGen::class, conf = "1:5")
class PortableNavigableMapLinearizabilityTest {

    private val map = PortableNavigableMap<Int, Int>(null)

    @Operation
    fun put(@Param(name = KEY) key: Int, @Param(name = KEY) value: Int): Int? = map.put(key, value)

    @Operation
    fun putIfAbsent(@Param(name = KEY) key: Int, @Param(name = KEY) value: Int): Int? =
        map.putIfAbsent(key, value)

    @Operation
    fun remove(@Param(name = KEY) key: Int): Int? = map.remove(key)

    @Operation
    fun removeValue(@Param(name = KEY) key: Int, @Param(name = KEY) value: Int): Boolean =
        map.remove(key, value)

    @Operation
    fun replace(@Param(name = KEY) key: Int, @Param(name = KEY) value: Int): Int? =
        map.replace(key, value)

    @Operation
    fun get(@Param(name = KEY) key: Int): Int? = map.get(key)

    @Operation
    fun lowerKey(@Param(name = KEY) key: Int): Int? = map.nearKey(key, LOWER)

    @Operation
    fun ceilingKey(@Param(name = KEY) key: Int): Int? = map.nearKey(key, CEILING)

    @Operation
    fun pollFirst(): Int? = map.pollFirstEntry()?.key

    @Operation
    fun pollLast(): Int? = map.pollLastEntry()?.key

    @Operation
    fun sizeFrom(@Param(name = KEY) key: Int): Int = map.tailMap(key, true).size

    @Operation
    fun keys(): List<Int> = map.keyIterator().asSequence().toList()

    // Lets Lincheck tell that two sequential executions reached the same state

    private fun entries(): List<Pair<Int, Int>> =
        map.entryIterator().asSequence().map { it.key to it.value }.toList()

    override fun equals(other: Any?): Boolean =
        other is PortableNavigableMapLinearizabilityTest && other.entries() == entries()

    override fun hashCode(): Int = entries().hashCode()

    @Test
    fun modelChecking() {
        ModelCheckingOptions()
            .threads(THREAD_COUNT)
            .actorsPerThread(ACTOR_COUNT)
            .invocationsPerIteration(MODEL_CHECKING_INVOCATIONS)
            .check(this::class)
    }

    @Test
    fun stress() {
        StressOptions()
            .threads(THREAD_COUNT)
            .actorsPerThread(ACTOR_COUNT)
            .check(this::class)
    }
}
