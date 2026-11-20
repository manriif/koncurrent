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

import koncurrent.UPDATED_KEY
import koncurrent.assertUpdateIsAtomic
import kotlin.test.Test

/**
 * Checks that a Kotlin lambda reaches the atomic updates of [PortableConcurrentHashMap] on the
 * JVM, and not the ones of `java.util.Map`.
 */
class AtomicUpdateBindingTest {

    @Test
    fun computeIfAbsentIsAtomic() {
        assertUpdateIsAtomic(newMap(), present = false) { map, function ->
            map.computeIfAbsent(UPDATED_KEY) { function() }
        }
    }

    @Test
    fun computeIfPresentIsAtomic() {
        assertUpdateIsAtomic(newMap(), present = true) { map, function ->
            map.computeIfPresent(UPDATED_KEY) { _, _ ->
                function()
            }
        }
    }

    @Test
    fun computeIsAtomic() {
        for (present in listOf(false, true)) {
            assertUpdateIsAtomic(newMap(), present) { map, function ->
                map.compute(UPDATED_KEY) { _, _ ->
                    function()
                }
            }
        }
    }

    @Test
    fun mergeIsAtomic() {
        assertUpdateIsAtomic(newMap(), present = true) { map, function ->
            map.merge(UPDATED_KEY, 1) { _, _ ->
                function()
            }
        }
    }

    private fun newMap() = PortableConcurrentHashMap<String, Int>()
}
