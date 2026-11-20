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

import koncurrent.locks.ReadWriteLockContractTest
import koncurrent.locks.TestLock
import koncurrent.locks.TestReadWriteLock

/**
 * Tests [PortableReentrantReadWriteLock] on real threads, on the JVM too where it backs nothing.
 */
class PortableReentrantReadWriteLockStressTest : ReadWriteLockContractTest() {

    override fun newLock(): TestReadWriteLock {
        val lock = PortableReentrantReadWriteLock()

        return TestReadWriteLock(
            read = TestLock(
                lock = lock::lockRead,
                lockInterruptibly = lock::lockReadInterruptibly,
                tryLock = lock::tryLockRead,
                tryLockFor = { lock.tryLockRead(it.inWholeNanoseconds) },
                unlock = lock::unlockRead,
            ),
            write = TestLock(
                lock = lock::lockWrite,
                lockInterruptibly = lock::lockWriteInterruptibly,
                tryLock = lock::tryLockWrite,
                tryLockFor = { lock.tryLockWrite(it.inWholeNanoseconds) },
                unlock = lock::unlockWrite,
            ),
            readHoldCount = lock::readHoldCount,
            writeHoldCount = lock::writeHoldCount,
        )
    }
}
