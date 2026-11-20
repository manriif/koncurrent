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
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole

/**
 * One kind of operation on one thread. An invocation does it once for each key, so the score
 * times [SingleThreadedState.keyCount] is the number of operations per second.
 */
public open class SingleThreadedBenchmark {

    /**
     * Gets each key from a map that holds them all.
     */
    @Benchmark
    public fun get(state: SingleThreadedState, blackhole: Blackhole) {
        val map = state.full

        for (key in state.keys.probes) {
            blackhole.consume(map[key])
        }
    }

    /**
     * Puts each key in a new map, which grows from its default size.
     */
    @Benchmark
    public fun put(state: SingleThreadedState): MutableMap<Key, Key> = state.newFullMap()

    /**
     * Removes each key from a map that holds them all. The map is filled out of the measure.
     */
    @Benchmark
    public fun remove(state: SingleThreadedState, refilled: RefilledState, blackhole: Blackhole) {
        val map = refilled.map

        for (key in state.keys.probes) {
            blackhole.consume(map.remove(key))
        }
    }
}

@State(Scope.Benchmark)
public open class SingleThreadedState {

    @Param
    public lateinit var implementation: Implementation

    @Param("1000", "1000000")
    public var keyCount: Int = 0

    public lateinit var keys: Keys
    public lateinit var full: MutableMap<Key, Key>

    @Setup
    public fun setUp() {
        keys = Keys(keyCount)
        full = newFullMap()
    }

    public fun newFullMap(): MutableMap<Key, Key> {
        val map = implementation.newMap()

        for (key in keys.stored) {
            map[key] = key
        }

        return map
    }
}

@State(Scope.Thread)
public open class RefilledState {

    public lateinit var map: MutableMap<Key, Key>

    @Setup(Level.Invocation)
    public fun refill(state: SingleThreadedState) {
        map = state.newFullMap()
    }
}
