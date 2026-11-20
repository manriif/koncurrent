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
@file:OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)

package koncurrent.internal

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.posix.pthread_cond_init
import platform.posix.pthread_cond_t
import platform.posix.pthread_cond_timedwait_relative_np
import platform.posix.pthread_mutex_init
import platform.posix.pthread_mutex_t
import platform.posix.timespec

private const val NANOS_PER_SECOND = 1_000_000_000L

internal actual fun initialize(
    mutex: CPointer<pthread_mutex_t>,
    condition: CPointer<pthread_cond_t>,
) {
    check(pthread_mutex_init(mutex, null) == 0)
    check(pthread_cond_init(condition, null) == 0)
}

// Apple takes the time to wait as it is, where the other systems take the moment to wake up
internal actual fun timedWait(
    condition: CPointer<pthread_cond_t>,
    mutex: CPointer<pthread_mutex_t>,
    nanos: Long,
): Unit = memScoped {
    val time = alloc<timespec>()

    time.tv_sec = (nanos / NANOS_PER_SECOND).convert()
    time.tv_nsec = (nanos % NANOS_PER_SECOND).convert()

    val _ = pthread_cond_timedwait_relative_np(condition, mutex, time.ptr)
}
