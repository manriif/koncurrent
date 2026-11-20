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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import koncurrent.compute as computeExtension
import koncurrent.computeIfAbsent as computeIfAbsentExtension
import koncurrent.computeIfPresent as computeIfPresentExtension
import koncurrent.merge as mergeExtension

/**
 * Tests the computations of the public [ConcurrentHashMap] as the extensions that common code
 * declares. On the JVM and Android a call selects a member of the map instead, so only an alias
 * reaches the extensions.
 */
class ComputationExtensionsTest {

    @Test
    fun extensionsComputeLikeTheMembers() {
        val map = ConcurrentHashMap<String, Int>()

        assertEquals(1, map.computeIfAbsentExtension("a") { 1 })
        assertEquals(1, map.computeIfAbsentExtension("a") { 2 })

        assertEquals(3, map.computeIfPresentExtension("a") { _, value -> value + 2 })
        assertNull(map.computeIfPresentExtension("b") { _, _ -> 1 })

        assertEquals(4, map.computeExtension("a") { _, value -> (value ?: 0) + 1 })
        assertNull(map.computeExtension("a") { _, _ -> null })

        assertEquals(5, map.mergeExtension("a", 5) { old, new -> old + new })
        assertEquals(7, map.mergeExtension("a", 2) { old, new -> old + new })
        assertNull(map.mergeExtension("a", 1) { _, _ -> null })

        assertEquals(emptyMap(), map)
    }
}
