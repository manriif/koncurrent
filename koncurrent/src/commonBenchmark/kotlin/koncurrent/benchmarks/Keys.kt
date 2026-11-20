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

import kotlin.random.Random

public const val SMALL_KEY_COUNT: Int = 1_000
public const val LARGE_KEY_COUNT: Int = 1_000_000

private const val SEED = 42

/**
 * A map key. Its hash code is random, like the one of a string or of an identity.
 */
public class Key(private val hash: Int) {

    override fun equals(other: Any?): Boolean = other is Key && other.hash == hash

    override fun hashCode(): Int = hash
}

/**
 * The keys of a benchmark. A map stores the [stored] keys and is asked for the [probes], which
 * are equal to them but are other instances, in another order. So a map has to call `equals`
 * to find a key, and it does not find the keys in the order of their insertion.
 */
public class Keys(public val count: Int) {

    public val stored: Array<Key>
    public val probes: Array<Key>

    init {
        val random = Random(SEED)
        val hashes = LinkedHashSet<Int>()

        while (hashes.size < count) {
            hashes += random.nextInt()
        }

        stored = hashes.map { Key(it) }.toTypedArray()
        probes = hashes.shuffled(random).map { Key(it) }.toTypedArray()
    }
}
