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
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A copy-on-write list under test, whatever its type.
 */
internal class TestList<E>(
    val list: MutableList<E>,
    val addIfAbsent: (E) -> Boolean,
    val addAllAbsent: (Collection<E>) -> Int,
)

/**
 * Tests what one thread sees of a copy-on-write list.
 */
abstract class ListContractTest {

    internal abstract fun <E> newList(elements: Collection<E>): TestList<E>

    private fun <E> listOfElements(vararg elements: E): MutableList<E> =
        newList(elements.asList()).list

    @Test
    fun emptyListHoldsNothing() {
        val list = listOfElements<String>()

        assertEquals(0, list.size)
        assertTrue(list.isEmpty())
        assertFalse("a" in list)
        assertEquals(-1, list.indexOf("a"))
        assertFalse(list.iterator().hasNext())
        assertEquals(emptyList(), list)
        assertEquals("[]", list.toString())
        assertFailsWith<IndexOutOfBoundsException> { list[0] }
    }

    @Test
    fun constructorCopiesTheElements() {
        val source = mutableListOf("a", "b")
        val list = newList(source).list

        source += "c"

        assertEquals(listOf("a", "b"), list)
    }

    @Test
    fun addAppendsAndInserts() {
        val list = listOfElements<String>()

        assertTrue(list.add("a"))
        assertTrue(list.add("c"))
        list.add(1, "b")
        list.add(3, "d")
        list.add(0, "z")

        assertEquals(listOf("z", "a", "b", "c", "d"), list)
        assertEquals(5, list.size)
        assertEquals("b", list[2])
        assertFailsWith<IndexOutOfBoundsException> { list.add(6, "e") }
        assertFailsWith<IndexOutOfBoundsException> { list.add(-1, "e") }
    }

    @Test
    fun addAllAppendsAndInserts() {
        val list = listOfElements("a", "d")

        assertTrue(list.addAll(listOf("e", "f")))
        assertTrue(list.addAll(1, listOf("b", "c")))
        assertFalse(list.addAll(emptyList()))

        assertEquals(listOf("a", "b", "c", "d", "e", "f"), list)
        assertFailsWith<IndexOutOfBoundsException> { list.addAll(7, listOf("g")) }
    }

    @Test
    fun searchFindsTheFirstAndTheLastOccurrence() {
        val list = listOfElements("a", "b", "a", "c")

        assertEquals(0, list.indexOf("a"))
        assertEquals(2, list.lastIndexOf("a"))
        assertEquals(-1, list.lastIndexOf("d"))
        assertTrue("c" in list)
        assertTrue(list.containsAll(listOf("a", "c")))
        assertFalse(list.containsAll(listOf("a", "d")))
    }

    @Test
    fun setReplacesAndReturnsThePreviousElement() {
        val list = listOfElements("a", "b")

        assertEquals("b", list.set(1, "c"))
        list[0] = "d"

        assertEquals(listOf("d", "c"), list)
        assertFailsWith<IndexOutOfBoundsException> { list[2] = "e" }
    }

    @Test
    fun removeDropsOneOccurrence() {
        val list = listOfElements("a", "b", "a", "c")

        assertTrue(list.remove("a"))
        assertFalse(list.remove("d"))
        assertEquals(listOf("b", "a", "c"), list)

        assertEquals("a", list.removeAt(1))
        assertEquals(listOf("b", "c"), list)
        assertFailsWith<IndexOutOfBoundsException> { list.removeAt(2) }
    }

    @Test
    fun bulkRemovalsKeepTheOrder() {
        val list = listOfElements(1, 2, 3, 4, 5, 6)

        assertTrue(list.removeAll(listOf(2, 4)))
        assertFalse(list.removeAll(listOf(7)))
        assertEquals(listOf(1, 3, 5, 6), list)

        assertTrue(list.retainAll(listOf(5, 1, 9)))
        assertFalse(list.retainAll(listOf(1, 5)))
        assertEquals(listOf(1, 5), list)

        list.clear()
        assertTrue(list.isEmpty())
    }

    @Test
    fun nullIsAnElementLikeAnother() {
        val list = listOfElements<String?>("a")

        list += null

        assertTrue(null in list)
        assertEquals(1, list.indexOf(null))
        assertNull(list[1])
        assertTrue(list.remove(null))
        assertEquals(listOf<String?>("a"), list)
    }

    @Test
    fun equalsHashCodeAndToStringMatchArrayList() {
        val source = arrayListOf("a", "b", "c")
        val list = newList(source).list

        assertEquals<List<String>>(source, list)
        assertEquals<List<String>>(list, source)
        assertEquals(source.hashCode(), list.hashCode())
        assertEquals(source.toString(), list.toString())
        assertNotEquals(list, listOf("a", "b"))
        assertNotEquals(list, listOf("a", "c", "b"))
        assertEquals(list, newList(source).list)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Absent elements
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun addIfAbsentOnlyAddsANewElement() {
        val list = newList(listOf("a"))

        assertFalse(list.addIfAbsent("a"))
        assertTrue(list.addIfAbsent("b"))
        assertFalse(list.addIfAbsent("b"))

        assertEquals(listOf("a", "b"), list.list)
    }

    @Test
    fun addAllAbsentAddsEachNewElementOnce() {
        val list = newList(listOf("a", "b"))

        assertEquals(2, list.addAllAbsent(listOf("b", "c", "a", "d", "c")))
        assertEquals(0, list.addAllAbsent(listOf("a", "d")))
        assertEquals(0, list.addAllAbsent(emptyList()))

        assertEquals(listOf("a", "b", "c", "d"), list.list)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Iterators
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun iteratorGoesThroughTheElementsOfItsCreation() {
        val list = listOfElements("a", "b", "c")
        val iterator = list.iterator()

        list.remove("b")
        list += "d"
        list[0] = "z"

        assertEquals(listOf("a", "b", "c"), iterator.asSequence().toList())
        assertEquals(listOf("z", "c", "d"), list)
    }

    @Test
    fun iterationSurvivesUpdatesOfTheList() {
        val list = listOfElements(1, 2, 3, 4)
        val seen = mutableListOf<Int>()

        for (element in list) {
            seen += element
            list.remove(element)
            list += element * 10
        }

        assertEquals(listOf(1, 2, 3, 4), seen)
        assertEquals(listOf(10, 20, 30, 40), list)
    }

    @Test
    fun iteratorCannotChangeTheList() {
        val list = listOfElements("a", "b")
        val iterator = list.listIterator()

        assertEquals("a", iterator.next())

        assertFailsWith<UnsupportedOperationException> { iterator.remove() }
        assertFailsWith<UnsupportedOperationException> { iterator.set("c") }
        assertFailsWith<UnsupportedOperationException> { iterator.add("c") }
        assertEquals(listOf("a", "b"), list)
    }

    @Test
    fun listIteratorGoesBothWays() {
        val list = listOfElements("a", "b", "c")
        val iterator = list.listIterator(1)

        assertEquals(1, iterator.nextIndex())
        assertEquals(0, iterator.previousIndex())
        assertTrue(iterator.hasPrevious())
        assertEquals("b", iterator.next())
        assertEquals("c", iterator.next())
        assertFalse(iterator.hasNext())
        assertFailsWith<NoSuchElementException> { iterator.next() }

        assertEquals("c", iterator.previous())
        assertEquals("b", iterator.previous())
        assertEquals("a", iterator.previous())
        assertFalse(iterator.hasPrevious())
        assertFailsWith<NoSuchElementException> { iterator.previous() }

        assertFalse(list.listIterator(3).hasNext())
        assertFailsWith<IndexOutOfBoundsException> { list.listIterator(4) }
        assertFailsWith<IndexOutOfBoundsException> { list.listIterator(-1) }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Sub list
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun subListIsAViewOfTheList() {
        val list = listOfElements("a", "b", "c", "d", "e")
        val view = list.subList(1, 4)

        assertEquals(listOf("b", "c", "d"), view)
        assertEquals(3, view.size)
        assertEquals("c", view[1])
        assertEquals(1, view.indexOf("c"))
        assertFailsWith<IndexOutOfBoundsException> { view[3] }
    }

    @Test
    fun subListUpdatesTheList() {
        val list = listOfElements("a", "b", "c", "d", "e")
        val view = list.subList(1, 4)

        assertEquals("c", view.set(1, "x"))
        view.add(0, "y")
        view += "z"
        assertEquals("d", view.removeAt(3))

        assertEquals(listOf("y", "b", "x", "z"), view)
        assertEquals(listOf("a", "y", "b", "x", "z", "e"), list)

        view.clear()
        assertEquals(listOf("a", "e"), list)
    }

    @Test
    fun subListFailsOnceTheListChangedOtherwise() {
        val list = listOfElements("a", "b", "c")
        val view = list.subList(0, 2)

        list += "d"

        assertFailsWith<ConcurrentModificationException> { view[0] }
        assertFailsWith<ConcurrentModificationException> { view.size }
        assertFailsWith<ConcurrentModificationException> { view.add("e") }
    }

    @Test
    fun subListChecksItsRange() {
        val list = listOfElements("a", "b", "c")

        assertTrue(list.subList(1, 1).isEmpty())
        assertEquals(listOf("a", "b", "c"), list.subList(0, 3))
        assertFailsWith<IndexOutOfBoundsException> { list.subList(-1, 2) }
        assertFailsWith<IndexOutOfBoundsException> { list.subList(0, 4) }
        assertFails { list.subList(2, 1) }
    }
}

/**
 * Tests the public [CopyOnWriteArrayList].
 */
class CopyOnWriteArrayListTest : ListContractTest() {

    override fun <E> newList(elements: Collection<E>): TestList<E> {
        val list = CopyOnWriteArrayList(elements)

        return TestList(list, { list.addIfAbsent(it) }, { list.addAllAbsent(it) })
    }

    @Test
    fun defaultConstructorCreatesAnEmptyList() {
        assertTrue(CopyOnWriteArrayList<String>().isEmpty())
    }
}
