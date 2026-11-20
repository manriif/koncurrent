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
import platform.posix.CLOCK_MONOTONIC
import platform.posix.clock_gettime
import platform.posix.pthread_cond_init
import platform.posix.pthread_cond_t
import platform.posix.pthread_cond_timedwait
import platform.posix.pthread_condattr_destroy
import platform.posix.pthread_condattr_init
import platform.posix.pthread_condattr_setclock
import platform.posix.pthread_condattr_tVar
import platform.posix.pthread_mutex_init
import platform.posix.pthread_mutex_t
import platform.posix.timespec

private const val NANOS_PER_SECOND = 1_000_000_000L

// The condition counts the time of a wait on the clock that never jumps
internal actual fun initialize(
    mutex: CPointer<pthread_mutex_t>,
    condition: CPointer<pthread_cond_t>,
): Unit = memScoped {
    val attributes = alloc<pthread_condattr_tVar>()

    check(pthread_mutex_init(mutex, null) == 0)
    check(pthread_condattr_init(attributes.ptr) == 0)
    check(pthread_condattr_setclock(attributes.ptr, CLOCK_MONOTONIC) == 0)
    check(pthread_cond_init(condition, attributes.ptr) == 0)

    val _ = pthread_condattr_destroy(attributes.ptr)
}

internal actual fun timedWait(
    condition: CPointer<pthread_cond_t>,
    mutex: CPointer<pthread_mutex_t>,
    nanos: Long,
): Unit = memScoped {
    val time = alloc<timespec>()
    val _ = clock_gettime(CLOCK_MONOTONIC, time.ptr)
    val nanosOfSecond = time.tv_nsec.convert<Long>() + nanos % NANOS_PER_SECOND
    val seconds = time.tv_sec.convert<Long>() + nanos / NANOS_PER_SECOND

    time.tv_sec = (seconds + nanosOfSecond / NANOS_PER_SECOND).convert()
    time.tv_nsec = (nanosOfSecond % NANOS_PER_SECOND).convert()

    val _ = pthread_cond_timedwait(condition, mutex, time.ptr)
}
