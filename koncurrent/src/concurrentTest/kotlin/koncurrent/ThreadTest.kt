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

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private const val THREAD_COUNT = 64

private val ShortTime = 50.milliseconds
private val LongTime = 30.seconds
private val BlockedDelay = 50.milliseconds

/**
 * Tests [Thread] and its functions, on the Java class and on the native one.
 */
class ThreadTest {

    @Test
    fun aStartedThreadRunsItsTargetOnItself() {
        val caller = currentThread()
        var runner: Thread? = null
        val thread = Thread { runner = currentThread() }

        thread.start()
        thread.join()

        assertSame(thread, runner)
        assertNotSame(caller, runner)
    }

    @Test
    fun currentThreadIsOneObjectForAThread() {
        assertSame(currentThread(), currentThread())
    }

    @Test
    fun manyThreadsRunAndEnd() {
        val count = AtomicInt(0)
        val threads = List(THREAD_COUNT) { thread { val _ = count.incrementAndFetch() } }

        threads.forEach { it.join() }

        assertEquals(THREAD_COUNT, count.load())
        assertTrue(threads.none { it.isAlive() })
    }

    @Test
    fun aThreadIsAliveFromItsStartToItsEnd() {
        val release = CountDownLatch(1)
        val thread = Thread { release.await() }

        assertFalse(thread.isAlive())
        thread.start()
        assertTrue(thread.isAlive())

        release.countDown()
        thread.join()

        assertFalse(thread.isAlive())
    }

    @Test
    fun startingAThreadTwiceFails() {
        val thread = Thread {}

        thread.start()
        assertFailsWith<IllegalThreadStateException> { thread.start() }
        thread.join()
        assertFailsWith<IllegalThreadStateException> { thread.start() }
    }

    @Test
    fun joinDoesNotWaitForAThreadThatNeverStarted() {
        val thread = Thread {}

        thread.join()

        assertTrue(thread.join(LongTime))
    }

    @Test
    fun timedJoinTellsWhetherTheThreadStillRuns() {
        val release = CountDownLatch(1)
        val thread = thread { release.await() }
        val start = TimeSource.Monotonic.markNow()

        assertFalse(thread.join(ShortTime))
        assertTrue(start.elapsedNow() >= ShortTime)
        assertFalse(thread.join(Duration.ZERO))

        release.countDown()

        assertTrue(thread.join(LongTime))
        assertTrue(thread.join(Duration.ZERO))
    }

    @Test
    fun aThreadHasANameThatCanChange() {
        val thread = Thread {}

        assertTrue(thread.getName().startsWith("Thread-"))

        thread.setName("worker")

        assertEquals("worker", thread.getName())
    }

    @Test
    fun theBuilderSetsTheNameAndTheDaemonFlag() {
        var name: String? = null
        val thread = thread(start = false, isDaemon = true, name = "builder") {
            name = currentThread().getName()
        }

        assertFalse(thread.isAlive())
        assertTrue(thread.isDaemon())

        thread.start()
        thread.join()

        assertEquals("builder", name)
    }

    @Test
    fun theBuilderStartsTheThreadByDefault() {
        val release = CountDownLatch(1)
        val thread = thread { release.await() }

        assertTrue(thread.isAlive())
        assertFalse(thread.isDaemon())

        release.countDown()
        thread.join()
    }

    @Test
    fun theDaemonFlagDoesNotChangeOnceStarted() {
        val release = CountDownLatch(1)
        val thread = thread { release.await() }

        assertFailsWith<IllegalThreadStateException> { thread.setDaemon(true) }

        release.countDown()
        thread.join()
    }

    @Test
    fun sleepLastsAtLeastItsDuration() {
        val start = TimeSource.Monotonic.markNow()

        sleep(ShortTime)

        assertTrue(start.elapsedNow() >= ShortTime)
    }

    @Test
    fun sleepOfNoTimeReturnsAtOnce() {
        val start = TimeSource.Monotonic.markNow()

        sleep(Duration.ZERO)
        sleep((-1).seconds)

        assertTrue(start.elapsedNow() < LongTime)
    }

    @Test
    fun interruptedTellsTheFlagThenClearsIt() {
        val thread = TestThread {
            val current = currentThread()

            assertFalse(interrupted())

            current.interrupt()

            assertTrue(current.isInterrupted())
            assertTrue(current.isInterrupted())
            assertTrue(interrupted())
            assertFalse(interrupted())
            assertFalse(current.isInterrupted())
        }

        assertNull(thread.join())
    }

    @Test
    fun anInterruptEndsASleep() {
        val thread = TestThread {
            assertFailsWith<InterruptedException> { sleep(LongTime) }
            assertFalse(currentThread().isInterrupted())
        }

        spinFor(BlockedDelay)
        thread.interrupt()

        assertNull(thread.join())
    }

    @Test
    fun aSleepFailsAtOnceForAnInterruptedThread() {
        val thread = TestThread {
            currentThread().interrupt()

            assertFailsWith<InterruptedException> { sleep(LongTime) }
            assertFalse(interrupted())
        }

        assertNull(thread.join())
    }

    @Test
    fun anInterruptEndsAJoin() {
        val release = CountDownLatch(1)
        val target = thread { release.await() }
        val joiner = TestThread {
            assertFailsWith<InterruptedException> { target.join() }
            assertFailsWith<InterruptedException> {
                currentThread().interrupt()

                val _ = target.join(LongTime)
            }
        }

        spinFor(BlockedDelay)
        joiner.interrupt()

        assertNull(joiner.join())

        release.countDown()
        target.join()
    }

    @Test
    fun anExceptionEndsItsThreadAlone() {
        val thread = thread { error("Thrown by a test, to end its thread") }

        thread.join()

        assertFalse(thread.isAlive())
    }
}
