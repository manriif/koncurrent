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
 * A hash set that any number of threads can read and update at the same time. It is backed by
 * a [ConcurrentHashMap] and behaves as its keys do.
 *
 * [contains] never blocks. [add] and [remove] are atomic. The bulk functions are not. [size]
 * and [isEmpty] give a transient state while other threads update the set.
 *
 * Its iterators are weakly consistent. They never throw [ConcurrentModificationException].
 * They visit once each element that existed when they were created, and may or may not reflect
 * later updates.
 *
 * Elements cannot be `null`.
 *
 * @param E the type of the elements
 */
public class ConcurrentHashSet<E : Any> private constructor(
    private val map: ConcurrentHashMap<E, Unit>,
) : MutableSet<E> by map.keys {

    override val size: Int
        get() = map.size

    /**
     * Creates an empty set with the default initial table size of 16 bins.
     */
    public constructor() : this(ConcurrentHashMap())

    /**
     * Creates an empty set that can hold [initialCapacity] elements without resizing.
     *
     * @throws IllegalArgumentException if [initialCapacity] is negative
     */
    public constructor(initialCapacity: Int) : this(ConcurrentHashMap(initialCapacity))

    override fun isEmpty(): Boolean = map.isEmpty()

    override fun contains(element: E): Boolean = map.containsKey(element)

    /**
     * Adds [element] unless the set already holds it. The check and the addition are one
     * atomic operation, so among the threads that add the same element at once, only one gets
     * `true`.
     *
     * @return whether the element was added
     */
    @IgnorableReturnValue
    override fun add(element: E): Boolean = map.putIfAbsent(element, Unit) == null

    @IgnorableReturnValue
    override fun addAll(elements: Collection<E>): Boolean {
        var added = false

        for (element in elements) {
            if (add(element)) {
                added = true
            }
        }

        return added
    }

    @IgnorableReturnValue
    override fun remove(element: E): Boolean = map.remove(element) != null

    override fun clear(): Unit = map.clear()

    override fun equals(other: Any?): Boolean = map.keys == other

    override fun hashCode(): Int = map.keys.hashCode()

    override fun toString(): String = map.keys.toString()
}
