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

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicIntArray

/**
 * The bin lock of the map, measured alone. It is a word in an atomic array next to the bins.
 * The map keeps the same code in its table, which is internal.
 */
public class ArraySpinLock(size: Int) {

    private val locks = AtomicIntArray(size)

    public fun lock(index: Int) {
        while (!locks.compareAndSetAt(index, 0, 1)) {
            while (locks.loadAt(index) != 0) {
                // Spins until the holder releases the lock
            }
        }
    }

    public fun unlock(index: Int): Unit = locks.storeAt(index, 0)
}

/**
 * The alternative that the map did not keep. It is a word in an atomic of its own, as each node
 * would need one to be locked like in the original.
 */
public class NodeSpinLock {

    private val lock = AtomicInt(0)

    public fun lock() {
        while (!lock.compareAndSet(0, 1)) {
            while (lock.load() != 0) {
                // Spins until the holder releases the lock
            }
        }
    }

    public fun unlock(): Unit = lock.store(0)
}
