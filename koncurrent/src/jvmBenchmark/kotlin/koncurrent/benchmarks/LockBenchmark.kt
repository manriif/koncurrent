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
@file:Suppress("RedundantVisibilityModifier", "unused")

package koncurrent.benchmarks

import koncurrent.internal.PortableReentrantLock
import koncurrent.internal.PortableReentrantReadWriteLock
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Threads
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * The cost of the locks of the library against other locks. An invocation takes one lock, adds
 * one to a counter and releases the lock. Each subclass runs it on a number of threads, which
 * all take the same lock.
 */
public abstract class LockBenchmark {

    /**
     * The port that backs the lock of the library on native. On the JVM that lock is the
     * `ReentrantLock` of the JDK.
     */
    @Benchmark
    public fun portableReentrantLock(state: LockState): Long {
        state.portableReentrantLock.lock()

        try {
            return ++state.counter
        } finally {
            state.portableReentrantLock.unlock()
        }
    }

    /**
     * The lock of the bins of the map port.
     */
    @Benchmark
    public fun arraySpinLock(state: LockState): Long {
        state.arraySpinLock.lock(LOCK_INDEX)

        try {
            return ++state.counter
        } finally {
            state.arraySpinLock.unlock(LOCK_INDEX)
        }
    }

    @Benchmark
    public fun nodeSpinLock(state: LockState): Long {
        state.nodeSpinLock.lock()

        try {
            return ++state.counter
        } finally {
            state.nodeSpinLock.unlock()
        }
    }

    /**
     * The lock of the bins of the original map.
     */
    @Benchmark
    public fun synchronizedBlock(state: LockState): Long = synchronized(state.monitor) {
        ++state.counter
    }

    @Benchmark
    public fun reentrantLock(state: LockState): Long {
        state.reentrantLock.lock()

        try {
            return ++state.counter
        } finally {
            state.reentrantLock.unlock()
        }
    }

    /**
     * The port that backs the read-write lock of the library on native, when writing.
     */
    @Benchmark
    public fun portableWriteLock(state: LockState): Long {
        state.portableReadWriteLock.lockWrite()

        try {
            return ++state.counter
        } finally {
            state.portableReadWriteLock.unlockWrite()
        }
    }

    /**
     * The same port when reading. The threads hold the lock together, so they only read.
     */
    @Benchmark
    public fun portableReadLock(state: LockState): Long {
        state.portableReadWriteLock.lockRead()

        try {
            return state.counter
        } finally {
            state.portableReadWriteLock.unlockRead()
        }
    }

    @Benchmark
    public fun writeLock(state: LockState): Long {
        val lock = state.readWriteLock.writeLock()

        lock.lock()

        try {
            return ++state.counter
        } finally {
            lock.unlock()
        }
    }

    @Benchmark
    public fun readLock(state: LockState): Long {
        val lock = state.readWriteLock.readLock()

        lock.lock()

        try {
            return state.counter
        } finally {
            lock.unlock()
        }
    }

    private companion object {

        const val LOCK_INDEX = 7
    }
}

@Threads(1)
public open class Lock1ThreadBenchmark : LockBenchmark()

@Threads(2)
public open class Lock2ThreadsBenchmark : LockBenchmark()

@Threads(4)
public open class Lock4ThreadsBenchmark : LockBenchmark()

@Threads(8)
public open class Lock8ThreadsBenchmark : LockBenchmark()

@State(Scope.Benchmark)
public open class LockState {

    public val arraySpinLock: ArraySpinLock = ArraySpinLock(16)
    public val nodeSpinLock: NodeSpinLock = NodeSpinLock()
    public val monitor: Any = Any()
    public val reentrantLock: ReentrantLock = ReentrantLock()
    public val readWriteLock: ReentrantReadWriteLock = ReentrantReadWriteLock()
    internal val portableReentrantLock = PortableReentrantLock()
    internal val portableReadWriteLock = PortableReentrantReadWriteLock()

    public var counter: Long = 0L
}
