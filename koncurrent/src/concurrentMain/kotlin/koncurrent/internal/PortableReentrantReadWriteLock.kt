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
/*
 * Ported from the ReentrantReadWriteLock of JSR-166, which was written by Doug Lea with
 * assistance from members of JCP JSR-166 Expert Group and released to the public domain, as
 * explained at http://creativecommons.org/publicdomain/zero/1.0/
 */
// Kotlin/Native gives each function its own frame of GC roots, even where LLVM inlines it. The
// small functions of the hot paths are inlined by Kotlin, which merges their frames.
@file:Suppress("NOTHING_TO_INLINE")

package koncurrent.internal

import koncurrent.InterruptedException
import koncurrent.ThreadLocal
import koncurrent.locks.IllegalMonitorStateException

// The state counts the holds of the write lock in its low half, and those of the read lock in
// its high half
private const val SHARED_SHIFT = 16
private const val SHARED_UNIT = 1 shl SHARED_SHIFT
private const val MAX_COUNT = (1 shl SHARED_SHIFT) - 1
private const val EXCLUSIVE_MASK = (1 shl SHARED_SHIFT) - 1

private const val TOO_MANY_HOLDS = "Maximum lock count exceeded"

/**
 * The `ReentrantReadWriteLock` of JSR-166 in common Kotlin. It backs the public lock where the
 * platform has none.
 *
 * Several threads can hold the read lock at once, while no thread holds the write lock. One
 * thread at a time holds the write lock. That thread can also take the read lock, and keep it
 * after it released the write lock. A thread that holds the read lock cannot take the write
 * lock, it would wait for itself.
 *
 * A thread that asks for a free lock takes it, even when other threads wait in the queue. One
 * rule keeps writers from waiting forever while readers follow each other. A thread that does
 * not read yet lets the first thread of the queue pass when it waits to write.
 *
 * A [fair] lock goes to the threads in the order of their arrival. A thread then only takes a
 * free lock when no other thread waits for it, and joins the queue without trying a few times
 * first. A thread that already reads still reads again, since the threads of the queue wait for
 * it.
 *
 * A fair lock is told by its class, [FairPortableReentrantReadWriteLock], and not by a field. A
 * field would sit next to those that the readers write. A reader that looks at it before it
 * updates the state then waits for the memory that another reader just wrote, which was
 * measured to cost two readers a fifth of their speed. The class is read from the header of the
 * object, which no thread writes. And a reader looks at the class only once the lock has a
 * queue, so it reads nothing more than it did without the fair mode until threads contend.
 *
 * The read lock is reentrant, so each thread has a count of its holds. The first reader has its
 * count in the lock. The others have theirs in a thread local.
 *
 * Two things differ from the original, both measured with several readers. The original also
 * caches the count of the last reader in the lock. That is a field that the readers write in
 * turn, which costs them more than their thread local does. And a read hold is released with
 * one atomic addition, where the original retries an atomic update.
 */
internal open class PortableReentrantReadWriteLock : Synchronizer() {

    val fair: Boolean
        get() = this is FairPortableReentrantReadWriteLock

    // The thread that holds the write lock. Not volatile. A thread only needs to tell whether
    // it is the owner, and it always reads its own write.
    private var owner = NO_THREAD

    // The thread that took the read lock when it was free, until it releases it. Not volatile,
    // for the same reason. Its holds are only accessed by that thread.
    private var firstReader = NO_THREAD
    private var firstReaderHolds = 0

    // The holds of each thread that reads, the first reader apart
    private val readHolds = ThreadLocal<HoldCounter>()

    ///////////////////////////////////////////////////////////////////////////
    // Write lock
    ///////////////////////////////////////////////////////////////////////////

    fun lockWrite() {
        val current = currentThreadId()

        if (!tryAcquireWrite(current)) {
            lockWriteContended(current)
        }
    }

    // Apart from lockWrite on purpose. Synchronizer.acquire tells why.
    private fun lockWriteContended(current: Int) {
        if (fair) {
            val _ = lockWriteQueued(current, interruptible = false, timed = false, 0L)
        } else {
            acquire(shared = false, interruptible = false) { tryAcquireWrite(current) }
        }
    }

    /**
     * Acquires the write lock as [lockWrite] does, unless the thread is interrupted before or
     * while it waits.
     */
    fun lockWriteInterruptibly() {
        val _ = acquireWriteOrGiveUp(timed = false, nanos = 0L)
    }

    /**
     * Acquires the write lock if it can within [nanos] nanoseconds, unless the thread is
     * interrupted before or while it waits.
     */
    fun tryLockWrite(nanos: Long): Boolean = acquireWriteOrGiveUp(timed = true, nanos)

    private fun acquireWriteOrGiveUp(timed: Boolean, nanos: Long): Boolean {
        if (clearInterrupt()) {
            throw InterruptedException()
        }

        val current = currentThreadId()

        if (tryAcquireWrite(current)) {
            return true
        }

        if (timed && nanos <= 0L) {
            return false
        }

        val deadline = if (timed) deadlineAfter(nanos) else 0L

        return lockWriteQueued(current, interruptible = true, timed, deadline)
    }

    // Apart from its callers on purpose. Synchronizer.acquire tells why.
    private fun lockWriteQueued(
        current: Int,
        interruptible: Boolean,
        timed: Boolean,
        deadline: Long,
    ): Boolean {
        return acquireQueued(null, shared = false, interruptible, timed, deadline) {
            tryAcquireWrite(current)
        }
    }

    // Takes a free lock even when other threads wait for it, and even when the lock is fair
    fun tryLockWrite(): Boolean = tryAcquireWrite(currentThreadId(), inTurn = false)

    fun unlockWrite() {
        if (owner != currentThreadId()) {
            throw IllegalMonitorStateException(WRITE_LOCK_NOT_HELD)
        }

        val next = state.load() - 1

        if (exclusiveCount(next) == 0) {
            owner = NO_THREAD
            state.store(next)
            signalNext()
        } else {
            state.store(next)
        }
    }

    fun writeHoldCount(): Int = if (owner == currentThreadId()) exclusiveCount(state.load()) else 0

    private fun tryAcquireWrite(current: Int): Boolean = tryAcquireWrite(current, inTurn = true)

    // [inTurn] tells to leave a free lock to the queue when a fair lock has to. Inline, so that
    // a try that takes no turn does not look at the queue.
    private inline fun tryAcquireWrite(current: Int, inTurn: Boolean): Boolean {
        val c = state.load()

        if (c != 0) {
            val writes = exclusiveCount(c)

            // When no thread writes, a state that is not zero counts readers
            if (writes == 0 || owner != current) {
                return false
            }

            if (writes == MAX_COUNT) {
                throw Error(TOO_MANY_HOLDS)
            }

            // Only the owner changes the state while it writes
            state.store(c + 1)
            return true
        }

        if ((inTurn && fair && hasQueuedPredecessors()) || !state.compareAndSet(c, c + 1)) {
            return false
        }

        owner = current
        return true
    }

    ///////////////////////////////////////////////////////////////////////////
    // Read lock
    ///////////////////////////////////////////////////////////////////////////

    fun lockRead() {
        val current = currentThreadId()

        if (!tryAcquireRead(current)) {
            lockReadContended(current)
        }
    }

    // Apart from lockRead on purpose. Synchronizer.acquire tells why.
    private fun lockReadContended(current: Int) {
        if (fair) {
            val _ = lockReadQueued(current, interruptible = false, timed = false, 0L)
        } else {
            acquire(shared = true, interruptible = false) { tryAcquireRead(current) }
        }
    }

    /**
     * Acquires the read lock as [lockRead] does, unless the thread is interrupted before or
     * while it waits.
     */
    fun lockReadInterruptibly() {
        val _ = acquireReadOrGiveUp(timed = false, nanos = 0L)
    }

    /**
     * Acquires the read lock if it can within [nanos] nanoseconds, unless the thread is
     * interrupted before or while it waits.
     */
    fun tryLockRead(nanos: Long): Boolean = acquireReadOrGiveUp(timed = true, nanos)

    private fun acquireReadOrGiveUp(timed: Boolean, nanos: Long): Boolean {
        if (clearInterrupt()) {
            throw InterruptedException()
        }

        val current = currentThreadId()

        if (tryAcquireRead(current)) {
            return true
        }

        if (timed && nanos <= 0L) {
            return false
        }

        val deadline = if (timed) deadlineAfter(nanos) else 0L

        return lockReadQueued(current, interruptible = true, timed, deadline)
    }

    // Apart from its callers on purpose. Synchronizer.acquire tells why.
    private fun lockReadQueued(
        current: Int,
        interruptible: Boolean,
        timed: Boolean,
        deadline: Long,
    ): Boolean {
        return acquireQueued(null, shared = true, interruptible, timed, deadline) {
            tryAcquireRead(current)
        }
    }

    fun tryLockRead(): Boolean {
        val current = currentThreadId()

        while (true) {
            val c = state.load()

            if (exclusiveCount(c) != 0 && owner != current) {
                return false
            }

            val readers = sharedCount(c)

            if (readers == MAX_COUNT) {
                throw Error(TOO_MANY_HOLDS)
            }

            if (state.compareAndSet(c, c + SHARED_UNIT)) {
                countHold(current, readers)
                return true
            }
        }
    }

    fun unlockRead() {
        val current = currentThreadId()

        if (firstReader == current) {
            if (firstReaderHolds == 1) {
                firstReader = NO_THREAD
            } else {
                firstReaderHolds--
            }
        } else {
            val holds = readHolds.get()

            if (holds == null || holds.count == 0) {
                throw IllegalMonitorStateException(READ_LOCK_NOT_HELD)
            }

            if (holds.count == 1) {
                readHolds.remove()
            }

            holds.count--
        }

        // This does nothing for readers. It lets a waiting writer in once both locks are free.
        if (state.addAndFetch(-SHARED_UNIT) == 0) {
            signalNext()
        }
    }

    fun readHoldCount(): Int {
        if (sharedCount(state.load()) == 0) {
            return 0
        }

        return readHoldCount(currentThreadId())
    }

    private fun readHoldCount(current: Int): Int {
        if (firstReader == current) {
            return firstReaderHolds
        }

        return readHolds.get()?.count ?: 0
    }

    private fun tryAcquireRead(current: Int): Boolean {
        val c = state.load()

        if (exclusiveCount(c) != 0 && owner != current) {
            return false
        }

        val readers = sharedCount(c)

        if (
            !readerShouldWait() &&
            readers < MAX_COUNT &&
            state.compareAndSet(c, c + SHARED_UNIT)
        ) {
            countHold(current, readers)
            return true
        }

        return fullTryAcquireRead(current)
    }

    // Tells whether a thread that does not read yet must let the queue pass. For a fair lock
    // that is any thread that has waited longer, else a writer that waits first. The queue is
    // looked for first: until there is one, the lock does not look at its class.
    private inline fun readerShouldWait(): Boolean {
        val head = head ?: return false

        return if (fair) hasQueuedPredecessors() else apparentlyFirstQueuedIsExclusive(head)
    }

    /**
     * Tries to take the read lock again after a first try failed, on a lost update of the
     * state or because a thread of the queue must pass first.
     */
    private fun fullTryAcquireRead(current: Int): Boolean {
        while (true) {
            val c = state.load()

            if (exclusiveCount(c) != 0) {
                if (owner != current) {
                    return false
                }

                // Else the thread holds the write lock. It must not wait for itself.
            } else if (readerShouldWait() && readHoldCount(current) == 0) {
                // Lets the queue pass. A thread that already reads goes on, since the queue
                // waits for it.
                return false
            }

            val readers = sharedCount(c)

            if (readers == MAX_COUNT) {
                throw Error(TOO_MANY_HOLDS)
            }

            if (state.compareAndSet(c, c + SHARED_UNIT)) {
                countHold(current, readers)
                return true
            }
        }
    }

    /**
     * Counts one more hold of the read lock for the [current] thread, which took it when the
     * threads together had [readers] holds.
     */
    private fun countHold(current: Int, readers: Int) {
        if (readers == 0) {
            firstReader = current
            firstReaderHolds = 1
        } else if (firstReader == current) {
            firstReaderHolds++
        } else {
            val holds = readHolds.get() ?: HoldCounter().also(readHolds::set)

            holds.count++
        }
    }

}

/**
 * A [PortableReentrantReadWriteLock] that is fair.
 *
 * It has its queue from the start. A reader only asks whether the lock is fair once it has a
 * queue, so a fair lock without one would let a reader pass the first threads that wait.
 */
internal class FairPortableReentrantReadWriteLock : PortableReentrantReadWriteLock() {

    init {
        createQueue()
    }
}

/**
 * The number of holds of the read lock that a thread has.
 */
internal class HoldCounter {

    var count = 0
}

private fun sharedCount(c: Int): Int = c ushr SHARED_SHIFT

private fun exclusiveCount(c: Int): Int = c and EXCLUSIVE_MASK
