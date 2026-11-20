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

import koncurrent.CountDownLatch
import koncurrent.Semaphore
import koncurrent.locks.Condition
import koncurrent.locks.Lock
import java.util.concurrent.TimeUnit

internal actual fun Lock.tryLockWithin(nanos: Long): Boolean =
    tryLock(nanos, TimeUnit.NANOSECONDS)

internal actual fun Condition.awaitWithin(nanos: Long): Boolean =
    await(nanos, TimeUnit.NANOSECONDS)

internal actual fun Semaphore.tryAcquireWithin(nanos: Long): Boolean =
    tryAcquire(nanos, TimeUnit.NANOSECONDS)

internal actual fun CountDownLatch.awaitWithin(nanos: Long): Boolean =
    await(nanos, TimeUnit.NANOSECONDS)
