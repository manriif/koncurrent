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

import koncurrent.locks.StampedLock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Tests the waits that could never end on a platform with one thread. They fail instead.
 */
class SingleThreadWaitsTest {

    @Test
    fun acquireFailsWithoutAPermit() {
        val semaphore = Semaphore(0)

        assertFailsWith<IllegalStateException> { semaphore.acquire() }
        assertEquals(0, semaphore.availablePermits())
    }

    @Test
    fun awaitFailsBeforeTheCountIsZero() {
        val latch = CountDownLatch(1)

        assertFailsWith<IllegalStateException> { latch.await() }
    }

    @Test
    fun awaitFailsAtABarrierOfSeveralParties() {
        val barrier = CyclicBarrier(2)

        assertFailsWith<IllegalStateException> { barrier.await() }
        assertFalse(barrier.isBroken())
    }

    @Test
    fun timedAwaitRunsOutOfTimeAtABarrierOfSeveralParties() {
        val barrier = CyclicBarrier(2)

        assertFailsWith<TimeoutException> { barrier.await(1.seconds) }
        assertTrue(barrier.isBroken())

        assertFailsWith<BrokenBarrierException> { barrier.await() }
    }

    @Test
    fun stampedLockFailsWhereItWouldWait() {
        val lock = StampedLock()
        val read = lock.readLock()

        assertFailsWith<IllegalStateException> { lock.writeLock() }
        assertFailsWith<IllegalStateException> { lock.writeLockInterruptibly() }
        assertEquals(1, lock.getReadLockCount())
        assertFalse(lock.isWriteLocked())

        lock.unlockRead(read)

        val write = lock.writeLock()

        assertFailsWith<IllegalStateException> { lock.readLock() }
        assertFailsWith<IllegalStateException> { lock.readLockInterruptibly() }
        assertFailsWith<IllegalStateException> { lock.writeLock() }
        assertEquals(0, lock.getReadLockCount())

        lock.unlockWrite(write)
    }
}
