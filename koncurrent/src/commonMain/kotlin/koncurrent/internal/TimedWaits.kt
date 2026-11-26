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
import koncurrent.CountDownLatch
import koncurrent.CyclicBarrier
import koncurrent.Semaphore
import koncurrent.locks.Condition
import koncurrent.locks.Lock
import koncurrent.locks.StampedLock

// The waits with a time limit, as each platform has them. The public functions take a
// Duration. These take nanoseconds, which is what the Java functions and the ports count in.

internal expect fun Lock.tryLockWithin(nanos: Long): Boolean

internal expect fun Condition.awaitWithin(nanos: Long): Boolean

internal expect fun StampedLock.tryWriteLockWithin(nanos: Long): Long

internal expect fun StampedLock.tryReadLockWithin(nanos: Long): Long

internal expect fun Semaphore.tryAcquireWithin(nanos: Long): Boolean

internal expect fun CountDownLatch.awaitWithin(nanos: Long): Boolean

internal expect fun CyclicBarrier.awaitWithin(nanos: Long): Int

internal expect fun <E : Any> BlockingQueue<E>.offerWithin(element: E, nanos: Long): Boolean

internal expect fun <E : Any> BlockingQueue<E>.pollWithin(nanos: Long): E?

internal expect fun <E : Any> BlockingDeque<E>.offerFirstWithin(element: E, nanos: Long): Boolean

internal expect fun <E : Any> BlockingDeque<E>.offerLastWithin(element: E, nanos: Long): Boolean

internal expect fun <E : Any> BlockingDeque<E>.pollFirstWithin(nanos: Long): E?

internal expect fun <E : Any> BlockingDeque<E>.pollLastWithin(nanos: Long): E?
