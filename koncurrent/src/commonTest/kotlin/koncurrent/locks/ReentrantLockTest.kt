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
package koncurrent.locks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tests what one thread sees of the public [ReentrantLock].
 */
class ReentrantLockTest {

    @Test
    fun freeLockIsAcquiredAndReleased() {
        val lock = ReentrantLock()

        lock.lock()
        lock.unlock()

        assertTrue(lock.tryLock())
        lock.unlock()
    }

    @Test
    fun lockIsReentrant() {
        val lock = ReentrantLock()

        lock.lock()
        lock.lock()
        assertTrue(lock.tryLock())

        repeat(3) { lock.unlock() }

        assertTrue(lock.tryLock())
        lock.unlock()
    }

    @Test
    fun unlockFailsWithoutAHold() {
        val lock = ReentrantLock()

        assertFailsWith<IllegalMonitorStateException> { lock.unlock() }

        lock.lock()
        lock.unlock()

        assertFailsWith<IllegalMonitorStateException> { lock.unlock() }
    }

    @Test
    fun withLockReturnsTheValueOfItsAction() {
        val lock = ReentrantLock()
        val initialized: Int

        val value = lock.withLock {
            initialized = 1
            "value"
        }

        assertEquals("value", value)
        assertEquals(1, initialized)
    }

    @Test
    fun withLockReleasesWhenItsActionFails() {
        val lock = ReentrantLock()

        assertFailsWith<IllegalArgumentException> {
            lock.withLock { throw IllegalArgumentException() }
        }

        assertTrue(lock.tryLock())
        lock.unlock()
    }

    @Test
    fun freeLockIsAcquiredInEveryWay() {
        val lock = ReentrantLock()

        lock.lockInterruptibly()
        assertTrue(lock.tryLock(Duration.ZERO))
        assertTrue(lock.tryLock(1.milliseconds))

        repeat(3) { lock.unlock() }

        assertFailsWith<IllegalMonitorStateException> { lock.unlock() }
    }
}
