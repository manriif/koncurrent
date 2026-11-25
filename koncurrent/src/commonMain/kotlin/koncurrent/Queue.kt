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
 * A collection that holds elements until they are processed. Elements leave it by its head.
 * The queues of this library can be used by any number of threads at the same time.
 *
 * Each operation has two forms. One throws when it cannot be done, the other one returns a
 * value that tells so:
 *
 * | | Throws | Returns a value |
 * |---|---|---|
 * | Add | [add] | [offer] |
 * | Remove the head | [remove] | [poll] |
 * | Read the head | [element] | [peek] |
 *
 * [add] throws [IllegalStateException] when a queue of limited capacity is full.
 *
 * Elements are not `null`, which is what [poll] and [peek] return for an empty queue.
 *
 * This interface is not meant to be implemented outside of this library. On the JVM and
 * Android it is `java.util.Queue`, which may get more functions to implement.
 *
 * @param E the type of the elements
 */
@SubclassOptInRequired(InternalForInheritanceKoncurrentApi::class)
public expect interface Queue<E : Any> : MutableCollection<E> {

    /**
     * Adds [element] if the queue has room for it. Never waits.
     *
     * @return whether the element was added
     */
    public fun offer(element: E): Boolean

    /**
     * Removes the head of the queue and returns it.
     *
     * @throws NoSuchElementException if the queue is empty
     */
    @IgnorableReturnValue
    public fun remove(): E

    /**
     * Removes the head of the queue and returns it, or returns `null` if the queue is empty.
     */
    @IgnorableReturnValue
    public fun poll(): E?

    /**
     * Returns the head of the queue without removing it.
     *
     * @throws NoSuchElementException if the queue is empty
     */
    public fun element(): E

    /**
     * Returns the head of the queue without removing it, or `null` if the queue is empty.
     */
    public fun peek(): E?
}
