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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Tests what one thread sees of the public [CopyOnWriteArraySet].
 */
class CopyOnWriteArraySetTest {

    @Test
    fun emptySetHoldsNothing() {
        val set = CopyOnWriteArraySet<String>()

        assertEquals(0, set.size)
        assertTrue(set.isEmpty())
        assertFalse("a" in set)
        assertFalse(set.remove("a"))
        assertFalse(set.iterator().hasNext())
        assertEquals("[]", set.toString())
    }

    @Test
    fun addKeepsAnElementOnce() {
        val set = CopyOnWriteArraySet<String?>()

        assertTrue(set.add("a"))
        assertFalse(set.add("a"))
        assertTrue(set.add(null))
        assertFalse(set.add(null))

        assertEquals(2, set.size)
        assertTrue("a" in set)
        assertTrue(null in set)
    }

    @Test
    fun elementsKeepTheOrderOfTheirAddition() {
        val set = CopyOnWriteArraySet(listOf("c", "a", "c", "b", "a"))

        assertEquals(listOf("c", "a", "b"), set.toList())
        assertEquals("[c, a, b]", set.toString())

        assertTrue(set.addAll(listOf("a", "d", "d")))
        assertFalse(set.addAll(listOf("a", "b")))
        assertEquals(listOf("c", "a", "b", "d"), set.toList())
    }

    @Test
    fun removalsDropElements() {
        val set = CopyOnWriteArraySet(listOf("a", "b", "c", "d"))

        assertTrue(set.remove("b"))
        assertFalse(set.remove("b"))
        assertTrue(set.containsAll(listOf("a", "c")))
        assertFalse(set.containsAll(listOf("a", "b")))

        assertTrue(set.removeAll(listOf("a", "x")))
        assertFalse(set.removeAll(listOf("x")))
        assertTrue(set.retainAll(listOf("d")))
        assertEquals(listOf("d"), set.toList())

        set.clear()
        assertTrue(set.isEmpty())
    }

    @Test
    fun iteratorIsASnapshotThatCannotChangeTheSet() {
        val set = CopyOnWriteArraySet(listOf("a", "b"))
        val iterator = set.iterator()

        assertTrue(set.add("c"))
        assertTrue(set.remove("a"))

        assertEquals("a", iterator.next())
        assertFailsWith<UnsupportedOperationException> { iterator.remove() }
        assertEquals("b", iterator.next())
        assertFalse(iterator.hasNext())
        assertEquals(listOf("b", "c"), set.toList())
    }

    @Suppress("RemoveExplicitTypeArguments", "KotlinMisorderedAssertEqualsArguments")
    @Test
    fun equalsAndHashCodeAreThoseOfASet() {
        val set = CopyOnWriteArraySet(listOf("a", "b", null))
        val same = CopyOnWriteArraySet(listOf(null, "b", "a"))

        assertEquals(setOf("b", null, "a"), set)
        assertEquals(set, setOf("b", null, "a"))
        assertEquals(setOf("b", null, "a").hashCode(), set.hashCode())
        assertEquals(set, same)
        assertEquals(same.hashCode(), set.hashCode())

        assertNotEquals<Set<String?>>(setOf("a", "b"), set)
        assertNotEquals<Set<String?>>(setOf("a", "b", "c"), set)
        assertFalse(set.equals(listOf("a", "b", null)))
    }
}
