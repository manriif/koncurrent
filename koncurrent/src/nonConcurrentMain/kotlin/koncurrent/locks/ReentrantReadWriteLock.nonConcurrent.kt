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

import koncurrent.internal.READ_LOCK_NOT_HELD
import koncurrent.internal.WRITE_LOCK_NOT_HELD

// JS and Wasm have one thread, which never waits. The holds are counted so that the locks fail
// where they do on the other platforms.
public actual class ReentrantReadWriteLock {

    private var readHolds = 0
    private var writeHolds = 0

    private val readLock = ReadLock(this)
    private val writeLock = WriteLock(this)

    public actual fun readLock(): ReadLock = readLock

    public actual fun writeLock(): WriteLock = writeLock

    public actual fun getReadHoldCount(): Int = readHolds

    public actual fun getWriteHoldCount(): Int = writeHolds

    public actual class ReadLock internal constructor(
        private val locks: ReentrantReadWriteLock,
    ) : Lock {

        actual override fun lock() {
            locks.readHolds++
        }

        actual override fun lockInterruptibly(): Unit = lock()

        actual override fun tryLock(): Boolean {
            locks.readHolds++
            return true
        }

        actual override fun unlock() {
            if (locks.readHolds == 0) {
                throw IllegalMonitorStateException(READ_LOCK_NOT_HELD)
            }

            locks.readHolds--
        }
    }

    public actual class WriteLock internal constructor(
        private val locks: ReentrantReadWriteLock,
    ) : Lock {

        // With threads, a reader that asks for the write lock waits for itself forever
        actual override fun lock() {
            if (!tryLock()) {
                throw IllegalStateException("The write lock cannot be taken while reading")
            }
        }

        actual override fun lockInterruptibly(): Unit = lock()

        actual override fun tryLock(): Boolean {
            if (locks.writeHolds == 0 && locks.readHolds != 0) {
                return false
            }

            locks.writeHolds++
            return true
        }

        actual override fun unlock() {
            if (locks.writeHolds == 0) {
                throw IllegalMonitorStateException(WRITE_LOCK_NOT_HELD)
            }

            locks.writeHolds--
        }
    }
}
