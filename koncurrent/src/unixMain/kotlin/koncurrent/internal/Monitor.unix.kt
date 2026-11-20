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

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import platform.posix.pthread_cond_destroy
import platform.posix.pthread_cond_signal
import platform.posix.pthread_cond_t
import platform.posix.pthread_cond_wait
import platform.posix.pthread_mutex_destroy
import platform.posix.pthread_mutex_lock
import platform.posix.pthread_mutex_t
import platform.posix.pthread_mutex_unlock

internal actual class Monitor {

    private val mutex = nativeHeap.alloc<pthread_mutex_t>()
    private val condition = nativeHeap.alloc<pthread_cond_t>()

    init {
        initialize(mutex.ptr, condition.ptr)
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

    actual fun await(nanos: Long): Unit = timedWait(condition.ptr, mutex.ptr, nanos)

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

/**
 * Initializes [mutex] and [condition] with the default attributes. The type of the attributes
 * differs between the unix targets.
 */
internal expect fun initialize(
    mutex: CPointer<pthread_mutex_t>,
    condition: CPointer<pthread_cond_t>,
)

/**
 * Waits on [condition] for [nanos] nanoseconds at most, with [mutex] held. The way to count
 * that time on a clock that never jumps differs between the unix targets.
 */
internal expect fun timedWait(
    condition: CPointer<pthread_cond_t>,
    mutex: CPointer<pthread_mutex_t>,
    nanos: Long,
)
