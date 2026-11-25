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
 * Checks with Lincheck that [PortableStampedLock] keeps writers alone, that a stamp is only
 * valid while no thread wrote, and that no thread is left waiting. The counter is a plain
 * field, so a writer that is not alone loses an increment, and a reader that is not kept from
 * a writer may see two values.
 */
class PortableStampedLockLinearizabilityTest {

    private val lock = PortableStampedLock()
    private var counter = 0

    @Operation
    fun write(): Int {
        val stamp = lock.writeLock()

        try {
            return ++counter
        } finally {
            lock.unlockWrite(stamp)
        }
    }

    @Suppress("KotlinConstantConditions")
    @Operation
    fun read(): Int {
        val stamp = lock.readLock()

        try {
            val seen = counter

            return if (counter == seen) seen else -1
        } finally {
            lock.unlockRead(stamp)
        }
    }

    // Reads without a lock, and with one when a thread wrote meanwhile
    @Operation
    fun optimisticRead(): Int {
        val stamp = lock.tryOptimisticRead()
        val seen = counter

        return if (lock.validate(stamp)) seen else read()
    }

    // Writes at once if no thread wrote since it looked, else waits for the write lock
    @Operation
    fun writeAfterLooking(): Int {
        val optimistic = lock.tryOptimisticRead()
        val seen = counter
        val stamp = lock.tryConvertToWriteLock(optimistic)

        if (stamp == 0L) {
            return write()
        }

        try {
            return if (counter == seen) ++counter else -1
        } finally {
            lock.unlockWrite(stamp)
        }
    }

    // Writes, then keeps reading without letting a writer in between
    @Operation
    fun writeThenRead(): Int {
        val write = lock.writeLock()
        val written = ++counter
        val read = lock.tryConvertToReadLock(write)

        try {
            return if (counter == written) written else -1
        } finally {
            lock.unlockRead(read)
        }
    }

    // Reads, then writes at once when it is the only reader, else waits for the write lock
    @Operation
    fun readThenWrite(): Int {
        val read = lock.readLock()
        val seen = counter
        val write = lock.tryConvertToWriteLock(read)

        if (write == 0L) {
            lock.unlockRead(read)
            return write()
        }

        try {
            return if (counter == seen) ++counter else -1
        } finally {
            lock.unlockWrite(write)
        }
    }

    // Gives its read lock back for a stamp, which stays valid until a thread writes
    @Operation
    fun readThenLook(): Int {
        val read = lock.readLock()
        val seen = counter
        val optimistic = lock.tryConvertToOptimisticRead(read)
        val seenAgain = counter

        return if (lock.validate(optimistic) && seenAgain != seen) -1 else seen
    }

    // The thread joins the queue when it cannot write, then leaves it since its time is over.
    // It may also get the lock, so its outcome is not returned.
    @Operation
    fun tryWriteBriefly() {
        val stamp = lock.tryWriteLock(BRIEF_NANOS)

        if (stamp != 0L) {
            lock.unlockWrite(stamp)
        }
    }

    // The same for the read lock
    @Operation
    fun tryReadBriefly() {
        val stamp = lock.tryReadLock(BRIEF_NANOS)

        if (stamp != 0L) {
            lock.unlockRead(stamp)
        }
    }

    // Lets Lincheck tell that two sequential executions reached the same state

    override fun equals(other: Any?): Boolean =
        other is PortableStampedLockLinearizabilityTest && other.counter == counter

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
