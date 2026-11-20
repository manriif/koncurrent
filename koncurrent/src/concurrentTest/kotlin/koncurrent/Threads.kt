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
package koncurrent

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Runs [action] on [threadCount] threads and waits for them. The threads start their actions
 * together, to maximize contention. Throws the first failure of an action.
 */
internal fun runConcurrently(threadCount: Int, action: (threadIndex: Int) -> Unit) {
    val ready = AtomicInt(0)
    val released = AtomicBoolean(false)

    val joins = List(threadCount) { index ->
        startThread {
            val _ = ready.incrementAndFetch()

            while (!released.load()) {
                // Spins until every thread has started
            }

            action(index)
        }
    }

    while (ready.load() < threadCount) {
        // Spins until every thread has started
    }

    released.store(true)

    val failures = joins.mapNotNull { it() }
    failures.firstOrNull()?.let { throw it }
}

/**
 * Keeps the calling thread busy for [duration].
 */
internal fun spinFor(duration: Duration) {
    val start = TimeSource.Monotonic.markNow()

    while (start.elapsedNow() < duration) {
        // Spins until the duration has passed
    }
}

/**
 * Starts [block] on a new thread. Returns a function that waits for the end of the thread and
 * returns its failure, if any.
 */
internal expect fun startThread(block: () -> Unit): () -> Throwable?

/**
 * A thread of the library that runs [block], for the tests that interrupt a thread. [join]
 * waits for its end and returns its failure, if any.
 */
internal class TestThread(block: () -> Unit) {

    private var failure: Throwable? = null

    val thread: Thread = thread {
        try {
            block()
        } catch (throwable: Throwable) {
            failure = throwable
        }
    }

    fun interrupt(): Unit = thread.interrupt()

    fun join(): Throwable? {
        thread.join()
        return failure
    }
}
