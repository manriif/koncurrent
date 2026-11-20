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
 * Tests what one thread sees of the public [CountDownLatch].
 */
class CountDownLatchTest {

    @Test
    fun countGoesDownToZeroAndStaysThere() {
        val latch = CountDownLatch(2)

        assertEquals(2L, latch.getCount())

        latch.countDown()
        assertEquals(1L, latch.getCount())

        latch.countDown()
        latch.countDown()
        assertEquals(0L, latch.getCount())
    }

    @Test
    fun awaitReturnsOnceTheCountIsZero() {
        val latch = CountDownLatch(1)

        latch.countDown()

        latch.await()
        latch.await()
    }

    @Test
    fun latchOfZeroIsOpen() {
        val latch = CountDownLatch(0)

        latch.await()

        assertEquals(0L, latch.getCount())
    }

    @Test
    fun negativeCountIsRejected() {
        assertFailsWith<IllegalArgumentException> { CountDownLatch(-1) }
    }

    @Test
    fun timedAwaitTellsWhetherTheGateIsOpen() {
        val latch = CountDownLatch(1)

        assertFalse(latch.await(Duration.ZERO))
        assertFalse(latch.await(1.milliseconds))

        latch.countDown()

        assertTrue(latch.await(Duration.ZERO))
        assertTrue(latch.await(1.milliseconds))
    }
}
