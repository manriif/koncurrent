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
@file:Suppress("RedundantVisibilityModifier")

package koncurrent.benchmarks

import co.touchlab.stately.collections.ConcurrentMutableMap
import co.touchlab.stately.concurrency.Lock
import koncurrent.ConcurrentHashMap
import koncurrent.computeIfAbsent
import koncurrent.locks.ReentrantLock
import koncurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.atomics.AtomicLong

private val ThreadCounts = listOf(1, 2, 4, 8)

private val MixedWorkloads = listOf(
    "read90Write10" to READ_90_WRITE_10,
    "read50Write50" to READ_50_WRITE_50,
)

// Takes what the measured operations find, so that the compiler cannot drop them
private val Sink = AtomicLong(0L)

private const val MAPS = "maps"
private const val LOCKS = "locks"

/**
 * Measures the maps and the locks on real threads. kotlinx-benchmark only runs one thread on
 * Kotlin/Native. Each line is a benchmark, an implementation, a number of keys, a number of
 * threads, then the median, the lowest and the highest operations per second of the rounds.
 *
 * [args] may hold `maps` or `locks` to only measure those.
 */
public fun main(args: Array<String>) {
    println("benchmark,implementation,keys,threads,median,lowest,highest")

    if (args.isEmpty() || MAPS in args) {
        measureMaps()
    }

    if (args.isEmpty() || LOCKS in args) {
        measureLocks()
    }
}

///////////////////////////////////////////////////////////////////////////
// Maps
///////////////////////////////////////////////////////////////////////////

private fun measureMaps() {
    for (keyCount in listOf(SMALL_KEY_COUNT, LARGE_KEY_COUNT)) {
        val keys = Keys(keyCount)

        measureMap(
            implementation = "Koncurrent",
            keys = keys,
            create = { ConcurrentHashMap<Key, Key>() },
            get = { map, key -> map[key] },
            put = { map, key -> map.put(key, key) },
            remove = { map, key -> map.remove(key) },
            computeIfAbsent = { map, key -> map.computeIfAbsent(key) { it } },
        )

        measureMap(
            implementation = "Stately",
            keys = keys,
            create = { ConcurrentMutableMap<Key, Key>() },
            get = { map, key -> map[key] },
            put = { map, key -> map.put(key, key) },
            remove = { map, key -> map.remove(key) },
            computeIfAbsent = { map, key -> map.computeIfAbsent(key) { it } },
        )
    }
}

/**
 * Measures the workloads on the maps that [create] returns. The map is used through [get],
 * [put], [remove] and [computeIfAbsent], so each of its calls is a direct one.
 */
private inline fun <M : Any> measureMap(
    implementation: String,
    keys: Keys,
    crossinline create: () -> M,
    crossinline get: (map: M, key: Key) -> Key?,
    crossinline put: (map: M, key: Key) -> Key?,
    crossinline remove: (map: M, key: Key) -> Key?,
    crossinline computeIfAbsent: (map: M, key: Key) -> Key,
) {
    val fill = {
        val map = create()

        for (key in keys.stored) {
            val _ = put(map, key)
        }

        map
    }

    val full = fill()

    report("get", implementation, keys.count, threads = 1) {
        singleThreadedRound(keys.count, prepare = {}) {
            var found = 0

            for (key in keys.probes) {
                if (get(full, key) != null) {
                    found++
                }
            }

            check(found == keys.count)
        }
    }

    report("put", implementation, keys.count, threads = 1) {
        singleThreadedRound(keys.count, prepare = {}) {
            check(get(fill(), keys.probes[0]) != null)
        }
    }

    report("remove", implementation, keys.count, threads = 1) {
        singleThreadedRound(keys.count, prepare = fill) { map ->
            var removed = 0

            for (key in keys.probes) {
                if (remove(map, key) != null) {
                    removed++
                }
            }

            check(removed == keys.count)
        }
    }

    for ((benchmark, writes) in MixedWorkloads) {
        for (threads in ThreadCounts) {
            val map = create()

            // Starts with half of the keys, which is where the mix of puts and removes settles
            for (index in 0..<keys.count step 2) {
                val _ = put(map, keys.stored[index])
            }

            report(benchmark, implementation, keys.count, threads) {
                concurrentRound(threads) { thread, running ->
                    val random = XorShift((thread + 1) * -0x61c88647)
                    var operations = 0L
                    var found = 0L

                    while (running.load()) {
                        repeat(CHUNK) {
                            mixedOperation(
                                random = random.next(),
                                keyCount = keys.count,
                                writes = writes,
                                get = { index ->
                                    if (get(map, keys.probes[index]) != null) {
                                        found++
                                    }
                                },
                                put = { index ->
                                    if (put(map, keys.stored[index]) != null) {
                                        found++
                                    }
                                },
                                remove = { index ->
                                    if (remove(map, keys.probes[index]) != null) {
                                        found++
                                    }
                                },
                            )
                        }

                        operations += CHUNK
                    }

                    val _ = Sink.addAndFetch(found)
                    operations
                }
            }
        }
    }

    // Every key has a value, which is what a cache gets once it is filled
    for (threads in ThreadCounts) {
        report("computeIfAbsent", implementation, keys.count, threads) {
            concurrentRound(threads) { thread, running ->
                val random = XorShift((thread + 1) * -0x61c88647)
                var operations = 0L
                var found = 0L

                while (running.load()) {
                    repeat(CHUNK) {
                        mixedOperation(
                            random = random.next(),
                            keyCount = keys.count,
                            writes = 0,
                            get = { index ->
                                val key = keys.probes[index]

                                if (computeIfAbsent(full, key) !== key) {
                                    found++
                                }
                            },
                            put = {},
                            remove = {},
                        )
                    }

                    operations += CHUNK
                }

                val _ = Sink.addAndFetch(found)
                operations
            }
        }
    }
}

///////////////////////////////////////////////////////////////////////////
// Locks
///////////////////////////////////////////////////////////////////////////

private const val LOCK_INDEX = 7

private class Counter {

    var value = 0L
}

/**
 * Measures the locks of the library and the bin lock of the map against other locks. The
 * threads all take the same lock to add one to a counter, or to read it under a read lock.
 */
private fun measureLocks() {
    val reentrantLock = ReentrantLock()
    val readLock = ReentrantReadWriteLock().readLock()
    val writeLock = ReentrantReadWriteLock().writeLock()
    val arraySpinLock = ArraySpinLock(16)
    val nodeSpinLock = NodeSpinLock()
    val statelyLock = Lock()
    val mutex = PthreadMutex()

    for (threads in ThreadCounts) {
        measureLock(
            benchmark = "reentrantLock",
            threads = threads,
            lock = { reentrantLock.lock() },
            unlock = { reentrantLock.unlock() },
        )

        measureLock(
            benchmark = "writeLock",
            threads = threads,
            lock = { writeLock.lock() },
            unlock = { writeLock.unlock() },
        )

        measureLock(
            benchmark = "readLock",
            threads = threads,
            lock = { readLock.lock() },
            unlock = { readLock.unlock() },
            shared = true,
        )

        measureLock(
            benchmark = "arraySpinLock",
            threads = threads,
            lock = { arraySpinLock.lock(LOCK_INDEX) },
            unlock = { arraySpinLock.unlock(LOCK_INDEX) },
        )

        measureLock(
            benchmark = "nodeSpinLock",
            threads = threads,
            lock = { nodeSpinLock.lock() },
            unlock = { nodeSpinLock.unlock() },
        )

        measureLock(
            benchmark = "statelyLock",
            threads = threads,
            lock = { statelyLock.lock() },
            unlock = { statelyLock.unlock() },
        )

        measureLock(
            benchmark = "pthreadMutex",
            threads = threads,
            lock = { mutex.lock() },
            unlock = { mutex.unlock() },
        )
    }
}

/**
 * Measures the pairs of [lock] and [unlock] that the threads do on the same lock. A thread adds
 * one to a counter while it holds the lock. With a [shared] lock it only reads the counter,
 * since other threads hold the lock too.
 */
private inline fun measureLock(
    benchmark: String,
    threads: Int,
    crossinline lock: () -> Unit,
    crossinline unlock: () -> Unit,
    shared: Boolean = false,
) {
    val counter = Counter()
    val counted = AtomicLong(0L)

    report(benchmark, "lock", keyCount = 0, threads) {
        concurrentRound(threads) { _, running ->
            var operations = 0L
            var seen = 0L

            while (running.load()) {
                repeat(CHUNK) {
                    lock()

                    if (shared) {
                        seen += counter.value
                    } else {
                        counter.value++
                    }

                    unlock()
                }

                operations += CHUNK
            }

            val _ = Sink.addAndFetch(seen)
            val _ = counted.addAndFetch(operations)
            operations
        }
    }

    // A lock that let two threads in at once would have lost some of the additions
    check(shared || counter.value == counted.load()) { "$benchmark is not a lock" }
}
