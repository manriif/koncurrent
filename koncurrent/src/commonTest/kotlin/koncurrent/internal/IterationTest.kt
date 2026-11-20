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

import koncurrent.CollidingKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val INITIAL_COUNT = 1_000
private const val ADDED_COUNT = 20_000

// Fewer keys than the other tests take. An insertion searches the whole bin of the keys that
// share its hash code, since they have no order.
private const val COLLIDING_COUNT = 200

/**
 * Tests the iteration of [PortableConcurrentHashMap] while it is updated. An iteration is weakly
 * consistent. It never fails. It gives once each mapping that the map holds from its start to
 * its end.
 */
class IterationTest {

    @Test
    fun iterationGivesEachMappingOnce() {
        val map = PortableConcurrentHashMap<Int, Int>()
        repeat(INITIAL_COUNT) { map[it] = -it }

        val seen = HashMap<Int, Int>()

        for ((key, value) in map) {
            assertFalse(key in seen)
            seen[key] = value
        }

        assertEquals<Map<Int, Int>>(map, seen)
    }

    @Test
    fun iterationSurvivesResizes() {
        val map = PortableConcurrentHashMap<Int, Int>()
        repeat(INITIAL_COUNT) { map[it] = it }

        val seen = BooleanArray(INITIAL_COUNT)
        var steps = 0
        var added = 0

        for (key in map.keys) {
            if (key < INITIAL_COUNT) {
                assertFalse(seen[key], "key $key given twice")
                seen[key] = true
            }

            // Grows the table several times while the iteration is in its middle
            if (steps++ % 50 == 0 && added < ADDED_COUNT) {
                repeat(ADDED_COUNT / 20) { map[INITIAL_COUNT + added++] = it }
            }
        }

        assertEquals(ADDED_COUNT, added)
        assertTrue(seen.all { it })
    }

    @Test
    fun iterationSkipsTheMappingsRemovedAhead() {
        val map = PortableConcurrentHashMap<Int, Int>()
        repeat(INITIAL_COUNT) { map[it] = it }

        val seen = HashSet<Int>()
        val removed = HashSet<Int>()
        var justRemoved = -1

        for (key in map.keys) {
            // An iterator holds its next key in advance, so it may still give the key just removed
            assertTrue(key !in removed || key == justRemoved, "key $key given after its removal")
            assertTrue(seen.add(key), "key $key given twice")

            // Removes a key of another bin, which the iteration may or may not have passed
            val other = (key + INITIAL_COUNT / 2) % INITIAL_COUNT

            if (other !in seen && map.remove(other) != null) {
                removed += other
                justRemoved = other
            }
        }

        val kept = (0..<INITIAL_COUNT).toSet() - removed

        assertTrue(seen.containsAll(kept))
        assertEquals(kept, map.keys)
    }

    @Test
    fun iterationOverCollidingKeysSurvivesUpdates() {
        val map = PortableConcurrentHashMap<CollidingKey, Int>()
        val keys = List(COLLIDING_COUNT) { CollidingKey(it, hash = (it % 8) * 64) }
        keys.forEach { map[it] = it.id }

        val seen = BooleanArray(COLLIDING_COUNT)
        var next = COLLIDING_COUNT

        for (key in map.keys) {
            if (key.id < COLLIDING_COUNT) {
                assertFalse(seen[key.id], "key ${key.id} given twice")
                seen[key.id] = true
            }

            // Adds to the tree bins being iterated, and to others
            repeat(3) {
                map[CollidingKey(next, hash = (next % 16) * 64)] = next
                next++
            }
        }

        assertTrue(seen.all { it })
        assertEquals(next, map.size)
    }

    @Test
    fun emptyMapHasNothingToIterate() {
        val map = PortableConcurrentHashMap<Int, Int>()

        assertFalse(map.keys.iterator().hasNext())
        assertFalse(map.values.iterator().hasNext())
        assertFalse(map.entries.iterator().hasNext())

        map[1] = 1
        val _ = map.remove(1)

        assertFalse(map.entries.iterator().hasNext())
    }
}
