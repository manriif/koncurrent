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
import kotlin.test.assertNull

private const val THREAD_COUNT = 8
private const val LOCAL_COUNT = 100
private const val ROUND_COUNT = 1_000

/**
 * Tests the public [ThreadLocal] on real threads.
 */
class ThreadLocalStressTest {

    @Test
    fun eachThreadSeesItsOwnValue() {
        val locals = List(LOCAL_COUNT) { ThreadLocal<Int>() }

        runConcurrently(THREAD_COUNT) { thread ->
            repeat(ROUND_COUNT) { round ->
                for ((index, local) in locals.withIndex()) {
                    val value = thread * LOCAL_COUNT + index + round

                    assertEquals(if (round == 0) null else value - 1, local.get())
                    local.set(value)
                }
            }
        }
    }

    @Test
    fun getOrSetGivesEachThreadItsOwnValue() {
        val local = ThreadLocal<IntArray>()
        val created = AtomicInt(0)

        runConcurrently(THREAD_COUNT) {
            repeat(ROUND_COUNT) {
                val counts = local.getOrSet {
                    val _ = created.incrementAndFetch()
                    IntArray(1)
                }

                counts[0]++
            }

            assertEquals(ROUND_COUNT, local.get()?.get(0))
        }

        assertEquals(THREAD_COUNT, created.load())
    }

    @Test
    fun valueOfAThreadIsNotSeenByAnother() {
        val local = ThreadLocal<String>()
        local.set("main")

        assertNull(
            startThread {
                assertNull(local.get())
                local.set("other")
                assertEquals("other", local.get())
                local.remove()
                assertNull(local.get())
            }(),
        )

        assertEquals("main", local.get())
    }
}
