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

import org.jetbrains.lincheck.datastructures.ModelCheckingOptions
import org.jetbrains.lincheck.datastructures.Operation
import org.jetbrains.lincheck.datastructures.StressOptions
import kotlin.test.Test

private const val THREAD_COUNT = 3
private const val ACTOR_COUNT = 3
private const val MODEL_CHECKING_INVOCATIONS = 1_000

// A time that is over as soon as a thread has to wait
private const val BRIEF_NANOS = 1L

/**
 * Checks with Lincheck that [PortableReentrantLock] excludes threads from each other and never
 * leaves one waiting. The counter is a plain field, so a lost exclusion loses an increment.
 */
class PortableReentrantLockLinearizabilityTest {

    private val lock = PortableReentrantLock()
    private var counter = 0

    @Operation
    fun increment(): Int {
        lock.lock()

        try {
            return ++counter
        } finally {
            lock.unlock()
        }
    }

    @Operation
    fun reentrantIncrement(): Int {
        lock.lock()

        try {
            return increment()
        } finally {
            lock.unlock()
        }
    }

    // The thread joins the queue when the lock is held, then leaves it since its time is over.
    // It may also get the lock, so its outcome is not returned.
    @Operation
    fun tryBriefly() {
        if (lock.tryLock(BRIEF_NANOS)) {
            lock.unlock()
        }
    }

    @Operation
    fun get(): Int {
        lock.lock()

        try {
            return counter
        } finally {
            lock.unlock()
        }
    }

    // Lets Lincheck tell that two sequential executions reached the same state

    override fun equals(other: Any?): Boolean =
        other is PortableReentrantLockLinearizabilityTest && other.counter == counter

    override fun hashCode(): Int = counter

    @Test
    fun modelChecking() {
        ModelCheckingOptions()
            .threads(THREAD_COUNT)
            .actorsPerThread(ACTOR_COUNT)
            .invocationsPerIteration(MODEL_CHECKING_INVOCATIONS)
            .check(this::class)
    }

    @Test
    fun stress() {
        StressOptions()
            .threads(THREAD_COUNT)
            .actorsPerThread(ACTOR_COUNT)
            .check(this::class)
    }
}
