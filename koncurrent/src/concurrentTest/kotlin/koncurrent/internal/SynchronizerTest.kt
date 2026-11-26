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
import kotlin.test.assertSame
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
 * while its thread is in the queue, a thread that gives up in front of another one, and a link
 * to a node that gave up.
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

    /**
     * A signal links a condition node behind its predecessor in the place of the thread of
     * that node, which sleeps. That write may come late: after the thread gave up, and after
     * the thread behind it skipped the node. The head would then point again to a node that no
     * thread waits on, with the first thread behind it. The signal must put that thread back
     * in its place, since a release only looks at the node after the head.
     */
    @Test
    fun aSignalThatLinksANodeThatGaveUpLinksTheThreadBehindInstead() {
        val synchronizer = Synchronizer()

        synchronizer.state.store(HELD)

        // It wakes up by itself after a while, so that a lost wake-up fails this test
        val waiter = startThread {
            assertTrue(synchronizer.acquireWithin(AcquireTimeout, shared = false))
        }

        val start = TimeSource.Monotonic.markNow()

        while (synchronizer.tail.load()?.status != WAITING) {
            check(start.elapsedNow() < AcquireTimeout) { "The thread never waited" }
        }

        val head = checkNotNull(synchronizer.head)
        val first = checkNotNull(synchronizer.tail.load())
        val gaveUp = ConditionNode()

        // The node gave up, and the thread behind it skipped it
        gaveUp.prev = head
        gaveUp.next = first
        gaveUp.cancelled = true

        // The signal writes its link last
        synchronizer.link(head, gaveUp)

        assertSame(first, head.next)

        val release = TimeSource.Monotonic.markNow()

        synchronizer.state.store(FREE)
        synchronizer.signalNext()

        assertNull(waiter())

        // The release woke the thread up, not the end of its own time
        assertTrue(release.elapsedNow() < AcquireTimeout / 2)
    }

    @Test
    fun aSignalLeavesItsLinkToAThreadThatHasNotSkippedYet() {
        val synchronizer = Synchronizer()
        val head = ExclusiveNode()
        val gaveUp = ConditionNode()
        val behind = ExclusiveNode()

        // The thread behind still has the node that gave up as its predecessor
        gaveUp.prev = head
        gaveUp.next = behind
        gaveUp.cancelled = true
        behind.prev = gaveUp

        synchronizer.link(head, gaveUp)

        assertSame(gaveUp, head.next)

        // Its own link comes after the one of the signal, and is the one that stays
        synchronizer.skip(behind, gaveUp)

        assertSame(behind, head.next)
        assertSame(head, behind.prev)
    }

    @Test
    fun aSignalLinksTheFirstThreadBehindThatDidNotGiveUp() {
        val synchronizer = Synchronizer()
        val head = ExclusiveNode()
        val gaveUp = ConditionNode()
        val gaveUpToo = ExclusiveNode()
        val behind = ExclusiveNode()

        // Two nodes gave up in a row, and the thread behind them skipped both
        gaveUp.prev = head
        gaveUp.next = gaveUpToo
        gaveUp.cancelled = true
        gaveUpToo.prev = head
        gaveUpToo.next = behind
        gaveUpToo.cancelled = true
        behind.prev = head

        synchronizer.link(head, gaveUp)

        assertSame(behind, head.next)
    }

    @Test
    fun aSignalLinksItsNodeWhenItsThreadStillWaits() {
        val synchronizer = Synchronizer()
        val head = ExclusiveNode()
        val node = ConditionNode()

        node.prev = head

        synchronizer.link(head, node)

        assertSame(node, head.next)
    }
}
