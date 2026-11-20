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
@file:OptIn(ObsoleteWorkersApi::class)

package koncurrent.benchmarks

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.native.concurrent.ObsoleteWorkersApi
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.TimeSource
import platform.posix.usleep

private const val WARMUP_ROUNDS = 2
private const val MEASURED_ROUNDS = 5

/**
 * The number of operations that a thread does between two looks at the end of its round.
 */
internal const val CHUNK = 1024

private val RoundDuration = 1.seconds

/**
 * Measures a benchmark and prints its line. [round] runs it for one round and returns its
 * operations per second. The line gives the median, the lowest and the highest of the rounds.
 */
internal inline fun report(
    benchmark: String,
    implementation: String,
    keyCount: Int,
    threads: Int,
    round: () -> Double,
) {
    repeat(WARMUP_ROUNDS) {
        val _ = round()
    }

    val rates = List(MEASURED_ROUNDS) { round().toLong() }.sorted()
    val median = rates[MEASURED_ROUNDS / 2]
    val rounds = listOf(median, rates.first(), rates.last())

    println((listOf(benchmark, implementation, keyCount, threads) + rounds).joinToString(","))
}

/**
 * Runs batches of [operations] operations on one thread for one round. [prepare] runs before
 * each batch, out of the measure. Returns the operations per second.
 */
internal inline fun <S> singleThreadedRound(
    operations: Int,
    prepare: () -> S,
    batch: (S) -> Unit,
): Double {
    var elapsed = Duration.ZERO
    var batches = 0L

    while (elapsed < RoundDuration) {
        val state = prepare()
        val start = TimeSource.Monotonic.markNow()

        batch(state)
        elapsed += start.elapsedNow()
        batches++
    }

    return batches * operations / elapsed.toDouble(DurationUnit.SECONDS)
}

/**
 * Runs [work] on [threads] threads for one round. Each thread works while its flag is set and
 * returns its number of operations. Returns the operations per second of all the threads.
 */
internal fun concurrentRound(
    threads: Int,
    work: (thread: Int, running: AtomicBoolean) -> Long,
): Double {
    val ready = AtomicInt(0)
    val running = AtomicBoolean(false)

    val joins = List(threads) { thread ->
        startThread {
            val _ = ready.incrementAndFetch()

            while (!running.load()) {
                // Spins until every thread has started
            }

            work(thread, running)
        }
    }

    while (ready.load() < threads) {
        // Spins until every thread has started
    }

    val start = TimeSource.Monotonic.markNow()

    running.store(true)
    val _ = usleep(RoundDuration.inWholeMicroseconds.toUInt())
    running.store(false)

    val elapsed = start.elapsedNow()
    val operations = joins.sumOf { it() }

    return operations / elapsed.toDouble(DurationUnit.SECONDS)
}

/**
 * Starts [block] on a new thread. Returns a function that waits for its result.
 */
private fun startThread(block: () -> Long): () -> Long {
    val worker = Worker.start()
    val future = worker.execute(TransferMode.SAFE, { block }) { it() }

    return {
        val result = future.result

        worker.requestTermination().result
        result
    }
}
