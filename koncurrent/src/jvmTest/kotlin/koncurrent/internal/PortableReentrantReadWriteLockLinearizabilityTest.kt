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
 * Checks with Lincheck that [PortableReentrantReadWriteLock] keeps writers alone and never
 * leaves a thread waiting. The counter is a plain field, so a writer that is not alone loses an
 * increment, and a reader that is not kept from a writer may see two values.
 */
class PortableReentrantReadWriteLockLinearizabilityTest {

    private val lock = PortableReentrantReadWriteLock()
    private var counter = 0

    @Operation
    fun write(): Int {
        lock.lockWrite()

        try {
            return ++counter
        } finally {
            lock.unlockWrite()
        }
    }

    @Operation
    fun reentrantWrite(): Int {
        lock.lockWrite()

        try {
            return write()
        } finally {
            lock.unlockWrite()
        }
    }

    @Suppress("KotlinConstantConditions")
    @Operation
    fun read(): Int {
        lock.lockRead()

        try {
            val seen = counter

            return if (counter == seen) seen else -1
        } finally {
            lock.unlockRead()
        }
    }

    @Operation
    fun reentrantRead(): Int {
        lock.lockRead()

        try {
            return read()
        } finally {
            lock.unlockRead()
        }
    }

    // Writes, then keeps reading after it released the write lock
    @Operation
    fun writeThenRead(): Int {
        lock.lockWrite()

        val written = try {
            lock.lockRead()
            ++counter
        } finally {
            lock.unlockWrite()
        }

        try {
            return if (counter == written) written else -1
        } finally {
            lock.unlockRead()
        }
    }

    // The thread joins the queue when it cannot write, then leaves it since its time is over.
    // It may also get the lock, so its outcome is not returned.
    @Operation
    fun tryWriteBriefly() {
        if (lock.tryLockWrite(BRIEF_NANOS)) {
            lock.unlockWrite()
        }
    }

    // The same for the read lock
    @Operation
    fun tryReadBriefly() {
        if (lock.tryLockRead(BRIEF_NANOS)) {
            lock.unlockRead()
        }
    }

    // Lets Lincheck tell that two sequential executions reached the same state

    override fun equals(other: Any?): Boolean =
        other is PortableReentrantReadWriteLockLinearizabilityTest && other.counter == counter

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
