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
 * Ported from the StampedLock of JSR-166, which was written by Doug Lea with assistance from
 * members of JCP JSR-166 Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
// Kotlin/Native gives each function its own frame of GC roots, even where LLVM inlines it. The
// small functions of the hot paths are inlined by Kotlin, which merges their frames.
@file:Suppress("NOTHING_TO_INLINE")

package koncurrent.internal

import koncurrent.InterruptedException
import koncurrent.locks.IllegalMonitorStateException
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong

// The state counts the readers in its low bits. The next bit is set while a thread writes. The
// bits above count the write locks that were released, so a write never leaves the state as it
// found it.
private const val READ_BIT_COUNT = 7
private const val READ_UNIT = 1L
private const val WRITE_BIT = 1L shl READ_BIT_COUNT
private const val READ_BITS = WRITE_BIT - 1L
private const val LOCK_BITS = READ_BITS or WRITE_BIT

// The most readers that the state counts. The others are counted apart. All the reader bits
// are only set while a thread changes that other count.
private const val READ_FULL = READ_BITS - 1L

// What a stamp is compared by: the write bit and the count of the writes
private const val STAMP_BITS = READ_BITS.inv()

// Clears the write bit and the highest reader bit, to expect a state that no thread writes
// under and that is far from full
private const val READ_SAFE = (3L shl (READ_BIT_COUNT - 1)).inv()

// The first state. A stamp is never zero, which tells a failure.
private const val ORIGIN = WRITE_BIT shl 1

/**
 * The `StampedLock` of JSR-166 in common Kotlin. It backs the public lock where the platform
 * has none.
 *
 * The state is one number. A thread that takes a lock gets the new state as its stamp. An
 * optimistic reader takes the state as it is, and [validate] compares it later. The state is
 * the same unless a thread wrote in between.
 *
 * The lock is not reentrant and has no owner. A thread that reads and asks for the write lock
 * waits for itself. Any thread releases a lock with its stamp.
 *
 * As in the original, a thread that asks for a lock takes it if it can, whatever the queue
 * holds. One rule keeps writers from waiting forever while readers follow each other. A reader
 * that did not get the lock at its first try lets the first thread of the queue pass when it
 * waits to write.
 *
 * Two things differ from the original. It has a queue of its own, where the readers that wait
 * together share a node. Here the threads wait in the [Synchronizer] of the other locks, a
 * node each, and a reader that acquires wakes up the next one. And a thread first tries a few
 * times before it joins the queue, as for the other locks.
 *
 * The original orders its accesses to the memory with fences, which Kotlin does not have.
 * [writeStamp] and [loadFence] tell what replaces them.
 */
internal class PortableStampedLock : Synchronizer(), StampedLockBackend {

    // The modes and the stamp of the lock. The state of the queue class has 32 bits. A stamp
    // needs more, so that it never comes back to a value that a reader still holds.
    private val lockState = AtomicLong(ORIGIN)

    // The readers beyond READ_FULL. They are counted in the state of the queue class, which
    // this lock has no other use for. Only the thread that set all the reader bits changes it.
    private inline val readerOverflow: AtomicInt
        get() = state

    ///////////////////////////////////////////////////////////////////////////
    // Write lock
    ///////////////////////////////////////////////////////////////////////////

    override fun writeLock(): Long {
        // Expects a free lock, whatever the state is. The update fails on a lock that is held.
        val s = lockState.load() and LOCK_BITS.inv()

        if (lockState.compareAndSet(s, s or WRITE_BIT)) {
            return writeStamp()
        }

        return writeLockContended()
    }

    // Apart from writeLock on purpose. Synchronizer.acquire tells why.
    private fun writeLockContended(): Long {
        var stamp = 0L

        acquire(shared = false, interruptible = false) {
            stamp = tryAcquireWrite()
            stamp != 0L
        }

        return stamp
    }

    override fun tryWriteLock(): Long = tryAcquireWrite()

    override fun tryWriteLock(nanos: Long): Long = acquireWriteOrGiveUp(timed = true, nanos)

    override fun writeLockInterruptibly(): Long = acquireWriteOrGiveUp(timed = false, nanos = 0L)

    private fun acquireWriteOrGiveUp(timed: Boolean, nanos: Long): Long {
        if (clearInterrupt()) {
            throw InterruptedException()
        }

        val stamp = tryAcquireWrite()

        if (stamp != 0L || (timed && nanos <= 0L)) {
            return stamp
        }

        val deadline = if (timed) deadlineAfter(nanos) else 0L

        return writeLockQueued(timed, deadline)
    }

    // Apart from its caller on purpose. Synchronizer.acquire tells why.
    private fun writeLockQueued(timed: Boolean, deadline: Long): Long {
        var stamp = 0L

        val _ = acquireQueued(null, shared = false, interruptible = true, timed, deadline) {
            stamp = tryAcquireWrite()
            stamp != 0L
        }

        return stamp
    }

    private inline fun tryAcquireWrite(): Long {
        val s = lockState.load()

        if ((s and LOCK_BITS) == 0L && lockState.compareAndSet(s, s or WRITE_BIT)) {
            return writeStamp()
        }

        return 0L
    }

    /**
     * Returns the stamp of the write lock that the calling thread just took, which is the
     * state. It reads the state again rather than returning what it wrote.
     *
     * That read is the store fence of the original. On some processors the update of the state
     * may reach the other threads after the writes that follow it. An optimistic reader could
     * then read what the writer changed, and still find the state of before. A read of an
     * atomic comes after the update, and the writes that follow come after that read.
     */
    private inline fun writeStamp(): Long = lockState.load()

    override fun unlockWrite(stamp: Long) {
        if ((stamp and WRITE_BIT) == 0L || lockState.load() != stamp) {
            throw IllegalMonitorStateException(STAMP_NOT_HELD)
        }

        val _ = releaseWrite(stamp)
    }

    @IgnorableReturnValue
    override fun tryUnlockWrite(): Boolean {
        val s = lockState.load()

        if ((s and WRITE_BIT) == 0L) {
            return false
        }

        val _ = releaseWrite(s)
        return true
    }

    // Releases the write lock of the state [s], and returns the new state
    private inline fun releaseWrite(s: Long): Long {
        val next = afterWrite(s)

        lockState.store(next)
        signalNext()

        return next
    }

    ///////////////////////////////////////////////////////////////////////////
    // Read lock
    ///////////////////////////////////////////////////////////////////////////

    override fun readLock(): Long {
        // Expects a lock that no thread writes under, whatever the state is
        val s = lockState.load() and READ_SAFE
        val next = s + READ_UNIT

        if (lockState.compareAndSet(s, next)) {
            return next
        }

        return readLockContended()
    }

    // Apart from readLock on purpose. Synchronizer.acquire tells why.
    private fun readLockContended(): Long {
        var stamp = 0L

        acquire(shared = true, interruptible = false) {
            stamp = tryAcquireReadInTurn()
            stamp != 0L
        }

        return stamp
    }

    override fun tryReadLock(): Long = tryAcquireRead()

    override fun tryReadLock(nanos: Long): Long = acquireReadOrGiveUp(timed = true, nanos)

    override fun readLockInterruptibly(): Long = acquireReadOrGiveUp(timed = false, nanos = 0L)

    private fun acquireReadOrGiveUp(timed: Boolean, nanos: Long): Long {
        if (clearInterrupt()) {
            throw InterruptedException()
        }

        // As in the original, only a try with a time limit looks at the queue first
        val stamp = if (timed) tryAcquireReadInTurn() else tryAcquireRead()

        if (stamp != 0L || (timed && nanos <= 0L)) {
            return stamp
        }

        val deadline = if (timed) deadlineAfter(nanos) else 0L

        return readLockQueued(timed, deadline)
    }

    // Apart from its caller on purpose. Synchronizer.acquire tells why.
    private fun readLockQueued(timed: Boolean, deadline: Long): Long {
        var stamp = 0L

        val _ = acquireQueued(null, shared = true, interruptible = true, timed, deadline) {
            stamp = tryAcquireReadInTurn()
            stamp != 0L
        }

        return stamp
    }

    // Lets the first thread of the queue pass when it waits to write. Readers that follow each
    // other would else keep it out for as long as they come.
    private inline fun tryAcquireReadInTurn(): Long {
        val head = head

        if (head != null && apparentlyFirstQueuedIsExclusive(head)) {
            return 0L
        }

        return tryAcquireRead()
    }

    private fun tryAcquireRead(): Long {
        while (true) {
            val s = lockState.load()

            if ((s and LOCK_BITS) == WRITE_BIT) {
                return 0L
            }

            val next = tryAddReader(s)

            if (next != 0L) {
                return next
            }
        }
    }

    // Tries once to count a reader in the state [s], which no thread writes under. Returns the
    // new state, or zero when the state changed first.
    private inline fun tryAddReader(s: Long): Long {
        if ((s and LOCK_BITS) >= READ_FULL) {
            return tryAddOverflowReader(s)
        }

        return if (lockState.compareAndSet(s, s + READ_UNIT)) s + READ_UNIT else 0L
    }

    override fun unlockRead(stamp: Long) {
        // A stamp of the read lock has readers
        if ((stamp and READ_BITS) != 0L) {
            while (true) {
                val s = lockState.load()

                if ((s and STAMP_BITS) != (stamp and STAMP_BITS) || (s and READ_BITS) == 0L) {
                    break
                }

                if (tryRemoveReader(s) != 0L) {
                    return
                }
            }
        }

        throw IllegalMonitorStateException(STAMP_NOT_HELD)
    }

    @IgnorableReturnValue
    override fun tryUnlockRead(): Boolean {
        while (true) {
            val s = lockState.load()

            if ((s and READ_BITS) == 0L) {
                return false
            }

            if (tryRemoveReader(s) != 0L) {
                return true
            }
        }
    }

    // Tries once to take a reader out of the state [s], which has some. Returns the new state,
    // or zero when the state changed first. The last reader lets a waiting writer in.
    private inline fun tryRemoveReader(s: Long): Long {
        val readers = s and READ_BITS

        if (readers >= READ_FULL) {
            return tryRemoveOverflowReader(s)
        }

        if (!lockState.compareAndSet(s, s - READ_UNIT)) {
            return 0L
        }

        if (readers == READ_UNIT) {
            signalNext()
        }

        return s - READ_UNIT
    }

    ///////////////////////////////////////////////////////////////////////////
    // Optimistic read
    ///////////////////////////////////////////////////////////////////////////

    override fun tryOptimisticRead(): Long {
        val s = lockState.load()

        return if ((s and WRITE_BIT) == 0L) s and STAMP_BITS else 0L
    }

    override fun validate(stamp: Long): Boolean {
        loadFence()
        return (stamp and STAMP_BITS) == (lockState.load() and STAMP_BITS)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Any stamp
    ///////////////////////////////////////////////////////////////////////////

    override fun unlock(stamp: Long) {
        if ((stamp and WRITE_BIT) != 0L) {
            unlockWrite(stamp)
        } else {
            unlockRead(stamp)
        }
    }

    override fun tryConvertToWriteLock(stamp: Long): Long {
        val mode = stamp and LOCK_BITS

        while (true) {
            val s = lockState.load()

            if ((s and STAMP_BITS) != (stamp and STAMP_BITS)) {
                return 0L
            }

            val held = s and LOCK_BITS

            if (held == WRITE_BIT) {
                // Already the write lock, if the stamp is its own
                return if (mode == WRITE_BIT) stamp else 0L
            }

            val next = when (held) {
                // An optimistic stamp takes a free lock
                0L if mode == 0L -> s or WRITE_BIT
                // The only reader turns its hold into the write lock
                READ_UNIT if mode != 0L -> s - READ_UNIT + WRITE_BIT
                else -> return 0L
            }

            if (lockState.compareAndSet(s, next)) {
                return writeStamp()
            }
        }
    }

    override fun tryConvertToReadLock(stamp: Long): Long {
        val mode = stamp and LOCK_BITS

        while (true) {
            val s = lockState.load()

            if ((s and STAMP_BITS) != (stamp and STAMP_BITS)) {
                return 0L
            }

            if (mode >= WRITE_BIT) {
                if (s != stamp) {
                    return 0L
                }

                // The writer becomes the first reader, and lets the waiting readers in
                val next = afterWrite(s) + READ_UNIT

                lockState.store(next)
                signalNext()

                return next
            }

            if (mode != 0L) {
                // Already the read lock, if it is held
                return if ((s and LOCK_BITS) == 0L) 0L else stamp
            }

            val next = tryAddReader(s)

            if (next != 0L) {
                return next
            }
        }
    }

    override fun tryConvertToOptimisticRead(stamp: Long): Long {
        val mode = stamp and LOCK_BITS

        loadFence()

        while (true) {
            val s = lockState.load()

            if ((s and STAMP_BITS) != (stamp and STAMP_BITS)) {
                return 0L
            }

            if (mode >= WRITE_BIT) {
                return if (s == stamp) releaseWrite(s) else 0L
            }

            if (mode == 0L) {
                return stamp
            }

            if ((s and LOCK_BITS) == 0L) {
                return 0L
            }

            val next = tryRemoveReader(s)

            if (next != 0L) {
                return next and STAMP_BITS
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // State
    ///////////////////////////////////////////////////////////////////////////

    override fun isWriteLocked(): Boolean = (lockState.load() and WRITE_BIT) != 0L

    override fun isReadLocked(): Boolean = (lockState.load() and READ_BITS) != 0L

    override fun getReadLockCount(): Int {
        val readers = lockState.load() and READ_BITS

        // All the reader bits are only set while a thread counts apart from the state
        val counted = if (readers >= READ_FULL) READ_FULL + readerOverflow.load() else readers

        return counted.toInt()
    }

    ///////////////////////////////////////////////////////////////////////////
    // Readers beyond what the state counts
    ///////////////////////////////////////////////////////////////////////////

    // Tries once to count a reader apart from the state [s], which is full. Setting all the
    // reader bits keeps the other threads out of that count meanwhile. Returns the state, or
    // zero when another thread was counting or the state changed first.
    private fun tryAddOverflowReader(s: Long): Long {
        if ((s and LOCK_BITS) != READ_FULL || !lockState.compareAndSet(s, s or READ_BITS)) {
            return 0L
        }

        readerOverflow.store(readerOverflow.load() + 1)
        lockState.store(s)

        return s
    }

    // Tries once to take a reader out of the count apart from the state [s], or out of the
    // state once that count is empty. Returns the new state, or zero as above.
    private fun tryRemoveOverflowReader(s: Long): Long {
        if ((s and LOCK_BITS) != READ_FULL || !lockState.compareAndSet(s, s or READ_BITS)) {
            return 0L
        }

        val overflow = readerOverflow.load()
        var next = s

        if (overflow > 0) {
            readerOverflow.store(overflow - 1)
        } else {
            next = s - READ_UNIT
        }

        lockState.store(next)

        return next
    }
}

// The state once the write lock of the state [s] is released. Adding the write bit clears it
// and counts the write. The count starts again rather than reach zero.
private fun afterWrite(s: Long): Long {
    val next = s + WRITE_BIT

    return if (next == 0L) ORIGIN else next
}
