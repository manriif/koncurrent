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

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Threads
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.atomic.AtomicInteger

/**
 * Reads and writes mixed on one map shared by the threads. An invocation does one operation.
 * Each subclass runs it on a number of threads.
 */
public abstract class MixedBenchmark {

    @Benchmark
    public fun read90Write10(state: MixedState, random: ThreadRandom, blackhole: Blackhole) {
        state.operate(random.next(), READ_90_WRITE_10, blackhole)
    }

    @Benchmark
    public fun read50Write50(state: MixedState, random: ThreadRandom, blackhole: Blackhole) {
        state.operate(random.next(), READ_50_WRITE_50, blackhole)
    }
}

@Threads(1)
public open class Mixed1ThreadBenchmark : MixedBenchmark()

@Threads(2)
public open class Mixed2ThreadsBenchmark : MixedBenchmark()

@Threads(4)
public open class Mixed4ThreadsBenchmark : MixedBenchmark()

@Threads(8)
public open class Mixed8ThreadsBenchmark : MixedBenchmark()

@State(Scope.Benchmark)
public open class MixedState {

    @Param
    public lateinit var implementation: Implementation

    @Param("1000", "1000000")
    public var keyCount: Int = 0

    private lateinit var keys: Keys
    private lateinit var map: MutableMap<Key, Key>

    @Setup
    public fun setUp() {
        keys = Keys(keyCount)
        map = implementation.newMap()

        // Starts with half of the keys, which is where the mix of puts and removes settles
        for (index in 0..<keyCount step 2) {
            map[keys.stored[index]] = keys.stored[index]
        }
    }

    public fun operate(random: Int, writes: Int, blackhole: Blackhole) {
        val keys = keys
        val map = map

        mixedOperation(
            random = random,
            keyCount = keyCount,
            writes = writes,
            get = { blackhole.consume(map[keys.probes[it]]) },
            put = { blackhole.consume(map.put(keys.stored[it], keys.stored[it])) },
            remove = { blackhole.consume(map.remove(keys.probes[it])) },
        )
    }
}

@State(Scope.Thread)
public open class ThreadRandom {

    private val random = XorShift(Seeds.incrementAndGet() * -0x61c88647)

    public fun next(): Int = random.next()

    private companion object {

        val Seeds = AtomicInteger()
    }
}
