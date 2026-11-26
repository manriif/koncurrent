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

import koncurrent.internal.PortableSemaphore

public actual class Semaphore actual constructor(permits: Int, fair: Boolean) {

    private val semaphore = PortableSemaphore(permits, fair)

    public actual constructor(permits: Int) : this(permits, false)

    public actual fun acquire(): Unit = semaphore.acquire()

    public actual fun tryAcquire(): Boolean = semaphore.tryAcquire()

    internal fun tryAcquire(nanos: Long): Boolean = semaphore.tryAcquire(nanos)

    public actual fun release(): Unit = semaphore.release()

    public actual fun availablePermits(): Int = semaphore.availablePermits()

    public actual fun isFair(): Boolean = semaphore.fair
}
