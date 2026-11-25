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

// So small that the queue is often full and often empty
private const val CAPACITY = 2

/**
 * Checks with Lincheck that the operations of the port of a blocking queue are linearizable.
 *
 * What waits is left out. A scenario cannot promise that another thread ends a wait. And a
 * wait with a time limit never ends under model checking, which stops the clock: on the JVM
 * the ports wait on the conditions of the JDK, which count their time on that clock. The
 * iteration is left out too. It is not atomic by design.
 */
@Param(name = ELEMENT, gen = IntGen::class, conf = "1:3")
abstract class BlockingQueueLinearizabilityTest internal constructor(
    internal val queue: PortableBlockingQueue<Int>,
) {

    @Operation
    fun offer(@Param(name = ELEMENT) element: Int): Boolean = queue.offer(element)

    @Operation
    fun poll(): Int? = queue.poll()

    @Operation
    fun peek(): Int? = queue.peek()

    @Operation
    fun remove(@Param(name = ELEMENT) element: Int): Boolean = queue.remove(element)

    @Operation
    fun contains(@Param(name = ELEMENT) element: Int): Boolean = element in queue

    @Operation
    fun size(): Int = queue.size

    @Operation
    fun remainingCapacity(): Int = queue.remainingCapacity()

    @Operation
    fun clear(): Unit = queue.clear()

    // Lets Lincheck tell that two sequential executions reached the same state

    internal open fun elements(): List<Int> = queue.toList()

    override fun equals(other: Any?): Boolean =
        other is BlockingQueueLinearizabilityTest && other.elements() == elements()

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

/**
 * `drainTo` is left out for this queue. It lowers the count after it moved the elements, as in
 * the original, and the count is read without the lock that it holds. So until it is done the
 * size still counts the elements that are gone, and an `offer` may find the queue full.
 */
class PortableLinkedBlockingQueueLinearizabilityTest :
    BlockingQueueLinearizabilityTest(PortableLinkedBlockingQueue(CAPACITY))

/**
 * Adds `drainTo`, for the queues that one lock guards. It is atomic there.
 */
abstract class OneLockQueueLinearizabilityTest internal constructor(
    queue: PortableBlockingQueue<Int>,
) : BlockingQueueLinearizabilityTest(queue) {

    @Operation
    fun drain(): List<Int> {
        val drained = ArrayList<Int>()
        val _ = queue.drainTo(drained, Int.MAX_VALUE)

        return drained
    }
}

class PortableArrayBlockingQueueLinearizabilityTest :
    OneLockQueueLinearizabilityTest(PortableArrayBlockingQueue(CAPACITY))

/**
 * The queue starts with room for one element, so that it grows during the scenarios.
 */
class PortablePriorityBlockingQueueLinearizabilityTest :
    OneLockQueueLinearizabilityTest(PortablePriorityBlockingQueue(1, null)) {

    // Two heaps with the same elements behave alike, whatever their layout
    override fun elements(): List<Int> = queue.toList().sorted()
}

/**
 * Adds the operations of the other end of the deque.
 */
class PortableLinkedBlockingDequeLinearizabilityTest private constructor(
    private val deque: PortableLinkedBlockingDeque<Int>,
) : OneLockQueueLinearizabilityTest(deque) {

    constructor() : this(PortableLinkedBlockingDeque(CAPACITY))

    @Operation
    fun offerFirst(@Param(name = ELEMENT) element: Int): Boolean = deque.offerFirst(element)

    @Operation
    fun pollLast(): Int? = deque.pollLast()

    @Operation
    fun peekLast(): Int? = deque.peekLast()
}
