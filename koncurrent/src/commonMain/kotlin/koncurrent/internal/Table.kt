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

import kotlin.concurrent.atomics.AtomicArray
import kotlin.concurrent.atomics.AtomicIntArray

// The checks that a thread makes at once while it waits for a bin. After them it gives way to
// the other threads between two checks. Measured on macOS with threads that all update one
// key, this is 1.3 to 3.5 times faster than checking without end. It costs 6% to 9% to eight
// threads and more that update 64 keys. Giving way sooner helps the first case and hurts the
// second one more.
private const val BUSY_CHECKS = 128

internal const val BIN_UNLOCKED = 0
internal const val BIN_LOCKED = 1

// On Kotlin/JS a new array holds `undefined`, which a comparison does not take for `null`. The
// insertion into an empty bin would then never succeed.
private val NewArrayLacksNulls =
    !AtomicArray(arrayOfNulls<Any>(1)).compareAndSetAt(0, null, Unit)

/**
 * The bins of a [PortableConcurrentHashMap], with one lock for each bin.
 *
 * Every update of a bin needs its lock, except the insertion of its first node. The original
 * locks the monitor of the head node. Kotlin atomics cannot update a field in place, so a node
 * cannot carry its lock. The lock words live in an array next to the bins instead.
 */
internal class Table<K : Any, V : Any>(val size: Int) {

    // Not private, for the inline functions
    val bins = AtomicArray(arrayOfNulls<Bin<K, V>>(size))
    val locks = AtomicIntArray(size)

    init {
        if (NewArrayLacksNulls) {
            repeat(size) { bins.storeAt(it, null) }
        }
    }

    inline fun binAt(index: Int): Bin<K, V>? = bins.loadAt(index)

    inline fun casBinAt(index: Int, expected: Bin<K, V>?, bin: Bin<K, V>?): Boolean =
        bins.compareAndSetAt(index, expected, bin)

    inline fun setBinAt(index: Int, bin: Bin<K, V>?): Unit = bins.storeAt(index, bin)

    /**
     * Returns the node holding [key], following the bins moved by a resize.
     */
    inline fun find(hash: Int, key: K): Node<K, V>? {
        var table = this

        while (true) {
            when (val head = table.binAt((table.size - 1) and hash)) {
                null -> return null
                is ListNode -> return head.find(hash, key)
                is TreeBin -> return head.find(hash, key)
                is ForwardingNode -> table = head.nextTable
                is ReservationNode -> return null
            }
        }
    }

    /**
     * Acquires the lock of the bin at [index]. Waits while another thread holds it.
     */
    inline fun lock(index: Int) {
        if (!locks.compareAndSetAt(index, BIN_UNLOCKED, BIN_LOCKED)) {
            awaitLock(index)
        }
    }

    fun awaitLock(index: Int) {
        var checks = 0

        do {
            while (locks.loadAt(index) != BIN_UNLOCKED) {
                if (++checks > BUSY_CHECKS) {
                    yieldThread()
                }
            }
        } while (!locks.compareAndSetAt(index, BIN_UNLOCKED, BIN_LOCKED))
    }

    inline fun unlock(index: Int): Unit = locks.storeAt(index, BIN_UNLOCKED)

    /**
     * Runs [block] with the lock of the bin at [index] held.
     */
    inline fun <T> withLock(index: Int, block: () -> T): T {
        lock(index)

        return try {
            block()
        } finally {
            unlock(index)
        }
    }

    /**
     * Locks the bin at [index], then runs [block] if [head] still heads it.
     */
    inline fun lockBin(index: Int, head: Bin<K, V>, block: () -> Unit) {
        withLock(index) {
            if (binAt(index) === head) {
                block()
            }
        }
    }

    /**
     * Reserves the empty bin at [index], then fills it with the node that [compute] returns.
     * Returns `false` when the bin is not empty.
     *
     * The reservation sends the other updates of the bin to its lock. So the lock is taken
     * before the reservation shows.
     */
    inline fun reserve(index: Int, compute: () -> ListNode<K, V>?): Boolean = withLock(index) {
        if (!casBinAt(index, null, ReservationNode())) {
            return@withLock false
        }

        var node: ListNode<K, V>? = null

        try {
            node = compute()
        } finally {
            setBinAt(index, node)
        }

        true
    }

    /**
     * Gives [value] to [node], or removes [node] from the list of the bin at [index] when
     * [value] is `null`. [pred] is the node before it. Returns the change in the node count.
     */
    inline fun update(index: Int, node: ListNode<K, V>, pred: ListNode<K, V>?, value: V?): Int {
        if (value != null) {
            node.value = value
            return 0
        }

        if (pred != null) {
            pred.next = node.next
        } else {
            setBinAt(index, node.next)
        }

        return -1
    }

    /**
     * Gives [value] to [node], or removes [node] from the tree [bin] at [index] when [value]
     * is `null`. Returns the change in the node count.
     */
    inline fun update(index: Int, bin: TreeBin<K, V>, node: TreeNode<K, V>, value: V?): Int {
        if (value != null) {
            node.value = value
            return 0
        }

        if (bin.removeTreeNode(node)) {
            setBinAt(index, bin.untreeify())
        }

        return -1
    }
}
