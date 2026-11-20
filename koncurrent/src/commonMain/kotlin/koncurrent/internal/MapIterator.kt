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
 * Part of the port of the ConcurrentHashMap of JSR-166, which was written by Doug Lea with
 * assistance from members of JCP JSR-166 Expert Group and released to the public domain, as
 * explained at http://creativecommons.org/publicdomain/zero/1.0/
 */
// Kotlin/Native gives each function its own frame of GC roots, even where LLVM inlines it. The
// small functions of the hot paths are inlined by Kotlin, which merges their frames.
@file:Suppress("NOTHING_TO_INLINE")

package koncurrent.internal

/**
 * Base of the key, value and entry iterators. They are weakly consistent.
 */
internal abstract class MapIterator<K : Any, V : Any, E>(
    protected val map: PortableConcurrentHashMap<K, V>,
) : Traverser<K, V>(map.table), MutableIterator<E> {

    // Not private, for the inline function
    var lastReturned: Node<K, V>? = null

    init {
        advance()
    }

    final override fun hasNext(): Boolean = nextNode != null

    /**
     * Returns the node of the next element and moves on. Each iterator gives its element from
     * it, which spares a virtual call for each element.
     */
    inline fun take(): Node<K, V> {
        val node = nextNode ?: throw NoSuchElementException()

        lastReturned = node
        advance()

        return node
    }

    final override fun remove() {
        val node = checkNotNull(lastReturned)

        lastReturned = null
        val _ = map.remove(node.key)
    }
}

internal class KeyIterator<K : Any, V : Any>(
    map: PortableConcurrentHashMap<K, V>,
) : MapIterator<K, V, K>(map) {

    override fun next(): K = take().key
}

internal class ValueIterator<K : Any, V : Any>(
    map: PortableConcurrentHashMap<K, V>,
) : MapIterator<K, V, V>(map) {

    override fun next(): V = take().value
}

internal class EntryIterator<K : Any, V : Any>(
    map: PortableConcurrentHashMap<K, V>,
) : MapIterator<K, V, MutableMap.MutableEntry<K, V>>(map) {

    override fun next(): MutableMap.MutableEntry<K, V> {
        val node = take()

        return MapEntry(node.key, node.value, map)
    }
}

/**
 * The entry given by an [EntryIterator]. It is a snapshot whose [setValue] writes through to
 * the map.
 */
private class MapEntry<K : Any, V : Any>(
    override val key: K,
    value: V,
    private val map: PortableConcurrentHashMap<K, V>,
) : MutableMap.MutableEntry<K, V> {

    override var value: V = value
        private set

    @IgnorableReturnValue
    override fun setValue(newValue: V): V {
        @Suppress("CheckResult")
        val oldValue = value

        value = newValue
        map[key] = newValue

        return oldValue
    }

    override fun equals(other: Any?): Boolean =
        other is Map.Entry<*, *> && other.key == key && other.value == value

    override fun hashCode(): Int = key.hashCode() xor value.hashCode()

    override fun toString(): String = "$key=$value"
}
