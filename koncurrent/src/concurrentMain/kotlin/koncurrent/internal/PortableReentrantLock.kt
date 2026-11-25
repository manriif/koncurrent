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
 * Ported from the ReentrantLock of JSR-166, which was written by Doug Lea with assistance from
 * members of JCP JSR-166 Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

// Kotlin/Native gives each function its own frame of GC roots, even where LLVM inlines it. The
// small functions of the hot paths are inlined by Kotlin, which merges their frames.
@file:Suppress("NOTHING_TO_INLINE")

package koncurrent.internal

import koncurrent.InterruptedException
import koncurrent.locks.IllegalMonitorStateException

internal const val LOCK_FREE = 0
internal const val LOCK_HELD = 1

/**
 * The `ReentrantLock` of JSR-166 in common Kotlin. It backs the public lock where the platform
 * has none.
 *
 * The state is [LOCK_HELD] while a thread owns the lock. A thread that asks for a free lock
 * takes it, even when other threads wait in the queue, unless the lock is [fair].
 *
 * A fair lock goes to the thread that has waited for the longest. A thread joins the queue as
 * soon as it finds the lock taken or the queue in use, where it first tries a few times for a
 * lock that is not fair. Those tries would make it lose its turn.
 *
 * A fair lock is told by its class, [FairPortableReentrantLock], and not by a field. A field
 * would sit next to the owner and its holds, which every thread that takes the lock writes. A
 * thread that reads it before it updates the state then waits for the memory that the last
 * owner wrote. The class is read from the header of the object, which no thread writes, and
 * the lock keeps the size that it had.
 *
 * The original counts the holds of the owner in the state. Only the owner reads and writes that
 * count, so it is a plain field here. This spares two atomic accesses for each hold.
 *
 * The functions that take and release the lock are inline, for the class that wraps this one.
 * `unlock` is small enough for LLVM to inline it into its callers in turn, which it stops doing
 * for a few instructions more.
 */
internal open class PortableReentrantLock : Synchronizer() {

    // Not volatile. A thread only needs to tell whether it is the owner, and it always reads
    // its own write.
    var owner = NO_THREAD

    // Only accessed by the owner
    var holds = 0

    val fair: Boolean
        get() = this is FairPortableReentrantLock

    inline fun lock() {
        val current = currentThreadId()

        if (!fair && state.compareAndSet(LOCK_FREE, LOCK_HELD)) {
            owner = current
            holds = 1
        } else {
            lockContended(current)
        }
    }

    inline fun tryLock(): Boolean {
        val current = currentThreadId()

        if (state.compareAndSet(LOCK_FREE, LOCK_HELD)) {
            owner = current
            holds = 1
            return true
        }

        return owner == current && hold()
    }

    inline fun unlock() {
        if (owner != currentThreadId()) {
            throw IllegalMonitorStateException(LOCK_NOT_HELD)
        }

        if (--holds == 0) {
            owner = NO_THREAD
            state.store(LOCK_FREE)
            signalNext()
        }
    }

    fun lockContended(current: Int) {
        if (owner == current) {
            val _ = hold()
        } else if (!fair) {
            acquire(shared = false, interruptible = false) { tryAcquire(current) }
        } else if (!tryAcquireInTurn(current)) {
            lockInTurn(current)
        }
    }

    // Apart from lockContended on purpose. Synchronizer.acquire tells why.
    private fun lockInTurn(current: Int) {
        val _ = acquireQueued(null, shared = false, interruptible = false, timed = false, 0L) {
            tryAcquireInTurn(current)
        }
    }

    /**
     * Acquires the lock as [lock] does, unless the thread is interrupted before or while it
     * waits.
     */
    fun lockInterruptibly() {
        if (clearInterrupt()) {
            throw InterruptedException()
        }

        if (!tryLockInTurn()) {
            val _ = acquireOrGiveUp(timed = false, deadline = 0L)
        }
    }

    /**
     * Acquires the lock if it is free within [nanos] nanoseconds, unless the thread is
     * interrupted before or while it waits.
     */
    fun tryLock(nanos: Long): Boolean {
        if (clearInterrupt()) {
            throw InterruptedException()
        }

        return tryLockInTurn() ||
            (nanos > 0L && acquireOrGiveUp(timed = true, deadlineAfter(nanos)))
    }

    // The first try of a thread that may then wait. It does not take a fair lock past the
    // threads that wait for it, which `tryLock` does.
    private fun tryLockInTurn(): Boolean {
        if (!fair) {
            return tryLock()
        }

        val current = currentThreadId()

        return if (owner == current) hold() else tryAcquireInTurn(current)
    }

    private fun acquireOrGiveUp(timed: Boolean, deadline: Long): Boolean {
        val current = currentThreadId()

        return acquireQueued(null, shared = false, interruptible = true, timed, deadline) {
            tryAcquireInTurn(current)
        }
    }

    // Returns `true`, for the lock tries
    fun hold(): Boolean {
        if (holds == Int.MAX_VALUE) {
            throw Error("Maximum lock count exceeded")
        }

        holds++
        return true
    }

    inline fun tryAcquire(current: Int): Boolean {
        if (state.load() == LOCK_FREE && state.compareAndSet(LOCK_FREE, LOCK_HELD)) {
            owner = current
            holds = 1
            return true
        }

        return false
    }

    // Takes the lock if it is free, unless the lock is fair and another thread has waited for
    // it longer. The queue is read once the lock is seen free, as in the original. A thread
    // that joined the queue while the lock was held is then never missed.
    private fun tryAcquireInTurn(current: Int): Boolean {
        if (fair && (state.load() != LOCK_FREE || hasQueuedPredecessors())) {
            return false
        }

        return tryAcquire(current)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Conditions
    ///////////////////////////////////////////////////////////////////////////

    fun newCondition(): PortableCondition = PortableCondition(this)

    /**
     * Releases every hold of the owner, for the time of a wait. Returns their number.
     */
    fun releaseHolds(): Int {
        val released = holds

        holds = 0
        owner = NO_THREAD
        state.store(LOCK_FREE)
        signalNext()

        return released
    }

    /**
     * Acquires again after a wait, and takes back the [released] holds. [node] is the node
     * that a signal put in the queue, or `null` when the thread gave the wait up.
     */
    fun reacquire(node: ConditionNode?, current: Int, released: Int) {
        val _ = acquireQueued(
            queued = node,
            shared = false,
            interruptible = false,
            timed = false,
            deadline = 0L,
            tryAcquire = { tryAcquireInTurn(current) },
        )

        holds = released
    }
}

/**
 * A [PortableReentrantLock] that is fair.
 */
internal class FairPortableReentrantLock : PortableReentrantLock()

/**
 * A condition of a [PortableReentrantLock], which is the `ConditionObject` of JSR-166. A thread
 * that holds the lock waits on it for another thread to signal it.
 *
 * The waiting threads are kept in a list of their own. A signal moves the first one to the
 * queue of the lock, where it waits to acquire the lock again.
 *
 * A thread may give a wait up, on a timeout or an interrupt, while a signal moves its node. The
 * original settles which one came first with an atomic update of the status of the node. Here
 * the thread cancels its node, takes the lock again from a new node, and reads the status once
 * it holds the lock. Signals are given under that lock, so the status then tells whether one
 * moved the node. A signal that finds a cancelled node first goes to the next one.
 */
internal class PortableCondition(private val lock: PortableReentrantLock) {

    // Only accessed by the thread that holds the lock
    private var firstWaiter: ConditionNode? = null
    private var lastWaiter: ConditionNode? = null

    /**
     * Releases the lock and waits for a signal, then acquires the lock again with the holds
     * that the calling thread had. It may return without a signal.
     *
     * @throws InterruptedException if the thread is interrupted before a signal
     */
    fun await() {
        val _ = await(timed = false, nanos = 0L)
    }

    /**
     * Waits as [await] does, for [nanos] nanoseconds at most.
     *
     * @return `false` if the time ran out before a signal
     * @throws InterruptedException if the thread is interrupted before a signal
     */
    fun await(nanos: Long): Boolean = await(timed = true, nanos)

    private fun await(timed: Boolean, nanos: Long): Boolean {
        if (clearInterrupt()) {
            throw InterruptedException()
        }

        val current = currentThreadId()
        checkHeld(current)

        val node = addWaiter()
        val deadline = if (timed) deadlineAfter(nanos) else 0L
        val released = lock.releaseHolds()
        var interrupted = false
        var givenUp = false

        while (!lock.canReacquire(node)) {
            if ((node.status and COND) == 0) {
                // Signalled, and about to be in the queue of the lock
                yieldThread()
            } else if (clearInterrupt()) {
                interrupted = true
                givenUp = true
                break
            } else if (!timed) {
                park()
            } else {
                val left = deadline - nanoTime()

                if (left <= 0L) {
                    givenUp = true
                    break
                }

                parkNanos(left)
            }
        }

        if (!givenUp) {
            node.status = 0
            lock.reacquire(node, current, released)

            return true
        }

        // A signal may be moving the node right now. The other threads skip it from here on.
        lock.cancel(node)
        lock.reacquire(null, current, released)

        if ((node.status and COND) != 0) {
            unlink(node)

            if (interrupted) {
                throw InterruptedException()
            }

            return false
        }

        // A signal came first, so this is a normal return
        if (interrupted) {
            callingThread().interrupt()
        }

        return true
    }

    fun signal() {
        checkHeld(currentThreadId())

        while (true) {
            val first = removeFirst() ?: return

            if (!first.cancelled) {
                transfer(first)
                return
            }
        }
    }

    fun signalAll() {
        checkHeld(currentThreadId())

        while (true) {
            val first = removeFirst() ?: return

            if (!first.cancelled) {
                transfer(first)
            }
        }
    }

    private fun addWaiter(): ConditionNode {
        val node = ConditionNode()
        val last = lastWaiter

        node.waiter = callingThread()
        node.status = COND or WAITING

        if (last == null) {
            firstWaiter = node
        } else {
            last.nextWaiter = node
        }

        lastWaiter = node

        return node
    }

    private fun removeFirst(): ConditionNode? {
        val first = firstWaiter ?: return null
        val next = first.nextWaiter

        firstWaiter = next

        if (next == null) {
            lastWaiter = null
        }

        return first
    }

    /**
     * Takes out of the list the [node] of a thread that gave its wait up, if a signal did not
     * drop it already.
     */
    private fun unlink(node: ConditionNode) {
        var previous: ConditionNode? = null
        var waiter = firstWaiter

        while (waiter != null && waiter !== node) {
            previous = waiter
            waiter = waiter.nextWaiter
        }

        if (waiter == null) {
            return
        }

        val next = waiter.nextWaiter

        if (previous == null) {
            firstWaiter = next
        } else {
            previous.nextWaiter = next
        }

        if (next == null) {
            lastWaiter = previous
        }
    }

    /**
     * Moves [node] to the queue of the lock. A release wakes its thread up there.
     */
    private fun transfer(node: ConditionNode) {
        node.status = WAITING
        lock.enqueue(node)
    }

    private fun checkHeld(current: Int) {
        if (lock.owner != current) {
            throw IllegalMonitorStateException(LOCK_NOT_HELD)
        }
    }
}
