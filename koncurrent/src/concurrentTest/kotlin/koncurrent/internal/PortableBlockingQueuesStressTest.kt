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

import koncurrent.BlockingDequeContractTest
import koncurrent.BlockingQueueContractTest
import koncurrent.PriorityBlockingQueueContractTest
import koncurrent.TestBlockingDeque
import koncurrent.TestQueue

/**
 * Tests [PortableLinkedBlockingQueue] on real threads, on the JVM too where it backs nothing.
 */
@Suppress("unused")
class PortableLinkedBlockingQueueStressTest : BlockingQueueContractTest() {

    override fun <E : Any> newQueue(capacity: Int): TestQueue<E> =
        PortableLinkedBlockingQueue<E>(capacity).asTestQueue()
}

/**
 * Tests [PortableLinkedBlockingDeque] as a blocking queue, on real threads.
 */
@Suppress("unused")
class PortableLinkedBlockingDequeAsQueueStressTest : BlockingQueueContractTest() {

    override val untimedWaitsLookAtTheInterruptFirst: Boolean
        get() = false

    override fun <E : Any> newQueue(capacity: Int): TestQueue<E> =
        PortableLinkedBlockingDeque<E>(capacity).asTestQueue()
}

/**
 * Tests the ends of [PortableLinkedBlockingDeque] on real threads.
 */
class PortableLinkedBlockingDequeStressTest : BlockingDequeContractTest() {

    override fun <E : Any> newDeque(capacity: Int): TestBlockingDeque<E> =
        PortableLinkedBlockingDeque<E>(capacity).asTestBlockingDeque()
}

/**
 * Tests [PortableArrayBlockingQueue] on real threads.
 */
@Suppress("unused")
class PortableArrayBlockingQueueStressTest : BlockingQueueContractTest() {

    override fun <E : Any> newQueue(capacity: Int): TestQueue<E> =
        PortableArrayBlockingQueue<E>(capacity).asTestQueue()
}

/**
 * Tests [PortablePriorityBlockingQueue] on real threads. It starts with room for one element,
 * to grow as often as it can while the threads take.
 */
class PortablePriorityBlockingQueueStressTest : PriorityBlockingQueueContractTest() {

    override fun <E : Any> newQueue(): TestQueue<E> =
        PortablePriorityBlockingQueue<E>(1, null).asTestQueue()
}
