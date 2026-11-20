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

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val KEY_COUNT = 1_000
private const val OPERATION_COUNT = 100_000
private const val SEED = 20261117

/**
 * Tests that a thread drops the values of the thread locals that were collected.
 */
class ThreadValuesTest {

    @Test
    fun valuesOfDeadKeysAreDropped() {
        val values = ThreadValues()
        val kept = ThreadLocalKey()
        values[kept] = "kept"

        repeat(KEY_COUNT) { index ->
            val key = ThreadLocalKey()

            values[key] = index
            key.dead.store(true)
        }

        // Each sweep leaves at most the values set since the one before
        assertEquals("kept", values[kept])
        assertEquals(true, values.size < KEY_COUNT / 2)
    }

    @Test
    fun valuesOfLiveKeysAreKept() {
        val values = ThreadValues()
        val keys = List(KEY_COUNT) { ThreadLocalKey() }

        for ((index, key) in keys.withIndex()) {
            values[key] = index
        }

        assertEquals(KEY_COUNT, values.size)

        for ((index, key) in keys.withIndex()) {
            assertEquals(index, values[key])
        }

        values.remove(keys[0])
        assertNull(values[keys[0]])
    }

    @Test
    fun randomOperationsMatchHashMap() {
        val random = Random(SEED)
        val values = ThreadValues()
        val expected = HashMap<ThreadLocalKey, Int>()
        val keys = List(KEY_COUNT) { ThreadLocalKey() }

        repeat(OPERATION_COUNT) { operation ->
            val key = keys[random.nextInt(KEY_COUNT)]

            // More removals than additions now and then, to empty and fill the table again
            if (random.nextInt(4) < 1 + (operation / KEY_COUNT) % 3) {
                values.remove(key)
                expected.remove(key)
            } else {
                values[key] = operation
                expected[key] = operation
            }

            assertEquals(expected[key], values[key])
        }

        assertEquals(expected.size, values.size)

        for (key in keys) {
            assertEquals(expected[key], values[key])
        }
    }
}
