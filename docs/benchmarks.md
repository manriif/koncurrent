# Benchmarks

What Koncurrent costs against the JDK and against Stately, and how the figures were taken.
[CONTRIBUTING.md](../CONTRIBUTING.md#benchmarks) tells how to run them.

## Summary

- **Native.** One thread reads the map 2.2 to 3.8 times faster than through Stately. With 8
  threads the map does 15 to 34 times the work of Stately on mixed workloads, and 33 to 66
  times on `computeIfAbsent` of present keys. Stately only wins when one thread fills a map of
  a million keys, where Koncurrent is 27% behind.
- **JVM.** The map is the one of the JDK there. The port, which the other targets use, stays
  within 7% of the JDK on most workloads. It is 11% to 17% behind on inserts into a new map and
  where several threads update few keys.
- **Locks.** On native `ReentrantLock` does 1.6 to 3.3 times the work of the Stately lock, and
  more than a bare `pthread_mutex` at every thread count. The read lock is slower than
  `ReentrantLock` when it is held for a few instructions only.

## Setup

| | |
|---|---|
| Machine | Apple M5 Pro, 18 cores, 64 GB of memory, macOS 27.3 |
| Kotlin | 2.4.21 |
| JVM | JetBrains Runtime 25.0.4, with JMH 1.37 through kotlinx-benchmark 0.5.0 |
| Native | `macosArm64`, release binary |
| Stately | 2.1.0 |

**Keys.** A key holds a random hash code, like a string or an object identity. A map stores one
set of keys and is asked for equal keys that are other instances, in another order. So a lookup
calls `equals`, and it does not follow the order of insertion.

**Workloads.**

- `get` reads every key of a full map. `remove` empties a full map. `put` fills a new map,
  which grows from its default size.
- The mixed workloads start with half of the keys. Each operation picks a key at random. A
  write is a `put` or a `remove` with equal chances, so the map keeps about half of the keys.
- `computeIfAbsent` asks a full map for keys that it has. It is what a cache does once it is
  filled.
- A lock benchmark has every thread take the same lock to add one to a counter.

**Method.** On the JVM each figure comes from JMH, with 3 warm-up iterations and 5 measured
iterations of 2 seconds, and is the mean of two runs. kotlinx-benchmark only runs one thread on
Kotlin/Native, so the native figures come from a harness of this project that starts real
threads. It does 2 warm-up rounds and 5 measured rounds of 1 second and keeps the median. Each
native figure is the median of three runs.

A figure marked \* comes from runs that differ by more than 15%. Take it as an order of
magnitude.

## Maps on native

Millions of operations per second, all threads together. Higher is better.

**1 000 keys**

| Workload | Threads | Koncurrent | Stately | vs Stately |
|---|---:|---:|---:|---:|
| `get` | 1 | 157.4 | 41.2 \* | 3.82x |
| `put` into a new map | 1 | 31.2 | 24.3 \* | 1.28x |
| `remove` | 1 | 77.3 | 30.7 \* | 2.52x |
| 90% `get`, 5% `put`, 5% `remove` | 1 | 75.2 | 37.5 \* | 2.01x |
| 90% `get`, 5% `put`, 5% `remove` | 2 | 114.1 | 14.8 \* | 7.71x |
| 90% `get`, 5% `put`, 5% `remove` | 4 | 211.8 | 9.24 \* | 22.91x |
| 90% `get`, 5% `put`, 5% `remove` | 8 | 216.4 \* | 8.65 \* | 25.02x |
| 50% `get`, 25% `put`, 25% `remove` | 1 | 58.5 | 29.9 \* | 1.96x |
| 50% `get`, 25% `put`, 25% `remove` | 2 | 60.1 | 11.0 \* | 5.45x |
| 50% `get`, 25% `put`, 25% `remove` | 4 | 97.5 | 6.65 \* | 14.66x |
| 50% `get`, 25% `put`, 25% `remove` | 8 | 88.9 | 5.99 | 14.84x |
| `computeIfAbsent` of present keys | 1 | 116.5 | 39.4 \* | 2.96x |
| `computeIfAbsent` of present keys | 2 | 232.0 | 18.3 \* | 12.69x |
| `computeIfAbsent` of present keys | 4 | 429.0 | 10.6 \* | 40.31x |
| `computeIfAbsent` of present keys | 8 | 721.4 | 10.9 | 66.47x |

**1 000 000 keys**

| Workload | Threads | Koncurrent | Stately | vs Stately |
|---|---:|---:|---:|---:|
| `get` | 1 | 14.4 | 6.65 \* | 2.17x |
| `put` into a new map | 1 | 4.40 | 6.03 \* | 0.73x |
| `remove` | 1 | 7.58 | 3.83 | 1.98x |
| 90% `get`, 5% `put`, 5% `remove` | 1 | 9.32 | 4.79 | 1.95x |
| 90% `get`, 5% `put`, 5% `remove` | 2 | 17.7 | 1.93 \* | 9.18x |
| 90% `get`, 5% `put`, 5% `remove` | 4 | 33.4 \* | 1.38 \* | 24.29x |
| 90% `get`, 5% `put`, 5% `remove` | 8 | 68.6 | 2.00 \* | 34.30x |
| 50% `get`, 25% `put`, 25% `remove` | 1 | 7.11 | 4.09 | 1.74x |
| 50% `get`, 25% `put`, 25% `remove` | 2 | 13.6 \* | 1.88 \* | 7.23x |
| 50% `get`, 25% `put`, 25% `remove` | 4 | 28.3 \* | 1.24 \* | 22.73x |
| 50% `get`, 25% `put`, 25% `remove` | 8 | 50.0 | 1.60 \* | 31.36x |
| `computeIfAbsent` of present keys | 1 | 7.23 | 3.63 | 1.99x |
| `computeIfAbsent` of present keys | 2 | 14.4 | 1.81 \* | 7.97x |
| `computeIfAbsent` of present keys | 4 | 28.3 | 1.07 \* | 26.38x |
| `computeIfAbsent` of present keys | 8 | 54.5 | 1.63 \* | 33.34x |

## Maps on the JVM

On the JVM `koncurrent.ConcurrentHashMap` is the class of the JDK, so the **JDK** column is what
a JVM program gets. The **Port** column is the code that the other targets run, measured on the
JVM where it can be compared with the original.

Millions of operations per second, all threads together. Higher is better.

**1 000 keys**

| Workload | Threads | Port | JDK | Stately | vs JDK | vs Stately |
|---|---:|---:|---:|---:|---:|---:|
| `get` | 1 | 534.9 | 536.2 | 173.8 | 1.00x | 3.08x |
| `put` into a new map | 1 | 44.6 | 53.3 | 81.2 | 0.84x | 0.55x |
| `remove` | 1 | 175.9 | 166.1 | 132.9 | 1.06x | 1.32x |
| 90% `get`, 5% `put`, 5% `remove` | 1 | 80.4 | 80.1 | 72.7 | 1.00x | 1.11x |
| 90% `get`, 5% `put`, 5% `remove` | 2 | 132.7 | 127.1 | 22.1 | 1.04x | 6.00x |
| 90% `get`, 5% `put`, 5% `remove` | 4 | 213.7 | 251.1 | 24.3 \* | 0.85x | 8.80x |
| 90% `get`, 5% `put`, 5% `remove` | 8 | 264.4 | 271.9 | 28.2 | 0.97x | 9.38x |
| 50% `get`, 25% `put`, 25% `remove` | 1 | 62.0 | 63.2 | 58.8 | 0.98x | 1.06x |
| 50% `get`, 25% `put`, 25% `remove` | 2 | 65.4 | 75.6 | 20.6 | 0.87x | 3.17x |
| 50% `get`, 25% `put`, 25% `remove` | 4 | 112.4 | 126.0 | 20.9 | 0.89x | 5.37x |
| 50% `get`, 25% `put`, 25% `remove` | 8 | 96.1 | 116.1 | 22.7 | 0.83x | 4.24x |

**1 000 000 keys**

| Workload | Threads | Port | JDK | Stately | vs JDK | vs Stately |
|---|---:|---:|---:|---:|---:|---:|
| `get` | 1 | 30.1 \* | 32.2 | 17.0 | 0.93x | 1.77x |
| `put` into a new map | 1 | 8.19 | 9.36 | 15.5 | 0.88x | 0.53x |
| `remove` | 1 | 13.0 | 12.7 | 10.9 | 1.02x | 1.20x |
| 90% `get`, 5% `put`, 5% `remove` | 1 | 18.9 | 19.1 | 12.8 | 0.99x | 1.48x |
| 90% `get`, 5% `put`, 5% `remove` | 2 | 35.7 | 37.7 | 9.84 | 0.95x | 3.63x |
| 90% `get`, 5% `put`, 5% `remove` | 4 | 74.5 | 73.1 | 9.91 | 1.02x | 7.52x |
| 90% `get`, 5% `put`, 5% `remove` | 8 | 124.7 | 126.2 | 9.42 | 0.99x | 13.24x |
| 50% `get`, 25% `put`, 25% `remove` | 1 | 13.6 | 13.8 \* | 10.6 | 0.99x | 1.28x |
| 50% `get`, 25% `put`, 25% `remove` | 2 | 26.5 | 25.6 | 8.12 | 1.04x | 3.26x |
| 50% `get`, 25% `put`, 25% `remove` | 4 | 52.5 | 52.2 | 7.80 | 1.01x | 6.73x |
| 50% `get`, 25% `put`, 25% `remove` | 8 | 83.8 | 89.3 | 7.91 | 0.94x | 10.59x |

## Locks

Millions of lock and unlock pairs per second, all threads together. Higher is better.

**Native**

| Lock | 1 thread | 2 threads | 4 threads | 8 threads |
|---|---:|---:|---:|---:|
| Koncurrent `ReentrantLock` | 129.2 | 57.9 | 58.7 | 63.6 |
| Koncurrent write lock | 108.6 | 37.2 | 45.2 | 50.7 |
| Koncurrent read lock | 104.3 | 21.6 \* | 29.4 | 24.0 |
| Stately `Lock` | 82.2 | 36.7 \* | 22.2 | 19.1 |
| `pthread_mutex` | 118.5 | 43.6 \* | 29.4 | 24.7 |
| Spin lock word in an atomic array | 199.6 | 26.4 | 19.1 | 10.3 |
| Spin lock word in an atomic of each node | 152.0 | 35.1 | 20.7 | 9.12 |

The read lock is taken by threads that only read the counter. The other locks are taken to
add one to it.

With two threads the read lock has two levels, about 21 and 25 million. Where the lock lands
in memory decides which, and a run may get one or the other.

The lock figures come from a later session than the map figures. A lock alone on its thread is
slower in it: `pthread_mutex` does 118 where it did 164. Compare the rows of a table with each
other.

**JVM**

| Lock | 1 thread | 2 threads | 4 threads | 8 threads |
|---|---:|---:|---:|---:|
| `ReentrantLock` port | 233.1 | 101.6 \* | 59.6 \* | 107.7 |
| `ReentrantLock` of the JDK | 178.0 | 12.8 \* | 110.0 | 128.9 \* |
| Write lock port | 199.7 | 71.2 | 71.1 | 89.6 |
| Write lock of the JDK | 258.9 | 14.7 \* | 89.9 | 108.3 \* |
| Read lock port | 156.6 | 16.8 | 26.4 \* | 18.6 |
| Read lock of the JDK | 119.0 | 13.7 | 5.61 | 4.29 |
| `synchronized` | 213.9 | 5.70 | 6.07 | 6.13 |
| Spin lock word in an atomic array | 234.1 | 41.9 \* | 32.7 | 12.5 \* |
| Spin lock word in an atomic of each node | 233.7 | 43.7 | 32.2 | 7.29 \* |

The ports are the code that the native targets run, measured on the JVM against the classes
of the JDK that they come from.

The ports are written for Kotlin/Native, and one choice shows here. Each port waits in a
function of its own. That is worth 19% to the read-write lock on native with one thread, and
it costs the write lock port 13% and the read lock port 8% on the JVM. No program runs these
ports on the JVM, where the locks are the classes of the JDK.

With two threads the locks of the JDK have two levels, and a run gets one or the other. In four
runs of the same code `ReentrantLock` did 11.5, 11.6, 14.0 and 50.9, and `synchronized` did
5.7 twice, then 23.6 and 23.8.

The two spin locks are there as references. The first one is the bin lock of the map port: a
lock word for each bin, in an atomic array. The second one keeps the lock word in the node,
which Kotlin atomics cannot do without one more object per node.

## Reading

**Stately guards a standard collection with one lock.** Every operation takes it, lookups
included. So its throughput does not grow with the threads. On native it falls, from 37.5
million operations per second with one thread to 8.7 with eight. The map of Koncurrent locks
one bin for an update and nothing for a lookup, so it scales until the memory does not follow.

**One thread.** A lookup that takes no lock is also faster when nobody else is there: 157
million lookups per second against 41 on a map of a thousand keys.

**Filling a large map is the weak spot.** A new map of a million keys grows 17 times. Each
growth allocates a table, and Kotlin/Native copies the array given to an `AtomicArray`, so each
table is allocated twice. The garbage collector takes the rest: a third to 42% of the time of
this benchmark is spent waiting for it. Give the map its size when you know it,
`ConcurrentHashMap(1_000_000)`.

**The port against the JDK.** On reads and on large maps the port matches the original. It
loses 11% to 17% where updates dominate. An update writes two cache lines there, the lock word
and the node. The JDK writes one, because its lock is in the header of the node.

**The lock.** A thread that finds the lock taken tries a few more times before it goes to
sleep. Sleeping and being woken up costs far more than a short critical section. This is why
the port holds up with two threads on the JVM, about 100 million pairs per second, where the
`ReentrantLock` of the JDK drops to between 12 and 51 and `synchronized` to between 6 and 24.
The JDK lock wins again from four threads.

**The read-write lock.** A reader does not wait for the other readers, but it still writes the
state of the lock, twice for each hold. With several readers that state moves from one
processor to the other, and the read lock does 22 to 29 million pairs per second where
`ReentrantLock` does 58 to 64. The benchmark is the worst case for it, since nothing is done
under the lock. The read lock pays off when reading takes longer than taking a lock: threads
that each read for a microsecond under a `ReentrantLock` do one million reads per second
together, whatever their number.

The read lock of the JDK has the same weakness, and a deeper one. On the JVM it falls to 4.3
million pairs per second with 8 readers, where the port does 18.6.

## What the optimizations brought

The first version of the ports was measured on the same machine before the optimization work.
`macosArm64`, millions of operations per second.

| Workload | Keys | Threads | Before | After | Change |
|---|---:|---:|---:|---:|---:|
| `get` | 1K | 1 | 126.5 | 157.4 | +24% |
| `put` into a new map | 1K | 1 | 24.9 | 31.2 | +25% |
| `remove` | 1K | 1 | 66.2 | 77.3 | +17% |
| 90% `get`, 5% `put`, 5% `remove` | 1K | 1 | 70.5 | 75.2 | +7% |
| 90% `get`, 5% `put`, 5% `remove` | 1K | 2 | 103.7 | 114.1 | +10% |
| 90% `get`, 5% `put`, 5% `remove` | 1K | 4 | 194.7 | 211.8 | +9% |
| 90% `get`, 5% `put`, 5% `remove` | 1K | 8 | 226.3 | 216.4 | -4% |
| 50% `get`, 25% `put`, 25% `remove` | 1K | 1 | 54.2 | 58.5 | +8% |
| 50% `get`, 25% `put`, 25% `remove` | 1K | 2 | 56.6 | 60.1 | +6% |
| 50% `get`, 25% `put`, 25% `remove` | 1K | 4 | 91.8 | 97.5 | +6% |
| 50% `get`, 25% `put`, 25% `remove` | 1K | 8 | 87.6 | 88.9 | +2% |
| `get` | 1M | 1 | 11.8 | 14.4 | +22% |
| `put` into a new map | 1M | 1 | 4.3 | 4.4 | +3% |
| `remove` | 1M | 1 | 6.2 | 7.6 | +22% |
| 90% `get`, 5% `put`, 5% `remove` | 1M | 1 | 8.2 | 9.3 | +14% |
| 90% `get`, 5% `put`, 5% `remove` | 1M | 2 | 16.4 | 17.7 | +8% |
| 90% `get`, 5% `put`, 5% `remove` | 1M | 4 | 30.8 | 33.4 | +9% |
| 90% `get`, 5% `put`, 5% `remove` | 1M | 8 | 58.3 | 68.6 | +18% |
| 50% `get`, 25% `put`, 25% `remove` | 1M | 1 | 6.4 | 7.1 | +11% |
| 50% `get`, 25% `put`, 25% `remove` | 1M | 2 | 12.6 | 13.6 | +8% |
| 50% `get`, 25% `put`, 25% `remove` | 1M | 4 | 25.4 | 28.3 | +11% |
| 50% `get`, 25% `put`, 25% `remove` | 1M | 8 | 45.9 | 50.0 | +9% |
| `ReentrantLock` | - | 1 | 132.8 | 181.8 | +37% |
| `ReentrantLock` | - | 2 | 9.1 | 82.8 | +805% |
| `ReentrantLock` | - | 4 | 64.5 | 61.7 | -4% |
| `ReentrantLock` | - | 8 | 63.8 | 86.1 | +35% |

The gains on one thread come from one finding. Kotlin/Native gives each function a frame for
the garbage collector, even when LLVM inlines it. A function that Kotlin inlines has none. The
[design notes](design.md#what-makes-code-fast-on-kotlinnative) tell more.

The lock gained a short wait before sleeping. It is what turned 9.1 into 82.8 with two threads.
Against a `pthread_mutex` measured in the same run, `ReentrantLock` went from 0.82, 0.17, 1.61
and 2.13 times its throughput with 1, 2, 4 and 8 threads, to 1.11, 1.43, 1.55 and 2.84 times.

The read lock was first ported as it is in the JDK. Two changes then made it 2 to 3 times
faster with several readers: 23.1, 27.1 and 22.5 million pairs per second with 2, 4 and 8
threads, against 11.5, 8.6 and 7.0. The [design notes](design.md#reentrantreadwritelock) tell
what they are.

The read-write lock, the semaphore and the latch then gained from the other side of the frame
finding. The references of a function that Kotlin inlines join the frame of its caller, which
is cleared at each call. The queue of the locks is such a function. Each of these classes now
waits in a function of its own, so only a call that waits clears the references of the queue.
With one thread the write lock went from 91.1 to 108.6 million pairs per second, and the read
lock from 87.2 to 104.3. The two programs ran in turn in the same session.

Other parts were measured once, with probes that are not in the tree.

| Operation | Before | After |
|---|---:|---:|
| `computeIfAbsent` of a present key, 8 threads | 284 M/s | 721 M/s |
| `merge` on one key by 8 threads | 4.7 M/s | 16.4 M/s |
| Iteration over the keys, per key | 14.0 ns | 8.7 ns |
| Iteration over the entries, per entry | 22.5 ns | 13.7 ns |
| `ConcurrentHashSet.contains` | 8.2 ns | 6.7 ns |
| `CopyOnWriteArrayList.get` | 6.2 ns | 3.5 ns |
| `CopyOnWriteArrayList` iteration, per element | 7.2 ns | 2.0 ns |
| `CopyOnWriteArrayList.add` on a short list | 200 ns | 50 ns |
| `ThreadLocal.get` | 10.9 ns | 6.5 ns |
| `ThreadLocal.set` | 17.6 ns | 8.1 ns |
| `Semaphore.acquire` and `release` | 86.9 M/s | 108.9 M/s |
| `CountDownLatch.await` on an open latch | 154 M/s | 391 M/s |
