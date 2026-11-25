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

import koncurrent.internal.offerWithin
import koncurrent.internal.pollWithin
import kotlin.time.Duration

/**
 * A [Queue] whose threads can wait: for room when they add to a full queue, and for an element
 * when they take from an empty one. It is what producers and consumers share.
 *
 * Each operation has four forms:
 *
 * | | Throws | Returns a value | Waits | Waits for some time |
 * |---|---|---|---|---|
 * | Add | [add] | [offer] | [put] | [offer] with a limit |
 * | Remove the head | [remove] | [poll] | [take] | [poll] with a limit |
 * | Read the head | [element] | [peek] | | |
 *
 * A queue may have a capacity, which [remainingCapacity] tells. A queue without one never
 * makes a thread wait to add.
 *
 * This interface is not meant to be implemented outside of this library. On the JVM and
 * Android it is `java.util.concurrent.BlockingQueue`, which has more functions to implement.
 *
 * ### Platforms
 *
 * - JS and Wasm have one thread, which nothing could wake up. [put] on a full queue and [take]
 *   on an empty one throw [IllegalStateException] there.
 *
 * @param E the type of the elements
 */
@SubclassOptInRequired(InternalForInheritanceKoncurrentApi::class)
public expect interface BlockingQueue<E : Any> : Queue<E> {

    /**
     * Adds [element], and waits for room while the queue is full.
     *
     * @throws InterruptedException if the thread is interrupted before or while it waits
     */
    public fun put(element: E)

    /**
     * Removes the head of the queue and returns it, and waits for an element while the queue
     * is empty.
     *
     * @throws InterruptedException if the thread is interrupted before or while it waits
     */
    @IgnorableReturnValue
    public fun take(): E

    /**
     * Returns the number of elements that can be added now without waiting, or [Int.MAX_VALUE]
     * if the queue has no capacity. It is a transient value while other threads use the queue.
     */
    public fun remainingCapacity(): Int

    /**
     * Moves all the elements of this queue to [collection], in the order that [poll] gives.
     * Never waits. It is faster than taking the elements one by one.
     *
     * @return the number of elements moved
     * @throws IllegalArgumentException if [collection] is this queue
     */
    @IgnorableReturnValue
    public fun drainTo(collection: MutableCollection<in E>): Int

    /**
     * Moves [maxElements] elements at most from this queue to [collection], in the order that
     * [poll] gives. Never waits.
     *
     * @return the number of elements moved
     * @throws IllegalArgumentException if [collection] is this queue
     */
    @IgnorableReturnValue
    public fun drainTo(collection: MutableCollection<in E>, maxElements: Int): Int
}

/**
 * Adds [element], and waits for room for [timeout] at most. Does not wait when [timeout] is
 * zero or negative.
 *
 * On JS and Wasm this never waits, since no other thread could make room.
 *
 * @return `false` if the time ran out before there was room
 * @throws InterruptedException if the thread is interrupted before or while it waits
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER") // A member on native, JS and Wasm, which this calls
public fun <E : Any> BlockingQueue<E>.offer(element: E, timeout: Duration): Boolean =
    offerWithin(element, timeout.inWholeNanoseconds)

/**
 * Removes the head of the queue and returns it, and waits for an element for [timeout] at
 * most. Does not wait when [timeout] is zero or negative.
 *
 * On JS and Wasm this never waits, since no other thread could add an element.
 *
 * @return `null` if the time ran out before there was an element
 * @throws InterruptedException if the thread is interrupted before or while it waits
 */
@IgnorableReturnValue
@Suppress("EXTENSION_SHADOWED_BY_MEMBER") // A member on native, JS and Wasm, which this calls
public fun <E : Any> BlockingQueue<E>.poll(timeout: Duration): E? =
    pollWithin(timeout.inWholeNanoseconds)
