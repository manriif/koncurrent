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
package koncurrent.internal

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.native.concurrent.ThreadLocal

// The size of the table of a thread at first. A power of two, as every later size.
private const val INITIAL_CAPACITY = 16

// Two to the power of 32 over the golden ratio. Hashes that grow by it spread evenly over a
// table whose size is a power of two.
private const val HASH_INCREMENT = -0x61c88647

private val NextHash = AtomicInt(0)

/**
 * Identifies a thread local in the values of each thread. The values do not reference the thread
 * local itself, so that it can be collected.
 */
internal class ThreadLocalKey {

    val hash = NextHash.fetchAndAdd(HASH_INCREMENT)

    // Set once the thread local is collected
    val dead = AtomicBoolean(false)
}

/**
 * The values that the thread locals hold for one thread. They go away with the thread.
 *
 * It is a hash table with open addressing, which only its thread accesses. A key is looked for
 * from the slot of its hash onwards, up to an empty slot.
 *
 * A collected thread local cannot reach the values of the other threads. Each thread drops them
 * itself, when its table fills up.
 */
internal class ThreadValues {

    private var keys = arrayOfNulls<ThreadLocalKey>(INITIAL_CAPACITY)
    private var values = arrayOfNulls<Any>(INITIAL_CAPACITY)

    var size = 0
        private set

    operator fun get(key: ThreadLocalKey): Any? {
        val keys = keys
        val mask = keys.size - 1
        var index = key.hash and mask

        while (true) {
            val candidate = keys[index] ?: return null

            if (candidate === key) {
                return values[index]
            }

            index = (index + 1) and mask
        }
    }

    operator fun set(key: ThreadLocalKey, value: Any?) {
        val keys = keys
        val mask = keys.size - 1
        var index = key.hash and mask

        while (true) {
            val candidate = keys[index] ?: break

            if (candidate === key) {
                values[index] = value
                return
            }

            index = (index + 1) and mask
        }

        keys[index] = key
        values[index] = value
        size++

        // Keeps a third of the slots empty, so that a search is short
        if (size * 3 > keys.size * 2) {
            rehash()
        }
    }

    fun remove(key: ThreadLocalKey) {
        val keys = keys
        val mask = keys.size - 1
        var gap = key.hash and mask

        while (true) {
            val candidate = keys[gap] ?: return

            if (candidate === key) {
                break
            }

            gap = (gap + 1) and mask
        }

        // Moves back the keys that were pushed past the removed one, so that a search for them
        // does not stop at its empty slot
        var index = (gap + 1) and mask

        while (true) {
            val candidate = keys[index] ?: break
            val distance = (index - candidate.hash) and mask

            if (distance >= ((index - gap) and mask)) {
                keys[gap] = candidate
                values[gap] = values[index]
                gap = index
            }

            index = (index + 1) and mask
        }

        keys[gap] = null
        values[gap] = null
        size--
    }

    /**
     * Drops the values of the dead keys. Doubles the table if more than a third of it is still
     * used after that.
     */
    private fun rehash() {
        val oldKeys = keys
        val oldValues = values
        val live = oldKeys.count { it != null && !it.dead.load() }
        val capacity = if (live * 3 > oldKeys.size) oldKeys.size * 2 else oldKeys.size

        keys = arrayOfNulls(capacity)
        values = arrayOfNulls(capacity)
        size = 0

        for (index in oldKeys.indices) {
            val key = oldKeys[index]

            if (key != null && !key.dead.load()) {
                this[key] = oldValues[index]
            }
        }
    }
}

/**
 * The values of the calling thread. It is not private, for the thread local class to read it
 * without a call.
 */
@ThreadLocal
internal val CurrentThreadValues = ThreadValues()
