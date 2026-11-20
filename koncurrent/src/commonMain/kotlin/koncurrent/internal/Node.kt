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

import kotlin.concurrent.Volatile

/**
 * A mapping of a [PortableConcurrentHashMap], linked to the next mapping of its bin.
 */
internal sealed class Node<K : Any, V : Any>(
    val hash: Int,
    val key: K,
    @Volatile var value: V,
) {

    abstract val next: Node<K, V>?

    @Suppress("SuspiciousEqualsCombination")
    inline fun matches(hash: Int, key: K): Boolean =
        this.hash == hash && (this.key === key || key == this.key)

    /**
     * Returns the number of nodes of the list starting at this node.
     */
    fun count(): Int {
        var count = 0
        var node: Node<K, V>? = this

        while (node != null) {
            count++
            node = node.next
        }

        return count
    }
}

/**
 * A mapping of a bin arranged as a list. The first one heads the bin.
 */
internal class ListNode<K : Any, V : Any>(
    hash: Int,
    key: K,
    value: V,
    @Volatile override var next: ListNode<K, V>?,
) : Node<K, V>(hash, key, value), Bin<K, V> {

    /**
     * Returns the node holding [key] in the list starting at this node, or `null`.
     */
    inline fun find(hash: Int, key: K): ListNode<K, V>? {
        var node: ListNode<K, V>? = this

        while (node != null) {
            if (node.matches(hash, key)) {
                return node
            }

            node = node.next
        }

        return null
    }

    /**
     * Looks for [key] in the list starting at this node. Calls [found] with its node and the
     * node before it, or [missing] with the last node of the list. Returns the number of nodes
     * visited.
     */
    inline fun scan(
        hash: Int,
        key: K,
        found: (node: ListNode<K, V>, pred: ListNode<K, V>?) -> Unit,
        missing: (last: ListNode<K, V>) -> Unit,
    ): Int {
        var count = 1
        var pred: ListNode<K, V>? = null
        var node = this

        while (true) {
            if (node.matches(hash, key)) {
                found(node, pred)
                return count
            }

            val next = node.next

            if (next == null) {
                missing(node)
                return count
            }

            pred = node
            node = next
            count++
        }
    }
}
