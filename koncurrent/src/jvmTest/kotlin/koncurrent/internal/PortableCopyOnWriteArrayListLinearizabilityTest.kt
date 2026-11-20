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

private const val THREAD_COUNT = 3
private const val ACTOR_COUNT = 3
private const val MODEL_CHECKING_INVOCATIONS = 300

/**
 * Checks with Lincheck that the operations of [PortableCopyOnWriteArrayList] are linearizable.
 */
@Param(name = "element", gen = IntGen::class, conf = "1:3")
@Param(name = "index", gen = IntGen::class, conf = "0:2")
class PortableCopyOnWriteArrayListLinearizabilityTest {

    private val list = PortableCopyOnWriteArrayList<Int>()

    @Operation
    fun add(@Param(name = "element") element: Int): Boolean = list.add(element)

    @Operation
    fun addAt(@Param(name = "index") index: Int, @Param(name = "element") element: Int): Unit =
        list.add(index, element)

    @Operation
    fun addIfAbsent(@Param(name = "element") element: Int): Boolean = list.addIfAbsent(element)

    @Operation
    fun set(@Param(name = "index") index: Int, @Param(name = "element") element: Int): Int =
        list.set(index, element)

    @Operation
    fun remove(@Param(name = "element") element: Int): Boolean = list.remove(element)

    @Operation
    fun removeAt(@Param(name = "index") index: Int): Int = list.removeAt(index)

    @Operation
    fun get(@Param(name = "index") index: Int): Int = list[index]

    @Operation
    fun contains(@Param(name = "element") element: Int): Boolean = element in list

    // Reads through the iterator. `toList` reads the size and then the elements, in two steps.
    @Operation
    fun elements(): List<Int> = buildList { list.forEach(::add) }

    // Lets Lincheck tell that two sequential executions reached the same state

    override fun equals(other: Any?): Boolean =
        other is PortableCopyOnWriteArrayListLinearizabilityTest && other.list == list

    override fun hashCode(): Int = list.hashCode()

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
