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
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tests what one thread sees of the public [CyclicBarrier]. A thread alone only passes a
 * barrier of one party.
 */
class CyclicBarrierTest {

    @Test
    fun partiesMustBePositive() {
        assertFailsWith<IllegalArgumentException> { CyclicBarrier(0) }
        assertFailsWith<IllegalArgumentException> { CyclicBarrier(-1, null) }
    }

    @Test
    fun barrierOfOnePartyOpensAtOnce() {
        val barrier = CyclicBarrier(1)

        assertEquals(1, barrier.getParties())
        assertEquals(0, barrier.await())
        assertEquals(0, barrier.await(1.milliseconds))

        assertEquals(0, barrier.getNumberWaiting())
        assertFalse(barrier.isBroken())
    }

    @Test
    fun actionRunsEachTimeTheBarrierOpens() {
        var runs = 0
        val barrier = CyclicBarrier(1) { runs++ }

        barrier.await()
        barrier.await(1.milliseconds)

        assertEquals(2, runs)
    }

    @Test
    fun actionThatFailsBreaksTheBarrier() {
        val barrier = CyclicBarrier(1) { throw IllegalArgumentException() }

        assertFailsWith<IllegalArgumentException> { barrier.await() }
        assertTrue(barrier.isBroken())

        assertFailsWith<BrokenBarrierException> { barrier.await() }
        assertFailsWith<BrokenBarrierException> { barrier.await(1.milliseconds) }
    }

    @Test
    fun resetRepairsABrokenBarrier() {
        var failing = true
        val barrier = CyclicBarrier(1) { check(!failing) }

        assertFailsWith<IllegalStateException> { barrier.await() }
        assertTrue(barrier.isBroken())

        failing = false
        barrier.reset()

        assertFalse(barrier.isBroken())
        assertEquals(0, barrier.await())
    }
}
