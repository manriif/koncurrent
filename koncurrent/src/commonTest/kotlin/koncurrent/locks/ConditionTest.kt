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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tests what one thread sees of a [Condition] of the public [ReentrantLock].
 */
class ConditionTest {

    @Test
    fun conditionFailsWithoutTheLock() {
        val lock = ReentrantLock()
        val condition = lock.newCondition()

        assertFailsWith<IllegalMonitorStateException> { condition.await() }
        assertFailsWith<IllegalMonitorStateException> { val _ = condition.await(1.milliseconds) }
        assertFailsWith<IllegalMonitorStateException> { condition.signal() }
        assertFailsWith<IllegalMonitorStateException> { condition.signalAll() }
    }

    @Test
    fun signalWithoutWaitingThreadDoesNothing() {
        val lock = ReentrantLock()
        val condition = lock.newCondition()

        lock.withLock {
            condition.signal()
            condition.signalAll()
        }

        assertTrue(lock.tryLock())
        lock.unlock()
    }

    @Test
    fun timedAwaitReturnsFalseWhenNothingSignals() {
        val lock = ReentrantLock()
        val condition = lock.newCondition()

        lock.withLock {
            assertFalse(condition.await(Duration.ZERO))
            assertFalse(condition.await(1.milliseconds))
        }

        assertFailsWith<IllegalMonitorStateException> { lock.unlock() }
    }
}
