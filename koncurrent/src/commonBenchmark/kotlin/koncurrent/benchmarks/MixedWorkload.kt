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
@file:Suppress("RedundantVisibilityModifier")

package koncurrent.benchmarks

/**
 * Writes out of 1024 operations, for 10% of writes.
 */
public const val READ_90_WRITE_10: Int = 102

/**
 * Writes out of 1024 operations, for 50% of writes.
 */
public const val READ_50_WRITE_50: Int = 512

/**
 * A fast pseudo-random sequence, one for each thread.
 */
public class XorShift(seed: Int) {

    private var state = if (seed == 0) 1 else seed

    public fun next(): Int {
        var next = state

        next = next xor (next shl 13)
        next = next xor (next ushr 17)
        next = next xor (next shl 5)
        state = next

        return next
    }
}

/**
 * Does one operation of a mixed workload. [random] picks one of the [keyCount] keys by its
 * index, and the operation. It is a write [writes] times out of 1024, else a [get]. A write is
 * a [put] or a [remove] with equal chances. So a map that starts with half of the keys keeps
 * about half of them.
 */
public inline fun mixedOperation(
    random: Int,
    keyCount: Int,
    writes: Int,
    get: (index: Int) -> Unit,
    put: (index: Int) -> Unit,
    remove: (index: Int) -> Unit,
) {
    val index = (((random ushr 11).toLong() * keyCount) ushr 21).toInt()

    when {
        (random and 1023) >= writes -> get(index)
        (random and 1024) == 0 -> put(index)
        else -> remove(index)
    }
}
