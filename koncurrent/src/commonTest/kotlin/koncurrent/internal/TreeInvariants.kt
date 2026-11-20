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

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Checks every tree bin of [map]. Its tree must be a valid red-black tree ordered by hash, and
 * hold the same nodes as its list. Returns the number of tree bins.
 */
@IgnorableReturnValue
internal fun <K : Any, V : Any> assertTreeInvariants(map: PortableConcurrentHashMap<K, V>): Int {
    val table = map.table ?: return 0
    var treeBins = 0

    for (index in 0..<table.size) {
        val bin = table.binAt(index)

        if (bin is TreeBin) {
            assertTreeInvariants(bin)
            treeBins++
        }
    }

    return treeBins
}

private fun <K : Any, V : Any> assertTreeInvariants(bin: TreeBin<K, V>) {
    val root = bin.root
    var listed = 0
    var node = bin.first

    assertNull(bin.first?.prev)

    while (node != null) {
        val next = node.next

        if (next != null) {
            assertSame(node, next.prev)
        }

        listed++
        node = next
    }

    if (root == null) {
        assertEquals(0, listed)
        return
    }

    assertNull(root.parent)
    assertFalse(root.red)
    assertEquals(listed, countNodes(root))

    val _ = blackHeight(root)
}

private fun <K : Any, V : Any> countNodes(node: TreeNode<K, V>?): Int =
    if (node == null) 0 else 1 + countNodes(node.left) + countNodes(node.right)

/**
 * Checks the tree rooted at [node] and returns its black height.
 */
private fun <K : Any, V : Any> blackHeight(node: TreeNode<K, V>?): Int {
    if (node == null) {
        return 1
    }

    val left = node.left
    val right = node.right

    if (left != null) {
        assertSame(node, left.parent)
        assertTrue(left.hash <= node.hash)
        assertFalse(node.red && left.red)
    }

    if (right != null) {
        assertSame(node, right.parent)
        assertTrue(right.hash >= node.hash)
        assertFalse(node.red && right.red)
    }

    val height = blackHeight(left)
    assertEquals(height, blackHeight(right))

    return if (node.red) height else height + 1
}
