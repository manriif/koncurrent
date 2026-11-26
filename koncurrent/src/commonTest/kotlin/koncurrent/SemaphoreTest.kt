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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tests what one thread sees of the public [Semaphore].
 */
open class SemaphoreTest {

    protected open val fair: Boolean
        get() = false

    private fun newSemaphore(permits: Int): Semaphore =
        if (fair) Semaphore(permits, true) else Semaphore(permits)

    @Test
    fun semaphoreTellsWhetherItIsFair() {
        assertEquals(fair, newSemaphore(1).isFair())
        assertFalse(Semaphore(1, false).isFair())
    }

    @Test
    fun permitsAreTakenAndGivenBack() {
        val semaphore = newSemaphore(2)

        semaphore.acquire()
        assertTrue(semaphore.tryAcquire())
        assertEquals(0, semaphore.availablePermits())
        assertFalse(semaphore.tryAcquire())

        semaphore.release()
        assertEquals(1, semaphore.availablePermits())

        assertTrue(semaphore.tryAcquire())
        assertFalse(semaphore.tryAcquire())
    }

    @Test
    fun releaseAddsPermitsBeyondTheFirstCount() {
        val semaphore = newSemaphore(0)

        assertFalse(semaphore.tryAcquire())

        semaphore.release()
        semaphore.release()

        assertEquals(2, semaphore.availablePermits())
    }

    @Test
    fun negativeCountNeedsReleasesFirst() {
        val semaphore = newSemaphore(-1)

        assertFalse(semaphore.tryAcquire())

        semaphore.release()
        assertEquals(0, semaphore.availablePermits())
        assertFalse(semaphore.tryAcquire())

        semaphore.release()
        assertTrue(semaphore.tryAcquire())
    }

    @Test
    fun withPermitReturnsTheValueOfItsAction() {
        val semaphore = newSemaphore(1)

        val value = semaphore.withPermit {
            assertEquals(0, semaphore.availablePermits())
            "value"
        }

        assertEquals("value", value)
        assertEquals(1, semaphore.availablePermits())
    }

    @Test
    fun withPermitReleasesWhenItsActionFails() {
        val semaphore = newSemaphore(1)

        assertFailsWith<IllegalArgumentException> {
            semaphore.withPermit { throw IllegalArgumentException() }
        }

        assertEquals(1, semaphore.availablePermits())
    }

    @Test
    fun timedTryAcquireTakesAPermitOrGivesUp() {
        val semaphore = newSemaphore(2)

        assertTrue(semaphore.tryAcquire(Duration.ZERO))
        assertTrue(semaphore.tryAcquire(1.milliseconds))
        assertFalse(semaphore.tryAcquire(Duration.ZERO))
        assertFalse(semaphore.tryAcquire(1.milliseconds))
        assertEquals(0, semaphore.availablePermits())

        semaphore.release()

        assertTrue(semaphore.tryAcquire(1.milliseconds))
    }
}

/**
 * Tests what one thread sees of the public [Semaphore] when it is fair.
 */
class FairSemaphoreTest : SemaphoreTest() {

    override val fair: Boolean
        get() = true
}
