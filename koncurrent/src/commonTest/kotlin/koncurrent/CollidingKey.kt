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

/**
 * A key whose hash code is chosen, to make keys collide. It is not comparable, so a tree bin
 * cannot order the keys that share a hash code.
 */
internal open class CollidingKey(val id: Int, private val hash: Int) {

    override fun equals(other: Any?): Boolean = other is CollidingKey && other.id == id

    override fun hashCode(): Int = hash

    override fun toString(): String = "CollidingKey($id)"
}

/**
 * A [CollidingKey] that a tree bin can order.
 */
internal class OrderedCollidingKey(
    id: Int,
    hash: Int,
) : CollidingKey(id, hash), Comparable<OrderedCollidingKey> {

    override fun compareTo(other: OrderedCollidingKey): Int = id.compareTo(other.id)
}
