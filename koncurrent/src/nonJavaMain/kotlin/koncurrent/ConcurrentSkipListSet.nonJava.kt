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

import koncurrent.internal.NavigableSetAdapter
import koncurrent.internal.PortableNavigableMap
import koncurrent.internal.PortableNavigableSet

public actual class ConcurrentSkipListSet<E : Any> private constructor(
    private val set: NavigableSetAdapter<E>,
) : NavigableSet<E> by set {

    public actual constructor() : this(null)

    // An element is mapped to `true`, as in the original
    public actual constructor(comparator: Comparator<in E>?) : this(
        NavigableSetAdapter(PortableNavigableSet(PortableNavigableMap(comparator), marker = true)),
    )

    public actual constructor(elements: Collection<E>) : this() {
        addAll(elements)
    }

    public actual fun subSet(fromElement: E, toElement: E): NavigableSet<E> =
        subSet(fromElement, true, toElement, false)

    public actual fun headSet(toElement: E): NavigableSet<E> = headSet(toElement, false)

    public actual fun tailSet(fromElement: E): NavigableSet<E> = tailSet(fromElement, true)

    override fun equals(other: Any?): Boolean = set == other

    override fun hashCode(): Int = set.hashCode()

    override fun toString(): String = set.toString()
}
