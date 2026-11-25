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

import koncurrent.internal.offerFirstWithin
import koncurrent.internal.offerLastWithin
import koncurrent.internal.pollFirstWithin
import koncurrent.internal.pollLastWithin
import kotlin.time.Duration

/**
 * A [Deque] whose threads can wait at both ends: for room when they add to a full deque, and
 * for an element when they take from an empty one.
 *
 * It is a [BlockingQueue] too. [put] is [putLast], and [take] is [takeFirst].
 *
 * | | Head, waits | Head, for some time | Tail, waits | Tail, for some time |
 * |---|---|---|---|---|
 * | Add | [putFirst] | [offerFirst] with a limit | [putLast] | [offerLast] with a limit |
 * | Remove | [takeFirst] | [pollFirst] with a limit | [takeLast] | [pollLast] with a limit |
 *
 * This interface is not meant to be implemented outside of this library. On the JVM and
 * Android it is `java.util.concurrent.BlockingDeque`, which has more functions to implement.
 *
 * ### Platforms
 *
 * - JS and Wasm have one thread, which nothing could wake up. A `put` on a full deque and a
 *   `take` on an empty one throw [IllegalStateException] there.
 *
 * @param E the type of the elements
 */
@SubclassOptInRequired(InternalForInheritanceKoncurrentApi::class)
public expect interface BlockingDeque<E : Any> : BlockingQueue<E>, Deque<E> {

    /**
     * Adds [element] at the head, and waits for room while the deque is full.
     *
     * @throws InterruptedException if the thread is interrupted while it waits
     */
    public fun putFirst(element: E)

    /**
     * Adds [element] at the tail, and waits for room while the deque is full.
     *
     * @throws InterruptedException if the thread is interrupted while it waits
     */
    public fun putLast(element: E)

    /**
     * Removes the first element and returns it, and waits for an element while the deque is
     * empty.
     *
     * @throws InterruptedException if the thread is interrupted while it waits
     */
    @IgnorableReturnValue
    public fun takeFirst(): E

    /**
     * Removes the last element and returns it, and waits for an element while the deque is
     * empty.
     *
     * @throws InterruptedException if the thread is interrupted while it waits
     */
    @IgnorableReturnValue
    public fun takeLast(): E
}

/**
 * Adds [element] at the head, and waits for room for [timeout] at most. Does not wait when
 * [timeout] is zero or negative.
 *
 * On JS and Wasm this never waits, since no other thread could make room.
 *
 * @return `false` if the time ran out before there was room
 * @throws InterruptedException if the thread is interrupted before or while it waits
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER") // A member on native, JS and Wasm, which this calls
public fun <E : Any> BlockingDeque<E>.offerFirst(element: E, timeout: Duration): Boolean =
    offerFirstWithin(element, timeout.inWholeNanoseconds)

/**
 * Adds [element] at the tail, and waits for room for [timeout] at most. Does not wait when
 * [timeout] is zero or negative.
 *
 * On JS and Wasm this never waits, since no other thread could make room.
 *
 * @return `false` if the time ran out before there was room
 * @throws InterruptedException if the thread is interrupted before or while it waits
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER") // A member on native, JS and Wasm, which this calls
public fun <E : Any> BlockingDeque<E>.offerLast(element: E, timeout: Duration): Boolean =
    offerLastWithin(element, timeout.inWholeNanoseconds)

/**
 * Removes the first element and returns it, and waits for an element for [timeout] at most.
 * Does not wait when [timeout] is zero or negative.
 *
 * On JS and Wasm this never waits, since no other thread could add an element.
 *
 * @return `null` if the time ran out before there was an element
 * @throws InterruptedException if the thread is interrupted before or while it waits
 */
@IgnorableReturnValue
@Suppress("EXTENSION_SHADOWED_BY_MEMBER") // A member on native, JS and Wasm, which this calls
public fun <E : Any> BlockingDeque<E>.pollFirst(timeout: Duration): E? =
    pollFirstWithin(timeout.inWholeNanoseconds)

/**
 * Removes the last element and returns it, and waits for an element for [timeout] at most.
 * Does not wait when [timeout] is zero or negative.
 *
 * On JS and Wasm this never waits, since no other thread could add an element.
 *
 * @return `null` if the time ran out before there was an element
 * @throws InterruptedException if the thread is interrupted before or while it waits
 */
@IgnorableReturnValue
@Suppress("EXTENSION_SHADOWED_BY_MEMBER") // A member on native, JS and Wasm, which this calls
public fun <E : Any> BlockingDeque<E>.pollLast(timeout: Duration): E? =
    pollLastWithin(timeout.inWholeNanoseconds)
