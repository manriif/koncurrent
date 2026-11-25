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

import android.annotation.SuppressLint
import android.os.Build
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.StampedLock

/**
 * Whether the platform has a stamped lock, which it has from API 24.
 */
internal val HasPlatformStampedLock: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

/**
 * The platform lock. It exists from API 24, so it must not be loaded below.
 */
@SuppressLint("NewApi")
internal class PlatformStampedLock : StampedLock(), StampedLockBackend {

    override fun tryWriteLock(nanos: Long): Long = tryWriteLock(nanos, TimeUnit.NANOSECONDS)

    override fun tryReadLock(nanos: Long): Long = tryReadLock(nanos, TimeUnit.NANOSECONDS)
}
