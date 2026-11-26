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

import koncurrent.InterruptedException

/**
 * A mutual exclusion lock that the thread holding it can take again.
 *
 * Only one thread holds the lock at a time. That thread can call [lock] again without waiting.
 * It must then call [unlock] once for each [lock]. Prefer [withLock], which never forgets to
 * unlock.
 *
 * The lock is not fair unless it is created so. A thread that asks for a free lock then takes
 * it, even when other threads wait for it. A fair lock goes to the thread that has waited for
 * the longest. No thread waits forever for it, and it is much slower when many threads take
 * it, since every release wakes a thread up.
 *
 * The lock holds no native resource. It has nothing to close, and it is freed as any object.
 *
 * ### Platforms
 *
 * - On the JVM and Android this is `java.util.concurrent.locks.ReentrantLock`.
 * - On native this is a port of the `ReentrantLock` of JSR-166. A thread that waits sleeps
 *   until the lock is released.
 * - JS and Wasm have one thread, which never waits. The lock only counts its holds there.
 */
public expect class ReentrantLock() : Lock {

    /**
     * Creates a lock that is [fair] or not.
     */
    public constructor(fair: Boolean)

    /**
     * Acquires the lock, and waits while another thread holds it. An interrupt does not end
     * the wait.
     *
     * Returns at once if the current thread already holds the lock, with one more hold.
     */
    override fun lock()

    /**
     * Acquires the lock as [lock] does, unless the thread is interrupted.
     *
     * @throws InterruptedException if the thread is interrupted before or while it waits
     */
    override fun lockInterruptibly()

    /**
     * Acquires the lock if no other thread holds it. Never waits.
     *
     * It takes a free lock even when other threads wait for it, and even when the lock is
     * fair. It succeeds if the current thread already holds the lock, with one more hold.
     *
     * @return whether the lock was acquired
     */
    override fun tryLock(): Boolean

    /**
     * Releases one hold of the lock. The lock is free once the current thread has released
     * all its holds.
     *
     * @throws IllegalMonitorStateException if the current thread does not hold the lock
     */
    override fun unlock()

    /**
     * Returns a new [Condition] bound to this lock. A thread that holds the lock waits on it
     * until another thread, which holds the lock in turn, signals it.
     */
    public fun newCondition(): Condition

    /**
     * Tells whether this lock is fair.
     */
    public fun isFair(): Boolean
}
