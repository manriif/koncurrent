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
/*
 * Written for this library. It is not a port: the CopyOnWriteArrayList of JSR-166 carries a Sun
 * copyright, where the classes that are ported are in the public domain.
 */

// Kotlin/Native gives each function its own frame of GC roots, even where LLVM inlines it. The
// small functions of the hot paths are inlined by Kotlin, which merges their frames.
@file:Suppress("NOTHING_TO_INLINE")

package koncurrent.internal

import kotlin.concurrent.atomics.AtomicReference

/**
 * A list whose every update installs a new copy of its elements, with the behavior of the
 * `CopyOnWriteArrayList` of java.util.concurrent. It backs the public list where the platform
 * has none.
 *
 * A read never waits, and sees the elements as one update left them. An iterator goes through
 * the elements as they were at its creation, and cannot change the list.
 *
 * The list of java.util.concurrent guards its updates with a lock. Here an update replaces the
 * elements atomically, and starts over when another update came first.
 */
internal class PortableCopyOnWriteArrayList<E>(elements: Collection<E>) : MutableList<E> {

    // The array it holds is never modified. Not private, for the inline function.
    val snapshot = AtomicReference(arrayOfElements(elements))

    override val size: Int
        get() = snapshot.load().size

    constructor() : this(emptyList())

    /**
     * Returns the elements as the last update left them. A class that wraps this list inlines
     * it in its own reads. The array must stay in generic code. On the JVM it is an array of
     * objects, which code that knows the type of the elements would fail to cast.
     */
    inline fun elements(): Array<E> = snapshot.load()

    ///////////////////////////////////////////////////////////////////////////
    // Reads
    ///////////////////////////////////////////////////////////////////////////

    override fun isEmpty(): Boolean = snapshot.load().isEmpty()

    override fun contains(element: E): Boolean = snapshot.load().indexOf(element) >= 0

    override fun containsAll(elements: Collection<E>): Boolean {
        val current = snapshot.load()

        return elements.all { current.indexOf(it) >= 0 }
    }

    override fun get(index: Int): E = snapshot.load().read(index)

    override fun indexOf(element: E): Int = snapshot.load().indexOf(element)

    override fun lastIndexOf(element: E): Int = snapshot.load().lastIndexOf(element)

    override fun iterator(): MutableIterator<E> = SnapshotIterator(snapshot.load(), 0)

    override fun listIterator(): MutableListIterator<E> = SnapshotIterator(snapshot.load(), 0)

    override fun listIterator(index: Int): MutableListIterator<E> {
        val elements = snapshot.load()

        checkPosition(index, elements.size)
        return SnapshotIterator(elements, index)
    }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<E> {
        val elements = snapshot.load()

        if (fromIndex < 0 || toIndex > elements.size || fromIndex > toIndex) {
            throw IndexOutOfBoundsException(
                "fromIndex: $fromIndex, toIndex: $toIndex, size: ${elements.size}",
            )
        }

        return SubList(elements, fromIndex, toIndex - fromIndex)
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }

        if (other !is List<*>) {
            return false
        }

        // Goes through the other list with one iterator, should it change meanwhile
        val others = other.iterator()

        for (element in snapshot.load()) {
            if (!others.hasNext() || element != others.next()) {
                return false
            }
        }

        return !others.hasNext()
    }

    override fun hashCode(): Int = snapshot.load().fold(1) { hash, element ->
        31 * hash + element.hashCode()
    }

    override fun toString(): String = snapshot.load().joinToString(prefix = "[", postfix = "]")

    ///////////////////////////////////////////////////////////////////////////
    // Updates
    ///////////////////////////////////////////////////////////////////////////

    @IgnorableReturnValue
    override fun add(element: E): Boolean {
        update { it.inserted(it.size, element) }
        return true
    }

    override fun add(index: Int, element: E) {
        update { current ->
            checkPosition(index, current.size)
            current.inserted(index, element)
        }
    }

    @IgnorableReturnValue
    override fun addAll(elements: Collection<E>): Boolean {
        val added = arrayOfElements(elements)

        if (added.isEmpty()) {
            return false
        }

        update { it.inserted(it.size, added) }
        return true
    }

    @IgnorableReturnValue
    override fun addAll(index: Int, elements: Collection<E>): Boolean {
        val added = arrayOfElements(elements)

        update { current ->
            checkPosition(index, current.size)

            if (added.isEmpty()) {
                return false
            }

            current.inserted(index, added)
        }

        return true
    }

    @IgnorableReturnValue
    override fun set(index: Int, element: E): E {
        while (true) {
            val current = snapshot.load()
            val previous = current.read(index)
            val copy = current.copyOf()

            copy[index] = element

            if (snapshot.compareAndSet(current, copy)) {
                return previous
            }
        }
    }

    @IgnorableReturnValue
    override fun remove(element: E): Boolean {
        update { current ->
            val index = current.indexOf(element)

            if (index < 0) {
                return false
            }

            current.removed(index)
        }

        return true
    }

    @IgnorableReturnValue
    override fun removeAt(index: Int): E {
        while (true) {
            val current = snapshot.load()
            val previous = current.read(index)

            if (snapshot.compareAndSet(current, current.removed(index))) {
                return previous
            }
        }
    }

    @IgnorableReturnValue
    override fun removeAll(elements: Collection<E>): Boolean = retain { it !in elements }

    @IgnorableReturnValue
    override fun retainAll(elements: Collection<E>): Boolean = retain { it in elements }

    override fun clear(): Unit = snapshot.store(arrayOfElements(emptyList()))

    /**
     * Adds [element] unless the list holds it. Returns whether it was added.
     */
    @IgnorableReturnValue
    fun addIfAbsent(element: E): Boolean {
        update { current ->
            if (current.indexOf(element) >= 0) {
                return false
            }

            current.inserted(current.size, element)
        }

        return true
    }

    /**
     * Adds the [elements] that the list does not hold, each one once. Returns how many were
     * added.
     */
    @IgnorableReturnValue
    fun addAllAbsent(elements: Collection<E>): Int {
        val candidates = arrayOfElements(elements)

        while (true) {
            val current = snapshot.load()
            val absent = ArrayList<E>()

            for (candidate in candidates) {
                if (current.indexOf(candidate) < 0 && candidate !in absent) {
                    absent.add(candidate)
                }
            }

            if (absent.isEmpty()) {
                return 0
            }

            val changed = current.inserted(current.size, arrayOfElements(absent))

            if (snapshot.compareAndSet(current, changed)) {
                return absent.size
            }
        }
    }

    /**
     * Makes the array that [change] returns the elements. Starts over when another update came
     * first. [change] may leave the calling function instead, when there is nothing to update.
     */
    private inline fun update(change: (current: Array<E>) -> Array<E>) {
        while (true) {
            val current = snapshot.load()

            if (snapshot.compareAndSet(current, change(current))) {
                return
            }
        }
    }

    /**
     * Keeps the elements that [keep] accepts. Returns whether some were dropped.
     */
    private inline fun retain(keep: (E) -> Boolean): Boolean {
        update { current ->
            val kept = ArrayList<E>(current.size)

            for (element in current) {
                if (keep(element)) {
                    kept.add(element)
                }
            }

            if (kept.size == current.size) {
                return false
            }

            arrayOfElements(kept)
        }

        return true
    }

    ///////////////////////////////////////////////////////////////////////////
    // Sub list
    ///////////////////////////////////////////////////////////////////////////

    /**
     * A view of [length] elements of the list, from [offset]. It only holds while the list is
     * updated through it. It fails once another update changed the list.
     */
    private inner class SubList(
        private var expected: Array<E>,
        private val offset: Int,
        private var length: Int,
    ) : AbstractMutableList<E>() {

        override val size: Int
            get() {
                checkForComodification()
                return length
            }

        override fun get(index: Int): E {
            checkIndex(index, length)
            checkForComodification()

            return expected[offset + index]
        }

        @IgnorableReturnValue
        override fun set(index: Int, element: E): E {
            checkIndex(index, length)

            val previous = expected[offset + index]
            val copy = expected.copyOf()

            copy[offset + index] = element
            install(copy)

            return previous
        }

        override fun add(index: Int, element: E) {
            checkPosition(index, length)
            install(expected.inserted(offset + index, element))
            length++
        }

        @IgnorableReturnValue
        override fun removeAt(index: Int): E {
            checkIndex(index, length)

            val previous = expected[offset + index]

            install(expected.removed(offset + index))
            length--

            return previous
        }

        private fun checkForComodification() {
            if (snapshot.load() !== expected) {
                throw ConcurrentModificationException()
            }
        }

        private fun install(changed: Array<E>) {
            if (!snapshot.compareAndSet(expected, changed)) {
                throw ConcurrentModificationException()
            }

            expected = changed
        }
    }
}

/**
 * Goes through [elements], which never change, from [cursor].
 */
private class SnapshotIterator<E>(
    private val elements: Array<E>,
    private var cursor: Int,
) : MutableListIterator<E> {

    override fun hasNext(): Boolean = cursor < elements.size

    override fun hasPrevious(): Boolean = cursor > 0

    override fun nextIndex(): Int = cursor

    override fun previousIndex(): Int = cursor - 1

    override fun next(): E {
        if (cursor >= elements.size) {
            throw NoSuchElementException()
        }

        return elements[cursor++]
    }

    override fun previous(): E {
        if (cursor <= 0) {
            throw NoSuchElementException()
        }

        return elements[--cursor]
    }

    // The iterator of java.util.concurrent does not support the updates either

    override fun add(element: E): Unit = throw UnsupportedOperationException()

    override fun remove(): Unit = throw UnsupportedOperationException()

    override fun set(element: E): Unit = throw UnsupportedOperationException()
}

///////////////////////////////////////////////////////////////////////////
// Arrays of elements
///////////////////////////////////////////////////////////////////////////

// An array of a type parameter can only be created as an array of anything, as the standard
// collections do. These two functions hold the casts that this takes.

/**
 * Returns a new array of [size] elements. The caller fills it before it shows it.
 */
@Suppress("UNCHECKED_CAST")
internal fun <E> newArrayOfElements(size: Int): Array<E> = arrayOfNulls<Any?>(size) as Array<E>

/**
 * Returns the [elements] in a new array. It goes through them with one iterator, should they
 * change meanwhile.
 */
internal fun <E> arrayOfElements(elements: Collection<E>): Array<E> {
    val copy = ArrayList<E>()

    for (element in elements) {
        copy.add(element)
    }

    val array = newArrayOfElements<E>(copy.size)

    for (index in array.indices) {
        array[index] = copy[index]
    }

    return array
}

/**
 * Returns the element at [index]. An array of JavaScript gives `undefined` out of its bounds,
 * hence the check.
 */
internal inline fun <E> Array<E>.read(index: Int): E {
    checkIndex(index, size)
    return this[index]
}

/**
 * Returns a copy of this array with [element] at [index].
 */
private fun <E> Array<E>.inserted(index: Int, element: E): Array<E> {
    val copy = newArrayOfElements<E>(size + 1)

    val _ = copyInto(copy, endIndex = index)
    copy[index] = element
    val _ = copyInto(copy, destinationOffset = index + 1, startIndex = index)

    return copy
}

/**
 * Returns a copy of this array with [elements] at [index].
 */
private fun <E> Array<E>.inserted(index: Int, elements: Array<E>): Array<E> {
    val copy = newArrayOfElements<E>(size + elements.size)

    val _ = copyInto(copy, endIndex = index)
    val _ = elements.copyInto(copy, destinationOffset = index)
    val _ = copyInto(copy, destinationOffset = index + elements.size, startIndex = index)

    return copy
}

/**
 * Returns a copy of this array without the element at [index].
 */
private fun <E> Array<E>.removed(index: Int): Array<E> {
    val copy = newArrayOfElements<E>(size - 1)

    val _ = copyInto(copy, endIndex = index)
    val _ = copyInto(copy, destinationOffset = index, startIndex = index + 1)

    return copy
}

// Checks the index of an element to read or replace
internal fun checkIndex(index: Int, size: Int) {
    @Suppress("ConvertTwoComparisonsToRangeCheck")
    if (index < 0 || index >= size) {
        throw IndexOutOfBoundsException("index: $index, size: $size")
    }
}

// Checks the index of a place to insert at, which may be the end
private fun checkPosition(index: Int, size: Int) {
    @Suppress("ConvertTwoComparisonsToRangeCheck")
    if (index < 0 || index > size) {
        throw IndexOutOfBoundsException("index: $index, size: $size")
    }
}
