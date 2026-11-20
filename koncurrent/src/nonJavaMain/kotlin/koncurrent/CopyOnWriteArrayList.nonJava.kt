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
import koncurrent.internal.read

public actual class CopyOnWriteArrayList<E> private constructor(
    private val list: PortableCopyOnWriteArrayList<E>,
) : MutableList<E> by list {

    actual override val size: Int
        get() = list.elements().size

    public actual constructor() : this(PortableCopyOnWriteArrayList())

    public actual constructor(elements: Collection<E>) : this(
        PortableCopyOnWriteArrayList(elements),
    )

    actual override fun get(index: Int): E = list.elements().read(index)

    @IgnorableReturnValue
    public actual fun addIfAbsent(element: E): Boolean = list.addIfAbsent(element)

    @IgnorableReturnValue
    public actual fun addAllAbsent(elements: Collection<E>): Int = list.addAllAbsent(elements)

    override fun equals(other: Any?): Boolean = list == other

    override fun hashCode(): Int = list.hashCode()

    override fun toString(): String = list.toString()
}
