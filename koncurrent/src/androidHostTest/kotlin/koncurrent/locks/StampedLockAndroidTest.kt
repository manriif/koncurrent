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

import koncurrent.internal.PlatformStampedLock
import koncurrent.internal.PortableStampedLock
import kotlin.test.Test
import kotlin.test.assertIs

/**
 * Tests the public [StampedLock] over the platform lock, which backs it from API 24. The tests
 * run on the host, where the API level reads 0. So the tests of the other source sets cover
 * the port, which backs the lock below API 24.
 */
class PlatformStampedLockTest : StampedLockContractTest() {

    override fun newLock(): TestStampedLock =
        StampedLock(PlatformStampedLock()).asTestStampedLock()

    @Test
    fun portBacksTheLockBelowApi24() {
        assertIs<PortableStampedLock>(StampedLock().lock)
    }
}

/**
 * Tests the views and the block functions of the public [StampedLock] over the platform lock.
 */
class PlatformStampedLockViewsTest : StampedLockViewsContractTest() {

    override fun newLock(): StampedLock = StampedLock(PlatformStampedLock())
}

/**
 * Stress tests of the public [StampedLock] over the platform lock.
 */
class PlatformStampedLockStressTest : StampedLockConcurrencyContractTest() {

    override fun newLock(): TestStampedLock =
        StampedLock(PlatformStampedLock()).asTestStampedLock()
}
