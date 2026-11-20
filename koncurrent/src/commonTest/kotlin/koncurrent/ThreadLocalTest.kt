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
import kotlin.test.assertNull

/**
 * Tests what one thread sees of the public [ThreadLocal].
 */
class ThreadLocalTest {

    @Test
    fun valueIsNullUntilSet() {
        val local = ThreadLocal<String>()

        assertNull(local.get())

        local.set("a")
        assertEquals("a", local.get())

        local.set("b")
        assertEquals("b", local.get())
    }

    @Test
    fun removeDropsTheValue() {
        val local = ThreadLocal<String>()
        local.set("a")

        local.remove()

        assertNull(local.get())
        local.remove()
    }

    @Test
    fun threadLocalsDoNotShareTheirValues() {
        val first = ThreadLocal<Int>()
        val second = ThreadLocal<Int>()

        first.set(1)
        second.set(2)

        assertEquals(1, first.get())
        assertEquals(2, second.get())

        first.remove()
        assertEquals(2, second.get())
    }

    @Test
    fun getOrSetCreatesTheValueOnce() {
        val local = ThreadLocal<String>()
        var calls = 0

        val created = local.getOrSet {
            calls++
            "a"
        }

        val kept = local.getOrSet {
            calls++
            "b"
        }

        assertEquals("a", created)
        assertEquals("a", kept)
        assertEquals("a", local.get())
        assertEquals(1, calls)
    }

    @Test
    fun nullableValueCanBeSetToNull() {
        val local = ThreadLocal<String?>()
        local.set("a")

        local.set(null)

        assertNull(local.get())
    }
}
