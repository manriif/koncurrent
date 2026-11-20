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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Tests the key, value and entry views of [PortableConcurrentHashMap].
 */
class ViewsTest {

    private val map = PortableConcurrentHashMap<String, Int>().apply {
        this["a"] = 1
        this["b"] = 2
        this["c"] = 3
    }

    @Test
    fun viewsReflectTheMap() {
        val keys = map.keys
        val values = map.values
        val entries = map.entries

        assertEquals(setOf("a", "b", "c"), keys)
        assertEquals(listOf(1, 2, 3), values.sorted())
        assertEquals(mapOf("a" to 1, "b" to 2, "c" to 3).entries, entries)

        map["d"] = 4
        assertNotEquals(map.remove("a"), null)

        assertEquals(3, keys.size)
        assertEquals(3, values.size)
        assertEquals(3, entries.size)
        assertTrue("d" in keys)
        assertFalse("a" in keys)
        assertTrue(4 in values)
        assertFalse(1 in values)
        assertEquals(setOf("b", "c", "d"), entries.map { it.key }.toSet())
    }

    @Test
    fun viewsAreTheSameOnEachAccess() {
        assertSame(map.keys, map.keys)
        assertSame(map.values, map.values)
        assertSame(map.entries, map.entries)
    }

    @Test
    fun keysRemoveFromTheMap() {
        val keys = map.keys

        assertTrue(keys.remove("a"))
        assertFalse(keys.remove("a"))
        assertNull(map["a"])

        assertTrue(keys.removeAll(setOf("b", "z")))
        assertEquals(mapOf("c" to 3), map)

        assertFalse(keys.retainAll(setOf("c")))
        assertTrue(keys.retainAll(setOf("z")))
        assertTrue(map.isEmpty())
    }

    @Test
    fun keysAndValuesDoNotSupportAdditions() {
        assertFailsWith<UnsupportedOperationException> { map.keys.add("d") }
        assertFailsWith<UnsupportedOperationException> { map.keys.addAll(listOf("d")) }
        assertFailsWith<UnsupportedOperationException> { map.values.add(4) }
        assertEquals(3, map.size)
    }

    @Test
    fun valuesRemoveFromTheMap() {
        val values = map.values
        map["d"] = 3

        assertTrue(values.remove(1))
        assertFalse(values.remove(1))
        assertNull(map["a"])

        // Only one of the two mappings to 3 goes
        assertTrue(values.remove(3))
        assertEquals(listOf(2, 3), values.sorted())

        assertTrue(values.removeAll(listOf(3)))
        assertEquals(mapOf("b" to 2), map)

        assertTrue(values.retainAll(listOf(9)))
        assertTrue(map.isEmpty())
    }

    @Test
    fun entriesAddAndRemoveFromTheMap() {
        val entries = map.entries
        val a = entries.first { it.key == "a" }
        val d = PortableConcurrentHashMap<String, Int>().apply { this["d"] = 4 }.entries.first()

        assertTrue(a in entries)
        assertFalse(d in entries)

        assertTrue(entries.add(d))
        assertFalse(entries.add(d))
        assertEquals(4, map["d"])

        assertTrue(entries.remove(a))
        assertFalse(entries.remove(a))
        assertNull(map["a"])

        // An entry is only removed along with its value
        map["d"] = 5
        assertFalse(d in entries)
        assertFalse(entries.remove(d))
        assertEquals(5, map["d"])
    }

    @Test
    fun entryWritesThroughToTheMap() {
        val entry = map.entries.first { it.key == "b" }

        assertEquals(2, entry.setValue(20))

        assertEquals(20, entry.value)
        assertEquals(20, map["b"])
        assertEquals("b=20", entry.toString())
        assertEquals(mapOf("b" to 20).entries.first(), entry)
        assertEquals(mapOf("b" to 20).entries.first().hashCode(), entry.hashCode())
    }

    @Test
    fun iteratorsRemoveFromTheMap() {
        val keys = map.keys.iterator()
        val removed = keys.next()

        keys.remove()
        assertFailsWith<IllegalStateException> { keys.remove() }
        assertNull(map[removed])
        assertEquals(2, map.size)

        val values = map.values.iterator()

        while (values.hasNext()) {
            val _ = values.next()
            values.remove()
        }

        assertTrue(map.isEmpty())
        assertFailsWith<NoSuchElementException> { values.next() }
    }

    @Test
    fun clearingAViewClearsTheMap() {
        map.keys.clear()
        assertTrue(map.isEmpty())

        map["a"] = 1
        map.values.clear()
        assertTrue(map.isEmpty())

        map["a"] = 1
        map.entries.clear()
        assertTrue(map.isEmpty())
        assertTrue(map.keys.isEmpty())
        assertEquals("[]", map.keys.toString())
    }
}
