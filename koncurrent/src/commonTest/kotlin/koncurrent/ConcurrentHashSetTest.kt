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

/**
 * Tests [ConcurrentHashSet].
 */
class ConcurrentHashSetTest {

    @Test
    fun emptySetHoldsNothing() {
        val set = ConcurrentHashSet<String>()

        assertEquals(0, set.size)
        assertTrue(set.isEmpty())
        assertFalse("a" in set)
        assertFalse(set.remove("a"))
        assertFalse(set.iterator().hasNext())
        assertEquals("[]", set.toString())
    }

    @Test
    fun addKeepsAnElementOnce() {
        val set = ConcurrentHashSet<String>()

        assertTrue(set.add("a"))
        assertFalse(set.add("a"))
        assertTrue(set.add("b"))

        assertEquals(2, set.size)
        assertTrue("a" in set)
        assertTrue(set.containsAll(listOf("a", "b")))
        assertFalse(set.containsAll(listOf("a", "c")))
    }

    @Test
    fun addAllTellsWhetherTheSetChanged() {
        val set = ConcurrentHashSet<Int>()

        assertTrue(set.addAll(listOf(1, 2, 3)))
        assertTrue(set.addAll(listOf(3, 4)))
        assertFalse(set.addAll(listOf(1, 4)))

        assertEquals(setOf(1, 2, 3, 4), set)
    }

    @Test
    fun removalsDropElements() {
        val set = ConcurrentHashSet<Int>()
        set.addAll(0..<100)

        assertTrue(set.remove(1))
        assertFalse(set.remove(1))
        assertTrue(set.removeAll(listOf(2, 3, 1_000)))
        assertTrue(set.retainAll((0..<10).toSet()))

        assertEquals(setOf(0, 4, 5, 6, 7, 8, 9), set)

        val iterator = set.iterator()
        val removed = iterator.next()

        iterator.remove()
        assertFalse(removed in set)
        assertEquals(6, set.size)

        set.clear()
        assertTrue(set.isEmpty())
    }

    @Test
    fun setGrowsThroughResizes() {
        val set = ConcurrentHashSet<Int>(initialCapacity = 4)

        repeat(10_000) { assertTrue(set.add(it)) }

        assertEquals(10_000, set.size)
        assertEquals((0..<10_000).toSet(), set.toSet())
        assertFailsWith<IllegalArgumentException> { ConcurrentHashSet<Int>(-1) }
    }

    @Test
    fun equalsHashCodeAndToStringMatchHashSet() {
        val source = hashSetOf("a", "b", "c")
        val set = ConcurrentHashSet<String>()
        set.addAll(source)

        assertEquals<Set<String>>(source, set)
        assertEquals<Set<String>>(set, source)
        assertEquals(source.hashCode(), set.hashCode())
        assertFalse(set == hashSetOf("a", "b"))

        val other = ConcurrentHashSet<String>()
        other.addAll(source)
        assertEquals(set, other)

        set.clear()
        set.add("a")
        assertEquals("[a]", set.toString())
    }
}
