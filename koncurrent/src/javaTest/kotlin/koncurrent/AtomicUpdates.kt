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

import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal const val UPDATED_KEY = "a"

private const val WAIT_MILLIS = 100L

/**
 * Checks that [update] is atomic for [UPDATED_KEY] of [map]. It runs [update] with a function
 * that returns 1. While the function runs, another thread tries to put 2. It must wait for the
 * end of [update], and so replace 1.
 *
 * A map also inherits updates of the same names from `java.util.Map`. They take Java functional
 * interfaces and are not atomic. A call that would bind to one of them lets the other thread in.
 */
internal fun <M : MutableMap<String, Int>> assertUpdateIsAtomic(
    map: M,
    present: Boolean,
    update: (map: M, function: () -> Int) -> Unit,
) {
    var replaced: Int? = null
    val writer = Thread { replaced = map.put(UPDATED_KEY, 2) }

    if (present) {
        map[UPDATED_KEY] = 0
    }

    update(map) {
        writer.start()
        writer.join(WAIT_MILLIS)
        assertTrue(writer.isAlive, "the writer did not wait for the update")
        1
    }

    writer.join()

    assertEquals(1, replaced)
    assertEquals(2, map[UPDATED_KEY])
}
