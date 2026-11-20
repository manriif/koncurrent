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

import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicLongArray
import kotlin.concurrent.atomics.AtomicReference

/**
 * What an addition returns instead of the count, once threads contend on it.
 */
internal const val CONTENDED_COUNT = Long.MIN_VALUE

// Must be a power of two
internal const val CELL_COUNT = 16

// Longs between two cells. It keeps each cell on its own cache line, up to 128 bytes.
internal const val CELL_STRIDE = 16

/**
 * A count spread over several cells once threads contend on it. Adapted from the counter cells
 * of the original, themselves adapted from `LongAdder`.
 *
 * A thread adds to the cell that its identifier selects, so that threads rarely write to the
 * same cache line. The original moves a thread to another cell when it contends. Here a thread
 * keeps its cell, and the cells are created at once.
 */
internal class StripedCounter {

    // Not private, for the inline function
    val base = AtomicLong(0L)
    val cells = AtomicReference<AtomicLongArray?>(null)

    /**
     * Adds [delta]. Returns the new count, or [CONTENDED_COUNT] when it went to a cell, which
     * makes the count costly to tell.
     */
    inline fun add(delta: Long): Long {
        var cells = cells.load()

        if (cells == null) {
            val count = base.load()

            if (base.compareAndSet(count, count + delta)) {
                return count + delta
            }

            cells = createCells()
        }

        val _ = cells.fetchAndAddAt(cellIndex(currentThreadId()), delta)
        return CONTENDED_COUNT
    }

    fun sum(): Long {
        var sum = base.load()
        val cells = cells.load() ?: return sum

        for (cell in 0..<CELL_COUNT) {
            sum += cells.loadAt(cellIndex(cell))
        }

        return sum
    }

    fun createCells(): AtomicLongArray {
        val created = AtomicLongArray((CELL_COUNT + 2) * CELL_STRIDE)
        return cells.compareAndExchange(null, created) ?: created
    }

    inline fun cellIndex(cell: Int): Int = ((cell and (CELL_COUNT - 1)) + 1) * CELL_STRIDE
}
