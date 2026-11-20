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
package koncurrent

import koncurrent.internal.PlatformConcurrentMap
import koncurrent.internal.PortableConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertIs

/**
 * Tests the public [ConcurrentHashMap] over the platform map, which backs it from API 24. The
 * tests run on the host, where the API level reads 0. So the tests of the other source sets
 * cover the port, which backs the map below API 24.
 */
class PlatformConcurrentHashMapTest : ConcurrentHashMapTest() {

    override fun <K : Any, V : Any> newMap(): ConcurrentHashMap<K, V> =
        ConcurrentHashMap(PlatformConcurrentMap())

    @Test
    fun portBacksTheMapBelowApi24() {
        assertIs<PortableConcurrentHashMap<String, Int>>(ConcurrentHashMap<String, Int>().map)
        assertIs<PortableConcurrentHashMap<String, Int>>(ConcurrentHashMap<String, Int>(64).map)
    }
}

/**
 * Stress tests of the public [ConcurrentHashMap] over the platform map.
 */
class PlatformConcurrentHashMapStressTest : ConcurrentHashMapStressTest() {

    override fun <K : Any, V : Any> newMap(): ConcurrentHashMap<K, V> =
        ConcurrentHashMap(PlatformConcurrentMap())
}

/**
 * Checks that a Kotlin lambda reaches the atomic updates of the public [ConcurrentHashMap] on
 * Android, and not the ones of `java.util.Map`. Those are not atomic, and they do not exist
 * below API 24.
 */
class ConcurrentHashMapBindingTest {

    private val backends = listOf<() -> ConcurrentHashMap<String, Int>>(
        { ConcurrentHashMap(PortableConcurrentHashMap()) },
        { ConcurrentHashMap(PlatformConcurrentMap()) },
    )

    @Test
    fun computeIfAbsentIsAtomic() {
        for (newMap in backends) {
            assertUpdateIsAtomic(newMap(), present = false) { map, function ->
                map.computeIfAbsent(UPDATED_KEY) { function() }
            }
        }
    }

    @Test
    fun computeIfPresentIsAtomic() {
        for (newMap in backends) {
            assertUpdateIsAtomic(newMap(), present = true) { map, function ->
                map.computeIfPresent(UPDATED_KEY) { _, _ ->
                    function()
                }
            }
        }
    }

    @Test
    fun computeIsAtomic() {
        for (newMap in backends) {
            for (present in listOf(false, true)) {
                assertUpdateIsAtomic(newMap(), present) { map, function ->
                    map.compute(UPDATED_KEY) { _, _ ->
                        function()
                    }
                }
            }
        }
    }

    @Test
    fun mergeIsAtomic() {
        for (newMap in backends) {
            assertUpdateIsAtomic(newMap(), present = true) { map, function ->
                map.merge(UPDATED_KEY, 1) { _, _ ->
                    function()
                }
            }
        }
    }
}
