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

import koncurrent.InternalForInheritanceKoncurrentApi
import koncurrent.InterruptedException
import koncurrent.internal.awaitWithin
import kotlin.time.Duration

/**
 * A place where the threads that hold a lock wait for something to become true. It is created
 * by [ReentrantLock.newCondition].
 *
 * A thread calls [await] while it holds the lock. This releases the lock and puts the thread to
 * sleep. Another thread then takes the lock, changes the state that the first one waits for,
 * and calls [signal] or [signalAll]. The first thread wakes up, and returns from [await] once
 * it holds the lock again.
 *
 * [await] may return while nothing was signalled. So always wait in a loop that checks the
 * state again:
 *
 * ```kotlin
 * lock.withLock {
 *     while (queue.isEmpty()) {
 *         notEmpty.await()
 *     }
 *
 *     queue.removeFirst()
 * }
 * ```
 *
 * This interface is not meant to be implemented outside of this library. On the JVM and Android
 * it is `java.util.concurrent.locks.Condition`, which has more functions to implement.
 *
 * ### Platforms
 *
 * - JS and Wasm have one thread, which nothing could wake up. [await] throws
 *   [IllegalStateException] there.
 */
@SubclassOptInRequired(InternalForInheritanceKoncurrentApi::class)
public expect interface Condition {

    /**
     * Releases the lock and waits until another thread signals this condition. The current
     * thread holds the lock again when this returns or throws, with as many holds as before.
     *
     * @throws IllegalMonitorStateException if the current thread does not hold the lock
     * @throws InterruptedException if the thread is interrupted before it is signalled
     */
    public fun await()

    /**
     * Wakes up one of the threads that wait on this condition, if any. The longest waiting
     * thread is chosen. It returns from [await] once it has the lock again.
     *
     * @throws IllegalMonitorStateException if the current thread does not hold the lock
     */
    public fun signal()

    /**
     * Wakes up all the threads that wait on this condition. Each returns from [await] once it
     * has the lock again, one after the other.
     *
     * @throws IllegalMonitorStateException if the current thread does not hold the lock
     */
    public fun signalAll()
}

/**
 * Waits as [Condition.await] does, for [timeout] at most. Does not wait when [timeout] is zero
 * or negative. The current thread holds the lock again when this returns or throws.
 *
 * On JS and Wasm this returns `false` at once, since no other thread could signal.
 *
 * @return `false` if the time ran out before a signal
 * @throws IllegalMonitorStateException if the current thread does not hold the lock
 * @throws InterruptedException if the thread is interrupted before it is signalled
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER") // A member on native, JS and Wasm, which this calls
public fun Condition.await(timeout: Duration): Boolean = awaitWithin(timeout.inWholeNanoseconds)
