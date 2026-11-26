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
 * Ported from the AbstractQueuedSynchronizer of JSR-166, which was written by Doug Lea with
 * assistance from members of JCP JSR-166 Expert Group and released to the public domain, as
 * explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

// Kotlin/Native gives each function its own frame of GC roots, even where LLVM inlines it. The
// small functions of the hot paths are inlined by Kotlin, which merges their frames.
@file:Suppress("NOTHING_TO_INLINE")

package koncurrent.internal

import koncurrent.InterruptedException
import koncurrent.Thread
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference

/**
 * Set in the status of a node whose thread waits to be woken up.
 */
internal const val WAITING = 1

/**
 * Set in the status of a node that waits for a condition.
 */
internal const val COND = 2

// The most retries a woken thread makes before it waits again
internal const val MAX_SPINS = 0xFF

// The tries that a thread makes before it joins the queue. They take about as long as going
// to sleep and being woken up. Measured on macOS, 16 tries suit short critical sections best,
// and longer ones nearly as well as 64 tries.
internal const val SPIN_ROUNDS = 16

// The first tries are made at once. The next ones give way to the other threads first.
internal const val BUSY_ROUNDS = 2

/**
 * The queue of waiting threads that the blocking locks are built upon. It is the
 * `AbstractQueuedSynchronizer` of JSR-166.
 *
 * A subclass gives a meaning to the [state]. It acquires with [acquire] or [acquireQueued], to
 * which it gives the function that tries to change the state. The original calls an abstract
 * function instead, and throws for the mode that a subclass does not support. Here a mode that
 * a lock does not have cannot be called.
 *
 * The queue is a variant of a CLH lock queue. A thread enqueues itself by a single atomic update
 * of the tail. The first thread of the queue may acquire, and so may a thread that is not
 * enqueued yet. The head is a dummy node, or the node of the last thread that acquired from the
 * queue.
 *
 * Three things differ from the original. A thread may first try a few times before it joins the
 * queue, because going to sleep and being woken up costs far more than a short wait. The head
 * is a plain volatile field, set after the tail when the queue is created. And a thread that
 * gives up, on a timeout or an interrupt, leaves the queue in another way.
 *
 * The original unlinks the node of such a thread with atomic updates of the links, which the
 * fields of a Kotlin class do not have. Here the thread marks its node as [cancelled][cancel]
 * and wakes up the thread after it. The node stays linked. Two rules make that safe:
 *
 * - A thread never waits behind a cancelled node. It reads the mark of its predecessor before it
 *   waits, and [skips][skip] a cancelled one. The thread that cancels reads the link to its
 *   successor after it wrote its mark. So either it finds its successor and wakes it up, or its
 *   successor finds the mark. A thread that a signal [moves][enqueue] to the queue sleeps, so
 *   the thread that signals reads the mark for it.
 * - A thread that cancels may have been woken up to acquire. Waking up the thread after it
 *   passes that on.
 *
 * Only the thread of a node writes its link to its predecessor. The link from a predecessor to
 * its node has two writers when a signal moves a condition node: the thread that signals, for
 * the sleeping thread of that node, and the thread behind when it skips the node. The first
 * write may come last and leave the predecessor linked to a node that gave up, which no
 * release would wake a thread from. So the thread that signals reads the mark of the node once
 * it wrote the link, and [links][link] the thread behind in its place. A release does nothing
 * for it: one more read there was measured to cost a contended lock 5%.
 */
internal open class Synchronizer {

    // Not private, as what follows, for the inline functions
    val state = AtomicInt(0)

    // Null until threads contend
    @Volatile
    var head: WaitNode? = null

    val tail = AtomicReference<WaitNode?>(null)

    /**
     * Acquires, waiting for as long as [tryAcquire] fails. It first tries a few times without
     * joining the queue, which suits what is held for a short time.
     *
     * [shared] tells that several threads may acquire at once. The next thread of the queue is
     * then woken up too when it also waits to share.
     *
     * Kotlin/Native clears the frame of a function on entry, for the references of all its
     * code, and this function brings many. So a caller whose calls mostly acquire at their
     * first try keeps this call in a function of its own. Only a call that waits then clears
     * those references.
     *
     * @throws InterruptedException if [interruptible] and the thread is interrupted while it
     * waits
     */
    inline fun acquire(shared: Boolean, interruptible: Boolean, tryAcquire: () -> Boolean) {
        for (round in 0..<SPIN_ROUNDS) {
            if (tryAcquire()) {
                return
            }

            if (round >= BUSY_ROUNDS) {
                yieldThread()
            }
        }

        val _ = acquireQueued(null, shared, interruptible, timed = false, 0L, tryAcquire)
    }

    /**
     * Acquires, waiting in the queue for as long as [tryAcquire] fails. [queued] is the node of
     * the calling thread when a condition already put it in the queue, else `null`.
     *
     * The thread gives up once the [nanoTime] reaches [deadline] when [timed], and when it is
     * interrupted if [interruptible]. An interrupt that does not make it give up is kept for
     * the caller, by interrupting the thread again at the end.
     *
     * [tryAcquire] may throw. The thread then leaves the queue and lets the next one try.
     *
     * Repeatedly:
     * - Checks whether the node is now first. If so, waits for the head to be stable. If not,
     *   skips its predecessor when that one is cancelled.
     * - If the node is first or not enqueued yet, tries to acquire.
     * - Else if the node is not created yet, creates it.
     * - Else if it is not enqueued yet, tries once to enqueue it.
     * - Else if woken from a wait, retries a few times.
     * - Else if the waiting status is not set, sets it and retries.
     * - Else waits, then clears the waiting status, and gives up if it has to.
     *
     * As for [acquire], a caller keeps this call apart from its first try.
     *
     * @return whether it acquired. `false` tells that the time ran out.
     * @throws InterruptedException if [interruptible] and the thread is interrupted while it
     * waits
     */
    inline fun acquireQueued(
        queued: WaitNode?,
        shared: Boolean,
        interruptible: Boolean,
        timed: Boolean,
        deadline: Long,
        tryAcquire: () -> Boolean,
    ): Boolean {
        val current = callingThread()
        var node = queued
        var pred: WaitNode? = null
        var first = false
        var interrupted = false
        var spins = 0
        var postSpins = 0

        while (true) {
            if (!first) {
                pred = node?.prev

                if (pred != null) {
                    first = head === pred

                    if (!first && pred.cancelled) {
                        skip(checkNotNull(node), pred)
                        continue
                    }

                    if (!first && pred.prev == null) {
                        // The predecessor acquired and is not the head yet
                        continue
                    }
                }
            }

            if (first || pred == null) {
                val acquired = try {
                    tryAcquire()
                } catch (failure: Throwable) {
                    if (first) {
                        leave(checkNotNull(node), checkNotNull(pred))
                    }

                    if (interrupted) {
                        current.interrupt()
                    }

                    throw failure
                }

                if (acquired) {
                    if (first) {
                        dequeue(checkNotNull(node), checkNotNull(pred), shared)
                    }

                    if (interrupted) {
                        current.interrupt()
                    }

                    return true
                }
            }

            if (node == null) {
                node = if (shared) SharedNode() else ExclusiveNode()
            } else if (pred == null) {
                node.waiter = current
                tryEnqueue(node)
            } else if (first && spins != 0) {
                // Reduces the unfairness of a thread that is woken up and finds the lock taken
                spins--
            } else if (node.status == 0) {
                // Lets a release wake this thread up, then checks once more before waiting
                node.status = WAITING
            } else {
                postSpins = ((postSpins shl 1) or 1) and MAX_SPINS
                spins = postSpins

                if (!timed) {
                    park()
                } else {
                    val nanos = deadline - nanoTime()

                    if (nanos <= 0L) {
                        break
                    }

                    parkNanos(nanos)
                }

                node.status = 0

                // Also clears the interrupt, which would end every wait at once on the JVM
                if (clearInterrupt()) {
                    interrupted = true

                    if (interruptible) {
                        break
                    }
                }
            }
        }

        cancel(node)

        if (interrupted) {
            if (interruptible) {
                throw InterruptedException()
            }

            current.interrupt()
        }

        return false
    }

    /**
     * Wakes up the first thread of the queue, if it waits. To call once the state is released.
     */
    inline fun signalNext() {
        val head = head

        if (head != null) {
            signalNext(head)
        }
    }

    /**
     * Tells whether the first thread of the queue seems to wait to acquire alone. A lock that
     * is shared uses it to let such a thread pass before the threads that would share.
     *
     * [head] is the head of the queue, which the caller read. A volatile read is what costs
     * here, so the caller does not make it twice.
     */
    inline fun apparentlyFirstQueuedIsExclusive(head: WaitNode): Boolean {
        val next = head.next ?: return false

        return next !is SharedNode && next.waiter != null
    }

    /**
     * Tells whether another thread than the calling one has waited longer to acquire. What is
     * fair asks before it takes what is free.
     */
    fun hasQueuedPredecessors(): Boolean {
        val first = firstQueuedThread()

        return first != null && first !== callingThread()
    }

    // Returns the thread that has waited for the longest, or null when no thread waits
    private fun firstQueuedThread(): Thread? {
        val next = head?.next

        if (next != null) {
            val waiter = next.waiter

            // The link is read last, as in the original. A node that leaves the queue clears
            // it first, so its thread still waited when it was read.
            if (waiter != null && next.prev != null) {
                return waiter
            }
        }

        // Else a node is being linked, or the first thread gave up, or the queue has no head
        // yet. Goes from the tail back to the head, and keeps the last thread that it meets.
        var first: Thread? = null
        var node = tail.load()

        while (node != null) {
            val prev = node.prev ?: break

            node.waiter?.let { first = it }
            node = prev
        }

        return first
    }

    /**
     * Tries once to append [node] to the queue. Its predecessor stays `null` upon failure.
     */
    fun tryEnqueue(node: WaitNode) {
        val last = tail.load()

        node.prev = last

        if (last == null) {
            createQueue()
        } else if (!tail.compareAndSet(last, node)) {
            node.prev = null
        } else {
            last.next = node
        }
    }

    /**
     * Appends to the queue the [node] of a thread that waited for a condition. Its thread sees
     * a predecessor before the node is enqueued, which [canReacquire] accounts for.
     */
    fun enqueue(node: ConditionNode) {
        while (true) {
            val last = tail.load()

            node.prev = last

            if (last == null) {
                createQueue()
            } else if (tail.compareAndSet(last, node)) {
                link(last, node)
                return
            }
        }
    }

    /**
     * Links [node], which a signal appended behind [last].
     *
     * The thread of the node sleeps, and did not look at the node that it is put behind. When
     * that one gave up, the thread is woken up to skip it.
     *
     * The thread of the node may also have given up before this link is written, and the
     * thread behind may have skipped the node already. The link then puts the node back in
     * front of that thread, where no release would find it. So the mark of the node is read
     * once the link is written, and the thread behind is linked in its place.
     *
     * That thread writes the same link when it skips, and so does the one behind it if it
     * gives up too. Whichever of these writes comes last is right. A thread that has not
     * skipped up to [last] yet will write after this. And a thread that this links is looked
     * at again afterwards: if it gave up meanwhile, the thread that skips it may have written
     * first.
     */
    fun link(last: WaitNode, node: ConditionNode) {
        last.next = node

        if (last.cancelled) {
            node.waiter?.let(::unpark)
        }

        var linked: WaitNode = node

        while (linked.cancelled) {
            var next = linked.next

            while (next != null && next.cancelled) {
                next = next.next
            }

            if (next == null || next.prev !== last) {
                return
            }

            last.next = next
            linked = next
        }
    }

    /**
     * Tells whether [node] is in the queue, so that its thread can acquire again. It checks the
     * links of the node, since its status changes before it is enqueued.
     */
    fun canReacquire(node: ConditionNode): Boolean {
        val pred = node.prev ?: return false

        return pred.next === node || isEnqueued(node)
    }

    private fun isEnqueued(node: WaitNode): Boolean {
        var last = tail.load()

        while (last != null) {
            if (last === node) {
                return true
            }

            last = last.prev
        }

        return false
    }

    /**
     * Gives the queue its dummy node. The head is set after the tail. A thread that enqueues
     * itself in between finds that its predecessor is not the head yet, and tries again.
     */
    fun createQueue() {
        val dummy = ExclusiveNode()

        if (tail.compareAndSet(null, dummy)) {
            head = dummy
        }
    }

    /**
     * Makes [node] the head, once its thread acquired. [pred] is the head that it replaces.
     * Other threads tell that a node left the queue from its `null` predecessor, so that is
     * cleared first. A thread that shares what it acquired wakes up the next one that would.
     */
    fun dequeue(node: WaitNode, pred: WaitNode, shared: Boolean) {
        node.prev = null
        head = node
        pred.next = null
        node.waiter = null

        if (shared) {
            val next = node.next

            if (next is SharedNode) {
                signal(next)
            }
        }
    }

    /**
     * Takes the first [node] of the queue out of it though its thread did not acquire, and
     * lets the next thread try. [pred] is the head.
     */
    fun leave(node: WaitNode, pred: WaitNode) {
        dequeue(node, pred, shared = false)
        signalNext(node)
    }

    /**
     * Marks the [node] of a thread that gives up, and wakes up the first thread after it that
     * did not give up too. That thread skips the node, and acquires in its place if it can.
     */
    fun cancel(node: WaitNode) {
        node.waiter = null
        node.cancelled = true

        var next = node.next

        while (next != null && next.cancelled) {
            next = next.next
        }

        next?.waiter?.let(::unpark)
    }

    /**
     * Puts [node] right after the predecessor of [pred], which is cancelled. The thread of
     * [node] calls this. A cancelled node keeps its predecessor, since it never becomes the
     * head.
     */
    fun skip(node: WaitNode, pred: WaitNode) {
        val before = checkNotNull(pred.prev)

        node.prev = before
        before.next = node
    }

    /**
     * Wakes up the thread after [head], if it waits.
     */
    fun signalNext(head: WaitNode) {
        val next = head.next ?: return

        signal(next)
    }

    // Clearing the status first makes the thread check again, should it be about to wait
    private fun signal(node: WaitNode) {
        if (node.status != 0) {
            node.status = 0
            node.waiter?.let(::unpark)
        }
    }
}

/**
 * The place of a thread in the queue of a [Synchronizer].
 */
internal sealed class WaitNode {

    // Attached by the update of the tail. Null again once the node is the head.
    @Volatile
    var prev: WaitNode? = null

    // Not null when the next node can be woken up
    @Volatile
    var next: WaitNode? = null

    // Written by its thread, cleared by the thread that wakes it up
    @Volatile
    var status = 0

    // Set by its thread when it gives up. It is a field of its own, since a thread that wakes
    // this one up may write the status at the same time.
    @Volatile
    var cancelled = false

    // Not null while a thread waits on this. Its accesses are between accesses of the other
    // fields.
    var waiter: Thread? = null
}

/**
 * The node of a thread that waits to acquire alone.
 */
internal class ExclusiveNode : WaitNode()

/**
 * The node of a thread that waits to acquire with others.
 */
internal class SharedNode : WaitNode()

/**
 * The node of a thread that waits for a condition, then to acquire again.
 */
internal class ConditionNode : WaitNode() {

    // The next node that waits for the same condition
    var nextWaiter: ConditionNode? = null
}
