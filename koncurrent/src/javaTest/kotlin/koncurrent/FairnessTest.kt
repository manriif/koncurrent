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

import koncurrent.locks.ReentrantLock
import koncurrent.locks.ReentrantReadWriteLock
import koncurrent.locks.TestLock
import koncurrent.locks.TestReadWriteLock

/**
 * Tests the order of the fair `ReentrantLock` of the JDK, which tells the length of its queue.
 */
class ReentrantLockFairnessTest : FairnessContractTest() {

    override fun newSynchronizer(): TestFairSynchronizer {
        val lock = ReentrantLock(true)

        return TestFairSynchronizer(TestLock(lock), lock::getQueueLength)
    }
}

/**
 * Tests the order of the write lock of the fair `ReentrantReadWriteLock` of the JDK.
 */
class WriteLockFairnessTest : FairnessContractTest() {

    override fun newSynchronizer(): TestFairSynchronizer {
        val lock = ReentrantReadWriteLock(true)

        return TestFairSynchronizer(TestReadWriteLock(lock).write, lock::getQueueLength)
    }
}

/**
 * Tests the order of the read lock of the fair `ReentrantReadWriteLock` of the JDK.
 */
class ReadLockFairnessTest : FairReadWriteLockContractTest() {

    override fun newLock(): TestFairReadWriteLock {
        val lock = ReentrantReadWriteLock(true)
        val locks = TestReadWriteLock(lock)

        return TestFairReadWriteLock(locks.read, locks.write, lock::getQueueLength)
    }
}

/**
 * Tests the order of the fair `Semaphore` of the JDK.
 */
class SemaphoreFairnessTest : FairnessContractTest() {

    override fun newSynchronizer(): TestFairSynchronizer {
        val semaphore = Semaphore(1, true)

        return TestFairSynchronizer(TestSemaphore(semaphore), semaphore::getQueueLength)
    }
}
