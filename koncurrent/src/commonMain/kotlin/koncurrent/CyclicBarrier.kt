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

import koncurrent.internal.awaitWithin
import kotlin.time.Duration

/**
 * A point where a set number of threads wait for each other. Each thread calls [await] when it
 * arrives. Once the last one has, they all go on, and the barrier serves again.
 *
 * It suits threads that work by rounds and meet between two rounds. A barrier can run an
 * action each time its threads have all arrived, before any of them goes on.
 *
 * The threads go on together or not at all. When one of them leaves the barrier early, on an
 * interrupt, on a time limit or because the action failed, the barrier is broken. Every thread
 * that waits at it, and every thread that arrives later, gets a [BrokenBarrierException], until
 * [reset] is called.
 *
 * ### Platforms
 *
 * - On the JVM and Android this is `java.util.concurrent.CyclicBarrier`.
 * - On native this is a port of the `CyclicBarrier` of JSR-166.
 * - JS and Wasm have one thread, which only passes a barrier of one party. Waiting for another
 *   party could never end, so [await] throws [IllegalStateException] there.
 *
 * @param parties the number of threads that must call [await] for the barrier to open
 * @throws IllegalArgumentException if [parties] is less than one
 */
public expect class CyclicBarrier(parties: Int) {

    /**
     * Creates a barrier that runs [barrierAction] each time its [parties] have all arrived.
     * The last thread to arrive runs it, before the others go on.
     *
     * @throws IllegalArgumentException if [parties] is less than one
     */
    public constructor(parties: Int, barrierAction: Runnable?)

    /**
     * Returns the number of threads that the barrier waits for.
     */
    public fun getParties(): Int

    /**
     * Waits until all the parties have called `await` on this barrier.
     *
     * The last thread to arrive does not wait. It runs the action of the barrier, if any, then
     * lets the others go on. An exception of the action breaks the barrier, and is thrown to
     * that thread.
     *
     * @return the arrival index of the calling thread, from the number of parties minus one
     * for the first one to zero for the last one
     * @throws InterruptedException if the thread is interrupted before or while it waits
     * @throws BrokenBarrierException if the barrier is broken, or breaks or is reset while the
     * thread waits
     */
    @IgnorableReturnValue
    public fun await(): Int

    /**
     * Tells whether the barrier is broken. It is when a thread left it on an interrupt or on a
     * time limit, or when its action failed, since it was created or [reset].
     */
    public fun isBroken(): Boolean

    /**
     * Puts the barrier back in its first state. The threads that wait at it get a
     * [BrokenBarrierException].
     */
    public fun reset()

    /**
     * Returns the number of threads that wait at the barrier.
     */
    public fun getNumberWaiting(): Int
}

/**
 * Waits as [CyclicBarrier.await] does, for [timeout] at most. A thread whose time runs out
 * breaks the barrier.
 *
 * On JS and Wasm the time of a barrier of several parties runs out at once, since no other
 * thread could arrive.
 *
 * @return the arrival index of the calling thread
 * @throws InterruptedException if the thread is interrupted before or while it waits
 * @throws BrokenBarrierException if the barrier is broken, or breaks or is reset while the
 * thread waits
 * @throws TimeoutException if the time runs out before all the parties have arrived
 */
@IgnorableReturnValue
public fun CyclicBarrier.await(timeout: Duration): Int = awaitWithin(timeout.inWholeNanoseconds)
