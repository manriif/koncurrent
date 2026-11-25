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

/**
 * A set that any number of threads can read and update at the same time, on a
 * [CopyOnWriteArrayList]. Every update makes a new copy of the elements.
 *
 * A read never blocks. An update costs a copy of the whole set, and finding an element costs a
 * look at each of them. So this set suits a few elements that are read often and rarely
 * changed, like a set of listeners. Use a [ConcurrentHashSet] for more.
 *
 * Each update is atomic. Among the threads that add the same element at once, only one gets
 * `true`.
 *
 * An iterator is a snapshot. It goes through the elements as they were when it was created, in
 * the order of their addition. It never throws [ConcurrentModificationException], and it
 * cannot change the set: its `remove` throws [UnsupportedOperationException].
 *
 * Elements can be `null`.
 *
 * ### Platforms
 *
 * - On the JVM and Android this is `java.util.concurrent.CopyOnWriteArraySet`.
 * - Elsewhere this is the same class over the list of this library.
 *
 * @param E the type of the elements
 */
public expect class CopyOnWriteArraySet<E> : MutableSet<E> {

    override val size: Int

    /**
     * Creates an empty set.
     */
    public constructor()

    /**
     * Creates a set that holds the given [elements] once each, in the order of their iterator.
     */
    public constructor(elements: Collection<E>)

    override fun isEmpty(): Boolean

    override fun contains(element: E): Boolean

    override fun containsAll(elements: Collection<E>): Boolean

    override fun iterator(): MutableIterator<E>

    @IgnorableReturnValue
    override fun add(element: E): Boolean

    @IgnorableReturnValue
    override fun addAll(elements: Collection<E>): Boolean

    @IgnorableReturnValue
    override fun remove(element: E): Boolean

    @IgnorableReturnValue
    override fun removeAll(elements: Collection<E>): Boolean

    @IgnorableReturnValue
    override fun retainAll(elements: Collection<E>): Boolean

    override fun clear()
}
