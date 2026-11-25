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

import koncurrent.BlockingDeque
import koncurrent.BlockingQueue
import koncurrent.locks.Condition
import koncurrent.locks.Lock
import kotlin.time.Duration.Companion.nanoseconds

// Each calls the member that the interface has on these targets

internal actual fun Lock.tryLockWithin(nanos: Long): Boolean = tryLock(nanos.nanoseconds)

internal actual fun Condition.awaitWithin(nanos: Long): Boolean = await(nanos.nanoseconds)

internal actual fun <E : Any> BlockingQueue<E>.offerWithin(element: E, nanos: Long): Boolean =
    offer(element, nanos.nanoseconds)

internal actual fun <E : Any> BlockingQueue<E>.pollWithin(nanos: Long): E? = poll(nanos.nanoseconds)

internal actual fun <E : Any> BlockingDeque<E>.offerFirstWithin(element: E, nanos: Long): Boolean =
    offerFirst(element, nanos.nanoseconds)

internal actual fun <E : Any> BlockingDeque<E>.offerLastWithin(element: E, nanos: Long): Boolean =
    offerLast(element, nanos.nanoseconds)

internal actual fun <E : Any> BlockingDeque<E>.pollFirstWithin(nanos: Long): E? =
    pollFirst(nanos.nanoseconds)

internal actual fun <E : Any> BlockingDeque<E>.pollLastWithin(nanos: Long): E? =
    pollLast(nanos.nanoseconds)
