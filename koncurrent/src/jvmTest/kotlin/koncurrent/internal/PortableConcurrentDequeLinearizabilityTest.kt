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

private const val ELEMENT = "element"

private const val THREAD_COUNT = 3
private const val ACTOR_COUNT = 3
private const val MODEL_CHECKING_INVOCATIONS = 300

/**
 * Checks with Lincheck that the operations of [PortableConcurrentDeque] are linearizable, the
 * copy that an iterator goes through included.
 */
@Param(name = ELEMENT, gen = IntGen::class, conf = "1:3")
class PortableConcurrentDequeLinearizabilityTest {

    private val deque = PortableConcurrentDeque<Int>()

    @Operation
    fun addFirst(@Param(name = ELEMENT) element: Int): Unit = deque.addFirst(element)

    @Operation
    fun addLast(@Param(name = ELEMENT) element: Int): Unit = deque.addLast(element)

    @Operation
    fun pollFirst(): Int? = deque.pollFirst()

    @Operation
    fun pollLast(): Int? = deque.pollLast()

    @Operation
    fun peekFirst(): Int? = deque.peekFirst()

    @Operation
    fun peekLast(): Int? = deque.peekLast()

    @Operation
    fun remove(@Param(name = ELEMENT) element: Int): Boolean = deque.remove(element)

    @Operation
    fun contains(@Param(name = ELEMENT) element: Int): Boolean = element in deque

    @Operation
    fun size(): Int = deque.size

    @Operation
    fun clear(): Unit = deque.clear()

    @Operation
    fun elements(): List<Int> = deque.iterator().asSequence().toList()

    // Lets Lincheck tell that two sequential executions reached the same state

    override fun equals(other: Any?): Boolean =
        other is PortableConcurrentDequeLinearizabilityTest && other.elements() == elements()

    override fun hashCode(): Int = elements().hashCode()

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
