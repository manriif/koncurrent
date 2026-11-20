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
 * Visits the nodes of a map bin by bin. It visits once each node that was reachable when the
 * traversal started and still is. It may miss a node added to a bin already visited.
 *
 * A resize splits each bin of the initial table in two. Upon a forwarding node, the traversal
 * goes on in the next table, at the bin of the same index and at the one further by the size of
 * the table it left. A stack keeps its place in each table left, since a resize may move the
 * bins out of order.
 */
internal open class Traverser<K : Any, V : Any>(var table: Table<K, V>?) {

    // The properties are not private, for the inline functions

    val baseSize = table?.size ?: 0 // Size of the initial table
    var baseIndex = 0 // Current index in the initial table
    var index = 0 // Index of the bin to use next
    var stack: TableStack<K, V>? = null

    // The last node reached by advance
    var nextNode: Node<K, V>? = null

    /**
     * Moves to the next node and returns it, or `null` when there is none left.
     */
    @IgnorableReturnValue
    inline fun advance(): Node<K, V>? {
        val next = nextNode?.next

        if (next != null) {
            nextNode = next
            return next
        }

        return scanInOrder() ?: advanceAcrossTables()
    }

    /**
     * Moves to the first node of the next bin that has one, and returns it. It follows the
     * bins that a resize moved, and ends the traversal after the last bin.
     */
    fun advanceAcrossTables(): Node<K, V>? {
        var node: Node<K, V>? = null

        while (true) {
            if (node != null) {
                nextNode = node
                return node
            }

            val table = table
            val index = index

            if (baseIndex >= baseSize || table == null || table.size <= index || index < 0) {
                nextNode = null
                return null
            }

            when (val head = table.binAt(index)) {
                null -> Unit
                is ListNode -> node = head
                is TreeBin -> node = head.first
                is ReservationNode -> Unit

                is ForwardingNode -> {
                    // Saves the place in the table to leave
                    stack = TableStack(table, index, stack)
                    this.table = head.nextTable
                    continue
                }
            }

            if (stack != null) {
                recoverState(table.size)
            } else {
                this.index = index + baseSize

                // Visits the upper slots if present
                if (this.index >= table.size) {
                    this.index = ++baseIndex
                }
            }
        }
    }

    /**
     * Visits the bins of the initial table in order, up to the first node or to a bin that a
     * resize moved. Returns that node, or `null` for the general traversal to go on from there.
     *
     * This is the whole traversal when no resize runs. It spares the bookkeeping of the tables
     * left, which a traversal only needs across a resize.
     */
    inline fun scanInOrder(): Node<K, V>? {
        val table = table

        if (stack != null || table == null || table.size != baseSize || index != baseIndex) {
            return null
        }

        var bin = baseIndex
        var node: Node<K, V>? = null

        while (bin < baseSize && node == null) {
            val head = table.binAt(bin)

            if (head is ForwardingNode) {
                break
            }

            node = when (head) {
                is ListNode -> head
                is TreeBin -> head.first
                else -> null
            }

            bin++
        }

        baseIndex = bin
        index = bin

        if (node != null) {
            nextNode = node
        }

        return node
    }

    /**
     * Moves to the next bin of the current table, whose size is [tableSize]. Goes back to the
     * tables whose bin is fully visited.
     */
    private fun recoverState(tableSize: Int) {
        var size = tableSize

        while (true) {
            val saved = stack ?: break

            index += saved.table.size

            if (index < size) {
                return
            }

            size = saved.table.size
            index = saved.index
            table = saved.table
            stack = saved.next
        }

        index += baseSize

        if (index >= size) {
            index = ++baseIndex
        }
    }
}

/**
 * The place of a traversal in a table that it left for the next one.
 */
internal class TableStack<K : Any, V : Any>(
    val table: Table<K, V>,
    val index: Int,
    val next: TableStack<K, V>?,
)
