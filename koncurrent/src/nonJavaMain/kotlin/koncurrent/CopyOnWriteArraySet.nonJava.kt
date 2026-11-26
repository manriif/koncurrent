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

import koncurrent.internal.PortableCopyOnWriteArrayList

public actual class CopyOnWriteArraySet<E> private constructor(
    private val list: PortableCopyOnWriteArrayList<E>,
) : MutableSet<E> {

    actual override val size: Int
        get() = list.size

    public actual constructor() : this(PortableCopyOnWriteArrayList())

    public actual constructor(elements: Collection<E>) : this() {
        val _ = list.addAllAbsent(elements)
    }

    actual override fun isEmpty(): Boolean = list.isEmpty()

    actual override fun contains(element: E): Boolean = list.contains(element)

    actual override fun containsAll(elements: Collection<E>): Boolean = list.containsAll(elements)

    actual override fun iterator(): MutableIterator<E> = list.iterator()

    @IgnorableReturnValue
    actual override fun add(element: E): Boolean = list.addIfAbsent(element)

    @IgnorableReturnValue
    actual override fun addAll(elements: Collection<E>): Boolean = list.addAllAbsent(elements) > 0

    @IgnorableReturnValue
    actual override fun remove(element: E): Boolean = list.remove(element)

    @IgnorableReturnValue
    actual override fun removeAll(elements: Collection<E>): Boolean = list.removeAll(elements)

    @IgnorableReturnValue
    actual override fun retainAll(elements: Collection<E>): Boolean = list.retainAll(elements)

    actual override fun clear(): Unit = list.clear()

    // Compared with the elements of one moment, as a set is: whatever their order
    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }

        if (other !is Set<*>) {
            return false
        }

        val elements = list.elements()

        return other.size == elements.size && elements.all { it in other }
    }

    override fun hashCode(): Int = list.elements().sumOf { it.hashCode() }

    override fun toString(): String = list.toString()
}
