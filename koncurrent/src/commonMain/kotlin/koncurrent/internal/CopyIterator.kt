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

/**
 * Goes through a copy of the elements of a port, as they were when it was created. It never
 * fails when the port changes meanwhile.
 *
 * [remove] gives the element that [next] returned last to [removeSame], which removes that very
 * object from the port if it is still there. An equal one could be another element.
 */
internal class CopyIterator<E>(
    private val elements: Array<Any?>,
    private val removeSame: (Any?) -> Unit,
) : MutableIterator<E> {

    private var cursor = 0
    private var lastReturned = -1

    override fun hasNext(): Boolean = cursor < elements.size

    @Suppress("UNCHECKED_CAST")
    override fun next(): E {
        if (cursor >= elements.size) {
            throw NoSuchElementException()
        }

        lastReturned = cursor

        return elements[cursor++] as E
    }

    override fun remove() {
        check(lastReturned >= 0)

        val element = elements[lastReturned]

        lastReturned = -1
        removeSame(element)
    }
}
