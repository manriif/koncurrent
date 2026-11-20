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
@file:OptIn(ExperimentalForeignApi::class)

package koncurrent.internal

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.free
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import platform.posix.pthread_cond_destroy
import platform.posix.pthread_cond_init
import platform.posix.pthread_cond_signal
import platform.posix.pthread_cond_tVar
import platform.posix.pthread_cond_timedwait_relative_np
import platform.posix.pthread_cond_wait
import platform.posix.pthread_mutex_destroy
import platform.posix.pthread_mutex_init
import platform.posix.pthread_mutex_lock
import platform.posix.pthread_mutex_tVar
import platform.posix.pthread_mutex_unlock
import platform.posix.timespec

private const val NANOS_PER_SECOND = 1_000_000_000L

// The pthread types are integers on Windows, and structures on the other targets
internal actual class Monitor {

    private val mutex = nativeHeap.alloc<pthread_mutex_tVar>()
    private val condition = nativeHeap.alloc<pthread_cond_tVar>()

    init {
        check(pthread_mutex_init(mutex.ptr, null) == 0)
        check(pthread_cond_init(condition.ptr, null) == 0)
    }

    actual fun enter() {
        val _ = pthread_mutex_lock(mutex.ptr)
    }

    actual fun exit() {
        val _ = pthread_mutex_unlock(mutex.ptr)
    }

    actual fun await() {
        val _ = pthread_cond_wait(condition.ptr, mutex.ptr)
    }

    actual fun await(nanos: Long): Unit = memScoped {
        val time = alloc<timespec>()

        time.tv_sec = (nanos / NANOS_PER_SECOND).convert()
        time.tv_nsec = (nanos % NANOS_PER_SECOND).convert()

        val _ = pthread_cond_timedwait_relative_np(condition.ptr, mutex.ptr, time.ptr)
    }

    actual fun signal() {
        val _ = pthread_cond_signal(condition.ptr)
    }

    actual fun destroy() {
        val _ = pthread_cond_destroy(condition.ptr)
        val _ = pthread_mutex_destroy(mutex.ptr)

        nativeHeap.free(condition)
        nativeHeap.free(mutex)
    }
}
