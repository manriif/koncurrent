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

import koncurrent.spinFor
import koncurrent.startThread
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private const val FREE = 0
private const val HELD = 1

private val BlockedDelay = 50.milliseconds
private val GiveUpTimeout = 100.milliseconds
private val AcquireTimeout = 10.seconds

/**
 * Tests what the locks do not exercise of [Synchronizer] one case at a time: a try that throws
 * while its thread is in the queue, and a thread that gives up in front of another one.
 */
class SynchronizerTest {

    @Test
    fun exclusiveThreadBehindOneThatGaveUpAcquires() {
        assertThreadBehindOneThatGaveUpAcquires(shared = false)
    }

    @Test
    fun sharedThreadBehindOneThatGaveUpAcquires() {
        assertThreadBehindOneThatGaveUpAcquires(shared = true)
    }

    /**
     * Makes two threads wait in the queue, the first one for a short time. Once it gave up,
     * a release must wake up the second one, which sleeps behind the node that was left.
     */
    private fun assertThreadBehindOneThatGaveUpAcquires(shared: Boolean) {
        val synchronizer = Synchronizer()

        synchronizer.state.store(HELD)

        val first = startThread { assertFalse(synchronizer.acquireWithin(GiveUpTimeout, shared)) }

        spinFor(BlockedDelay)

        // It wakes up by itself after a while, so that a lost wake-up fails this test
        val second = startThread { assertTrue(synchronizer.acquireWithin(AcquireTimeout, shared)) }

        assertNull(first())
        spinFor(BlockedDelay)

        val release = TimeSource.Monotonic.markNow()

        synchronizer.state.store(FREE)
        synchronizer.signalNext()

        assertNull(second())

        // The release woke the second thread up, not the end of its own time
        assertTrue(release.elapsedNow() < AcquireTimeout / 2)
    }

    private fun Synchronizer.acquireWithin(timeout: Duration, shared: Boolean): Boolean {
        val deadline = deadlineAfter(timeout.inWholeNanoseconds)

        return acquireQueued(null, shared, interruptible = false, timed = true, deadline) {
            state.compareAndSet(FREE, HELD)
        }
    }

    @Test
    fun exclusiveTryThatThrowsLetsTheNextThreadAcquire() {
        assertFailedTryLetsTheNextThreadAcquire(shared = false)
    }

    @Test
    fun sharedTryThatThrowsLetsTheNextThreadAcquire() {
        assertFailedTryLetsTheNextThreadAcquire(shared = true)
    }

    /**
     * Makes two threads wait in the queue, then has the try of the first one throw once it is
     * woken up. The second thread must still acquire.
     */
    private fun assertFailedTryLetsTheNextThreadAcquire(shared: Boolean) {
        val synchronizer = Synchronizer()
        val failing = AtomicBoolean(false)
        val acquired = AtomicBoolean(false)

        synchronizer.state.store(HELD)

        val first = startThread {
            synchronizer.acquire(shared, interruptible = false) {
                check(!failing.load()) { "The try fails" }
                synchronizer.state.compareAndSet(FREE, HELD)
            }
        }

        spinFor(BlockedDelay)

        val second = startThread {
            synchronizer.acquire(shared, interruptible = false) {
                synchronizer.state.compareAndSet(FREE, HELD)
            }

            acquired.store(true)
        }

        spinFor(BlockedDelay)
        assertFalse(acquired.load())

        // Both threads wait. The release wakes up the first one, whose try now throws.
        failing.store(true)
        synchronizer.state.store(FREE)
        synchronizer.signalNext()

        assertIs<IllegalStateException>(first())
        assertNull(second())
        assertTrue(acquired.load())
    }
}
