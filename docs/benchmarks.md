# Benchmarks

What Koncurrent costs against the JDK and against Stately, how the figures were taken, and what each
optimization brought. [CONTRIBUTING.md](../CONTRIBUTING.md#benchmarks) tells how to run them.

## Summary

- **Native.** One thread reads the map 2.2 to 3.8 times faster than through Stately. With 8 threads
  the map does 15 to 34 times the work of Stately on mixed workloads, and 33 to 66 times on
  `computeIfAbsent` of present keys. Stately only wins when one thread fills a map of a million
  keys, where Koncurrent is 27% behind.
- **JVM.** The map is the one of the JDK there. The port, which the other targets use, stays within
  7% of the JDK on most workloads. It is 11% to 17% behind on inserts into a new map and where
  several threads update few keys.
- **Locks.** On native `ReentrantLock` does 1.8 to 4.0 times the work of the Stately lock, and more
  than a bare `pthread_mutex` at every thread count. The read lock is slower than `ReentrantLock`
  when it is held for a few instructions only.
- **Stamped lock.** On native an optimistic read with its validation runs 528 million times per
  second on one thread and 3.4 billion on eight. The read locks do 104 to 112 million on one thread,
  and 10 to 25 on eight.
- **Queues.** On native `ConcurrentLinkedQueue` hands over 78 million elements per second with one
  thread and 21 to 31 million with 2 to 8 threads. That is 2 times, then about 4 times, what the
  lock-free algorithm of the JDK gave there. `ArrayBlockingQueue` hands over 54 million with one
  thread, and 18 to 26 with 2 to 8.
- **Sorted map.** On native `ConcurrentSkipListMap` reads a key 5 to 7 times slower than the hash
  map, which is the price of the order. Its readers do not wait for each other: with nine reads in
  ten on a million keys, eight threads do more than 3 times the work of one.

## Setup

| | |
|---|---|
| Machine | Apple M5 Pro, 18 cores, 64 GB of memory, macOS 27.3 |
| Kotlin | 2.4.21 |
| JVM | JetBrains Runtime 25.0.4, with JMH 1.37 through kotlinx-benchmark 0.5.0 |
| Native | `macosArm64`, release binary |
| Stately | 2.1.0 |

**Keys.** A key holds a random hash code, like a string or an object identity. A map stores one set
of keys and is asked for equal keys that are other instances, in another order. So a lookup calls
`equals`, and it does not follow the order of insertion.

**Workloads.**

- `get` reads every key of a full map. `remove` empties a full map. `put` fills a new map, which
  grows from its default size.
- The mixed workloads start with half of the keys. Each operation picks a key at random. A write is
  a `put` or a `remove` with equal chances, so the map keeps about half of the keys.
- `computeIfAbsent` asks a full map for keys that it has. It is what a cache does once it is filled.
- A lock benchmark has every thread take the same lock to add one to a counter. Nothing else is done
  under the lock, which is the worst case for it.
- A queue benchmark has every thread add an element to the same queue, then take one, which may be
  the element of another thread.
- The sorted map runs the workloads of the maps on the same keys, which it keeps in the order of
  their hash codes, through a comparator. The hash map is measured again in the same runs.

**Method.** On the JVM each figure comes from JMH, with 3 warm-up iterations and 5 measured
iterations of 2 seconds, and is the mean of two runs. kotlinx-benchmark only runs one thread on
Kotlin/Native, so the native figures come from a harness of this project that starts real threads.
It does 2 warm-up rounds and 5 measured rounds of 1 second and keeps the median. Each native figure
is the median of three runs.

A figure marked \* comes from runs that differ by more than 15%. Take it as an order of magnitude.

The tables were not all taken in the same session, and a figure only compares with the others of its
table. The same `pthread_mutex` alone on its thread did 164 million pairs per second in one session
and 116 in another.

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

On the JVM `koncurrent.ConcurrentHashMap` is the class of the JDK, so the **JDK** column is what a
JVM program gets. The **Port** column is the code that the other targets run, measured on the JVM
where it can be compared with the original.

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
| Koncurrent `ReentrantLock` | 147.8 | 66.1 | 69.7 | 72.8 |
| Koncurrent write lock | 105.6 | 38.6 | 45.7 | 49.7 |
| Koncurrent read lock | 103.6 | 21.7 | 29.1 | 24.6 |
| Koncurrent `StampedLock`, write lock | 143.4 | 63.3 | 69.1 | 75.4 |
| Koncurrent `StampedLock`, read lock | 111.8 | 20.4 | 14.0 | 9.83 |
| Koncurrent `StampedLock`, optimistic read | 528.3 | 1075.6 | 2024.7 | 3413.9 |
| Stately `Lock` | 81.0 | 36.3 | 22.1 | 18.3 |
| `pthread_mutex` | 115.8 | 44.9 | 29.9 | 24.4 |
| Spin lock word in an atomic array | 193.2 | 26.6 | 18.9 | 10.7 |
| Spin lock word in an atomic of each node | 146.2 | 34.7 | 20.5 | 8.35 |

The read locks are taken by threads that only read the counter. The other locks are taken to add one
to it. An optimistic read reads the counter and validates its stamp, and no thread writes meanwhile.

Three things to know about these figures.

- **`ReentrantLock` alone does 148 here, and about 128 in a program that takes nothing else.** This
  program has more classes that take the lock port, and the compiler then keeps the wait of the lock
  out of its fast path.
- **The read lock has two levels with two threads**, about 21 and 25 million. Where the lock lands
  in memory decides which, and a run may get one or the other.
- **A fair lock was measured apart.** Alone, a thread takes it at half the speed of a plain one, 63
  against 129 million pairs. With 2 to 8 threads it does 0.36 million, against 58 to 63, since every
  release hands the lock over to a thread that sleeps.

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

The ports are the code that the native targets run, measured on the JVM against the classes of the
JDK that they come from. No program runs them there, where the locks are the classes of the JDK. The
write lock port is behind the one of the JDK with one thread, in part for
[a choice that suits Kotlin/Native](#the-locks).

With two threads the locks of the JDK have two levels, and a run gets one or the other. In four runs
of the same code `ReentrantLock` did 11.5, 11.6, 14.0 and 50.9, and `synchronized` did 5.7 twice,
then 23.6 and 23.8.

The two spin locks are there as references. The first one is the bin lock of the map port: a lock
word for each bin, in an atomic array. The second one keeps the lock word in the node, which Kotlin
atomics cannot do without one more object per node.

## Queues

Millions of pairs of `offer` and `poll` per second on native, all threads together. Higher is
better. The deques add at the head and take at the tail.

| Queue | 1 thread | 2 threads | 4 threads | 8 threads |
|---|---:|---:|---:|---:|
| `ConcurrentLinkedQueue` | 77.8 | 21.4 | 26.2 | 31.1 |
| `ConcurrentLinkedDeque` | 83.9 | 32.8 | 26.8 | 35.4 |
| `ArrayBlockingQueue` | 54.2 | 22.7 | 18.1 | 25.5 |
| `LinkedBlockingDeque` | 42.3 | 15.2 | 16.9 | 17.0 |
| `LinkedBlockingQueue` | 37.8 | 7.26 | 4.10 \* | 10.4 |

The two concurrent classes are one ring of elements under one lock. [Evolution](#the-queues) has the
designs that were measured before that choice.

`LinkedBlockingQueue` is the slowest when every thread adds and takes in turn. Its two locks let a
thread that adds and a thread that takes work at the same time, and they pay for it when the queue
is empty at each step: adding to an empty queue takes both locks, to wake up a thread that would
wait for an element. It does better where some threads only add and others only take.

On the JVM and Android these classes are the ones of the JDK.

## Sorted map

Millions of operations per second on native, all threads together. Higher is better. The hash map is
`ConcurrentHashMap`, on the same keys in the same runs.

**1 000 keys**

| Workload | Threads | Sorted map | Hash map | Sorted against hash |
|---|---:|---:|---:|---:|
| `get` | 1 | 30.8 | 155.9 | 0.20x |
| `put` into a new map | 1 | 18.1 | 30.2 | 0.60x |
| `remove` | 1 | 21.3 | 80.9 | 0.26x |
| 90% `get`, 5% `put`, 5% `remove` | 1 | 18.5 | 75.7 | 0.24x |
| 90% `get`, 5% `put`, 5% `remove` | 2 | 19.7 | 113.9 | 0.17x |
| 90% `get`, 5% `put`, 5% `remove` | 4 | 20.4 | 212.0 | 0.10x |
| 90% `get`, 5% `put`, 5% `remove` | 8 | 13.4 | 234.0 \* | 0.06x |
| 50% `get`, 25% `put`, 25% `remove` | 1 | 16.0 | 59.0 | 0.27x |
| 50% `get`, 25% `put`, 25% `remove` | 2 | 9.80 | 59.2 | 0.17x |
| 50% `get`, 25% `put`, 25% `remove` | 4 | 8.98 | 97.8 | 0.09x |
| 50% `get`, 25% `put`, 25% `remove` | 8 | 7.67 | 89.6 | 0.09x |

**1 000 000 keys**

| Workload | Threads | Sorted map | Hash map | Sorted against hash |
|---|---:|---:|---:|---:|
| `get` | 1 | 1.95 \* | 14.3 | 0.14x |
| `put` into a new map | 1 | 2.00 | 4.63 \* | 0.43x |
| `remove` | 1 | 1.86 \* | 8.09 | 0.23x |
| 90% `get`, 5% `put`, 5% `remove` | 1 | 2.32 | 9.43 | 0.25x |
| 90% `get`, 5% `put`, 5% `remove` | 2 | 4.15 | 18.8 | 0.22x |
| 90% `get`, 5% `put`, 5% `remove` | 4 | 6.61 | 36.2 | 0.18x |
| 90% `get`, 5% `put`, 5% `remove` | 8 | 7.63 | 65.1 \* | 0.12x |
| 50% `get`, 25% `put`, 25% `remove` | 1 | 2.11 | 7.10 | 0.30x |
| 50% `get`, 25% `put`, 25% `remove` | 2 | 2.73 | 14.2 | 0.19x |
| 50% `get`, 25% `put`, 25% `remove` | 4 | 2.68 | 26.4 | 0.10x |
| 50% `get`, 25% `put`, 25% `remove` | 8 | 2.14 | 45.5 | 0.05x |

On the JVM and Android this class is the skip list of the JDK, which takes no lock at all.
[Design](design.md#the-sorted-map-and-set) tells how it is built elsewhere.

## Reading

**Stately guards a standard collection with one lock.** Every operation takes it, lookups included.
So its throughput does not grow with the threads. On native it falls, from 37.5 million operations
per second with one thread to 8.7 with eight. The map of Koncurrent locks one bin for an update and
nothing for a lookup, so it scales until the memory does not follow.

**One thread.** A lookup that takes no lock is also faster when nobody else is there: 157 million
lookups per second against 41 on a map of a thousand keys.

**Filling a large map is the weak spot.** A new map of a million keys grows 17 times. Each growth
allocates a table, twice on Kotlin/Native, and the garbage collector takes the rest: a third to 42%
of the time of this benchmark is spent waiting for it. Give the map its size when you know it,
`ConcurrentHashMap(1_000_000)`.

**The port against the JDK.** On reads and on large maps the port matches the original. It loses 11%
to 17% where updates dominate, since [an update writes two cache lines](design.md#the-map) where the
JDK writes one.

**The lock.** A thread that finds the lock taken tries a few more times before it goes to sleep.
Sleeping and being woken up costs far more than a short critical section. This is why the port holds
up with two threads on the JVM, about 100 million pairs per second, where the `ReentrantLock` of the
JDK drops to between 12 and 51 and `synchronized` to between 6 and 24. The JDK lock wins again from
four threads.

**The read-write lock.** A reader does not wait for the other readers, but it still writes the state
of the lock, twice for each hold. With several readers that state moves from one processor to the
other, and the read lock does 22 to 29 million pairs per second where `ReentrantLock` does 66 to 73.
The read lock pays off when reading takes longer than taking a lock: threads that each read for a
microsecond under a `ReentrantLock` do one million reads per second together, whatever their number.
The read lock of the JDK has the same weakness, and a deeper one. On the JVM it falls to 4.3 million
pairs per second with 8 readers, where the port does 18.6.

**The stamped lock.** Its write lock keeps up with `ReentrantLock`: 143 million pairs per second
alone, and 63 to 75 with 2 to 8 threads. Its read lock is the fastest read lock for one thread, 112
against 104, since it counts nothing for the thread. With four threads and more it falls under the
other one, 14.0 then 9.8 against 29.1 then 24.6. It releases a hold with an update that is tried
again when another reader got in the way, where the read-write lock adds once.
[Design](design.md#stampedlock) tells why it cannot do the same.

Its optimistic read is another matter. It writes nothing that the readers share, so it grows with
them: 528 million reads and validations per second on one thread, 3.4 billion on eight. That is 5
times a read lock on one thread and 140 times on eight. It is what the lock is for.

**The sorted map.** It does more for each operation than a hash map. It compares its key a dozen
times among a thousand keys, and more than twenty times among a million, where the hash map computes
one hash code. So one thread reads it 5 to 7 times slower. With threads it depends on what they do.
A read takes no lock, and a change takes the one lock of the map. With nine reads in ten on a
million keys, eight threads do more than 3 times the work of one. With as many changes as reads the
map stays where one thread puts it, since the changes take turns. On a small map an operation is so
short that the lock is most of it, and more threads bring little.

## Evolution

What the ports gained on the way, and what was measured to decide how they are built.
[Design](design.md) tells how each of these works. The figures are those of `macosArm64`, in the
session of each change.

### From the first version

The first version of the ports was measured on the same machine before the optimization work.
Millions of operations per second.

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

The gains on one thread come from one finding. Kotlin/Native gives each function a frame for the
garbage collector, even when LLVM inlines it. A function that Kotlin inlines has none. Inlining
three small functions made lookups 25% faster.

The lock gained a short wait before sleeping. It is what turned 9.1 into 82.8 with two threads.
Against a `pthread_mutex` measured in the same run, `ReentrantLock` went from 0.82, 0.17, 1.61 and
2.13 times its throughput with 1, 2, 4 and 8 threads, to 1.11, 1.43, 1.55 and 2.84 times.

Other operations were measured once, before and after their optimization.

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

### The locks

- **The read lock** was first ported as it is in the JDK. Not caching the last reader, and releasing
  a hold with one atomic addition, made it 2 to 3 times faster with several readers: 23.1, 27.1 and
  22.5 million pairs per second with 2, 4 and 8 threads, against 11.5, 8.6 and 7.0.
- **A wait in a function of its own.** The references of a function that Kotlin inlines join the
  frame of its caller, which is cleared at each call. The queue of the locks is such a function.
  Since each class waits in a function of its own, only a call that waits clears those references.
  With one thread the write lock went from 91.1 to 108.6 million pairs per second, and the read lock
  from 87.2 to 104.3. The JIT of the JVM prefers the wait in place, by 8% to 13%.
- **The fair mode** had to cost the locks that are not fair as little as possible. A flag read
  before the update of the state cost two readers 22%, then 30% once the lock had a queue, and the
  plain lock 9% with four threads. One more volatile read of the head cost the write lock 4%. With
  16 instructions more in `unlock`, LLVM stopped inlining it, and the plain lock lost 5% to 13%. A
  lock 8 bytes bigger lost 3% to 7% with 2 and 4 threads. With a fair lock told by its class, the
  plain lock is 0.3% to 0.6% below what it was, the write lock about 2%, and the read lock 3% once
  it has a queue.
- **The first fence of the stamped lock** takes a quarter of the speed of an optimistic read: 526
  million reads and validations per second with one thread, against 696 without. An atomic addition
  or an exchange on its cell costs a little more than the write, 490 and 482, and a compare-and-set
  twice as much, 265.

### The queues

Outside the JVM and Android the queues that never wait are a ring under one lock, because that is
what was the fastest there. Millions of pairs of `offer` and `poll` per second:

| | 1 thread | 2 threads | 4 threads | 8 threads |
|---|---:|---:|---:|---:|
| The algorithm of JSR-166, an atomic reference for each field | 43 | 5.7 | 7.2 | 6.8 |
| The same, a node being one atomic array | 41 | 5.9 | 7.5 | 6.6 |
| Two locks on linked nodes | 81 | 7.9 | 5.5 | 28 |
| One lock on the `ArrayDeque` of the standard library | 57 | 22 | 25 | 27 |
| One lock on a ring of its own | 93 | 37 | 32 | 39 |

The ring of the last row took the lock port directly. Through the public lock it did 62 with one
thread. With its work split in three functions that called each other, the queue did 46, against 81
written in place.

The blocking queues take their lock without a call as well. They went through the public
`ReentrantLock` at first, which is one call more for each lock and unlock. The same program ran
before and after that change, in turn, and the queues did this much more or less:

| Queue | 1 thread | 2 threads | 4 threads | 8 threads |
|---|---:|---:|---:|---:|
| `ArrayBlockingQueue` | +23% | +16% | -1% | +21% |
| `LinkedBlockingQueue` | +25% | +35% | -23% | +59% |
| `LinkedBlockingDeque` | +3% | -13% | -3% | -2% |

One thread gains the calls. Several threads wait with the same code as before, so only the timing
changed, and it goes both ways.

### The sorted map

With every operation under its lock, the sorted map got slower with each thread that joined. Reading
an entry without the lock changed that. Millions of operations per second, when nine operations in
ten are a `get` and the others add or remove a key:

| Keys | Threads | Every operation under the lock | Reads without the lock |
|---:|---:|---:|---:|
| 1 000 | 1 | 19.0 | 18.5 |
| 1 000 | 2 | 13.4 | 20.0 |
| 1 000 | 4 | 11.5 | 20.6 |
| 1 000 | 8 | 11.2 | 13.7 |
| 1 000 000 | 1 | 2.44 | 2.43 |
| 1 000 000 | 2 | 1.47 | 4.35 |
| 1 000 000 | 4 | 1.45 | 6.95 |
| 1 000 000 | 8 | 1.45 | 7.82 |

The first column is one run, and the second one the median of three. One thread alone pays nothing
for the reads without the lock. The reads lose where they are not the rule: when half of the
operations change a small map, four to eight threads do 8% to 17% less than under the lock.

### What did not pay

Each of these was measured and dropped.

- **Lock words further apart in the map.** Neighbouring bins share the cache line of their lock
  words. One word per line changed nothing at 8 threads beyond noise, and slowed inserts on one
  thread by 8% to 47%, as the table grows with it.
- **Fewer lock words on a large table**, shared by the bins. No gain beyond noise. The processor
  fetches the lock word and the bin at once.
- **A lookup without the lock before `remove` and `replace`.** It made removals of absent keys 3.8
  times faster at 8 threads, and removals of present keys 21% slower. A key is usually present when
  it is removed.
- **Keeping the wait of a lock out of line** with `kotlin.native.NoInline`. No gain. That mark only
  stops the inliner of Kotlin, and LLVM still inlines a function that has one caller.
- **A release that looks past the nodes that gave up.** One more read on that path took 4% to 8%
  from a `ReentrantLock` that 2 to 8 threads fight for. A signal repairs the queue instead.
- **The state of a lock in the lock itself**, out of the class of the queue. Only the place of the
  field in the object changed, and `ReentrantLock` lost 7% to 12% with four threads.
