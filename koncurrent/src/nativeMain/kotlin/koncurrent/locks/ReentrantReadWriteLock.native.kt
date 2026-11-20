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
package koncurrent.locks

import koncurrent.internal.PortableReentrantReadWriteLock
import kotlin.time.Duration

public actual class ReentrantReadWriteLock {

    private val lock = PortableReentrantReadWriteLock()
    private val readLock = ReadLock(lock)
    private val writeLock = WriteLock(lock)

    public actual fun readLock(): ReadLock = readLock

    public actual fun writeLock(): WriteLock = writeLock

    public actual fun getReadHoldCount(): Int = lock.readHoldCount()

    public actual fun getWriteHoldCount(): Int = lock.writeHoldCount()

    public actual class ReadLock internal constructor(
        private val lock: PortableReentrantReadWriteLock,
    ) : Lock {

        actual override fun lock(): Unit = lock.lockRead()

        actual override fun lockInterruptibly(): Unit = lock.lockReadInterruptibly()

        actual override fun tryLock(): Boolean = lock.tryLockRead()

        override fun tryLock(timeout: Duration): Boolean =
            lock.tryLockRead(timeout.inWholeNanoseconds)

        actual override fun unlock(): Unit = lock.unlockRead()
    }

    public actual class WriteLock internal constructor(
        private val lock: PortableReentrantReadWriteLock,
    ) : Lock {

        actual override fun lock(): Unit = lock.lockWrite()

        actual override fun lockInterruptibly(): Unit = lock.lockWriteInterruptibly()

        actual override fun tryLock(): Boolean = lock.tryLockWrite()

        override fun tryLock(timeout: Duration): Boolean =
            lock.tryLockWrite(timeout.inWholeNanoseconds)

        actual override fun unlock(): Unit = lock.unlockWrite()
    }
}
