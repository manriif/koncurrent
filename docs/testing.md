# Testing

How Koncurrent is verified, and the results of the last run.

## Results

A complete `./gradlew build` passes. It compiles the 25 targets, checks the public API against
its record and runs the tests below. The times are those of the tests of one run. Most of the
JVM time is Lincheck.

| Target | Tests | Failures | Time |
|---|---:|---:|---:|
| `jvm`, on JDK 17 | 343 | 0 | 5 min 29 s |
| `android`, on the host | 352 | 0 | 19 s |
| `macosArm64` | 327 | 0 | 36 s |
| `iosSimulatorArm64` | 327 | 0 | 38 s |
| `tvosSimulatorArm64` | 327 | 0 | 37 s |
| `watchosSimulatorArm64` | 327 | 0 | 37 s |
| `js`, on Node.js | 139 | 0 | 1 s |
| `wasmJs`, on Node.js | 139 | 0 | 1 s |
| `wasmWasi`, on Node.js | 139 | 0 | 1 s |

The other targets are compiled only: the Apple devices, the x64 Apple targets since the host is
ARM, `linuxX64`, `linuxArm64`, `mingwX64` and the four `androidNative`.

## What has not run

- **Linux, Windows and Android native.** Their code is the one of the Apple targets, except the
  small files of pthread calls: the mutex and the condition that a thread sleeps on, the sleep
  with a time limit and the creation of a thread. Those files never ran. The `test-linux` and
  `test-windows` jobs of the CI are there for that.
- **An Android device below API 24.** The map takes its port there. The host tests cover that
  path, because the API level reads 0 on the host. Ksqlite ran its own tests on a device with
  API 30, which takes the platform map.

## Suites

A public class and the port behind it are tested by the same suites where it matters. So on
the JVM a suite runs once on the Java class, which checks the expectations of the tests, and
once on the port.

The targets with threads are the JVM, Android and native.

### Collections

| Suites | Tests | Targets | What they check |
|---|---:|---|---|
| `ConcurrentHashMapTest`, `ConcurrentHashSetTest` | 18, 6 | All | The public map and set: contract, atomic updates, computations, views, iteration during updates |
| `ConcurrentHashMapStressTest`, `ConcurrentHashSetStressTest` | 4, 2 | With threads | 8 threads: one computation per key, each `compute` and `merge` applied once, exact insertions and removals |
| `PortableConcurrentHashMapTest`, `ViewsTest`, `IterationTest` | 20, 9, 5 | All | The map port: contract, resizes, tree bins with their red-black invariants, 40 000 random operations against a `HashMap`, views, iteration during updates and resizes |
| `PortableConcurrentHashMapStressTest` | 12 | With threads | 8 threads: resizes, shared and colliding keys, exact counts, lookups and iterations during resizes |
| `PlatformConcurrentHashMapTest`, `PlatformConcurrentHashMapStressTest` | 19, 4 | Android | The public map over the platform map, as from API 24 |
| `ConcurrentHashMapBindingTest`, `AtomicUpdateBindingTest` | 4, 4 | Android, JVM | Kotlin lambdas reach the atomic computations, not the ones of `java.util.Map` |
| `ComputationExtensionsTest` | 1 | JVM, Android | The computations as the extensions that common code declares |
| `CopyOnWriteArrayListTest`, `PortableCopyOnWriteArrayListTest` | 21, 20 | All | The list contract, absent additions, iterators of one moment, sub lists |
| `CopyOnWriteArrayListStressTest`, `PortableCopyOnWriteArrayListStressTest` | 4, 4 | With threads | 8 threads: no lost update, one addition per absent element, readers see the elements of one update |

### Locks

Each stress suite runs on the public class and on its port.

| Suites | Tests | Targets | What they check |
|---|---:|---|---|
| `ReentrantLockTest` | 6 | All | Reentrancy, `withLock`, the failure of an unlock without a hold, and a free lock taken in every way |
| `ReentrantLockStressTest`, `PortableReentrantLockStressTest` | 15, 15 | With threads | Exclusion, 16 threads sent to sleep and woken up, blocking until release, `tryLock`, reentrant holds, unlock by another thread. A time limit that runs out or not, 8 threads of which half give up all the time. An interrupt that ends `lockInterruptibly` and a timed `tryLock`, and that `lock` ignores |
| `ReentrantReadWriteLockTest` | 11 | All | The hold counts, a writer that keeps reading, a reader that cannot write, `read` and `write` with the read holds given back, free locks taken in every way, a timed `tryLock` of the write lock that gives up while reading |
| `ReentrantReadWriteLockStressTest`, `PortableReentrantReadWriteLockStressTest` | 19, 19 | With threads | Writers alone, readers together, readers that never see a write half done, a waiting writer that passes before new readers, a reader that reads again while a writer waits, hold counts per thread. A time limit on each lock, readers that pass a writer that gave up, threads that give up while others read and write, an interrupt that ends `lockInterruptibly` of each lock |
| `ConditionTest` | 3 | All | A condition fails without the lock, a signal without a waiting thread does nothing, a timed wait that nothing signals |
| `ConditionStressTest`, `PortableConditionStressTest` | 14, 14 | With threads | A wait gives up every hold and takes them back, `signal` wakes one thread and `signalAll` the others, 4 producers and 4 consumers exchange 80 000 items through a buffer of 4. A time limit that runs out or not, a signal that goes past a wait that ran out or was interrupted, a signal sent just as a wait runs out, a signal that puts a thread behind one that gave up on the lock, 10 000 signals handed one at a time to threads that give up all the time, an interrupt that ends a wait with the lock held again |
| `SynchronizerTest` | 4 | With threads | The two things that the locks do not show one case at a time. An acquisition that throws while its thread is first in the queue lets the next thread acquire. A thread that gives up in front of another one lets that one acquire as soon as it can |
| `SingleThreadLocksTest` | 2 | JS, Wasm | The waits that could never end fail |

### Synchronizers, threads and thread locals

| Suites | Tests | Targets | What they check |
|---|---:|---|---|
| `SemaphoreTest`, `CountDownLatchTest` | 6, 5 | All | The count of permits, `withPermit`, a negative count, a latch that stays open, a time limit on one thread |
| `SemaphoreStressTest`, `PortableSemaphoreStressTest` | 12, 12 | With threads | Never more threads inside than permits, one permit excludes, blocking until a release, 16 waiting threads that each get their permit. A time limit that runs out or not, permits neither lost nor made when threads give up, permits released together that go past the threads that gave up, an interrupt that ends `acquire` |
| `CountDownLatchStressTest`, `PortableCountDownLatchStressTest` | 9, 9 | With threads | Blocking until zero, 16 waiting threads released together, 8 threads that count down. A time limit that runs out or not, a gate that opens for the threads behind those that gave up, an interrupt that ends `await` |
| `SingleThreadWaitsTest` | 2 | JS, Wasm | The waits that could never end fail |
| `ThreadTest` | 18 | With threads | A thread from its start to its end, `join` with and without a time limit, the name, the daemon flag, the builder, `sleep`, the interrupted flag, an interrupt that ends a sleep and a join, an exception that ends its thread alone |
| `ThreadLocalTest`, `ThreadLocalStressTest` | 5, 3 | All, with threads | A value per thread and per thread local, `getOrSet` |
| `ThreadValuesTest` | 3 | Native | The table of a thread: 100 000 random operations against a `HashMap`, and the values of collected thread locals are dropped |

## Lincheck

[Lincheck](https://github.com/JetBrains/lincheck) checks the ports on the JVM, in 7 suites of 2
tests. Each suite is model checked, which explores the interleavings of the threads one by one,
and stress tested.

- **Map.** 12 operations in three settings: keys that share two list bins, a table that is one
  insertion away from its resize, and keys that share one tree bin. 2 threads, 5 operations per
  thread. The size, the iterations, `clear` and `putAll` are left out. They are not atomic by
  design, as in the JDK.
- **List.** 9 operations, the snapshot of the elements included. 3 threads, 3 operations per
  thread.
- **Lock.** A plain counter guarded by the lock, with reentrant holds. 3 threads, 3 operations
  per thread. A lost exclusion loses an increment, and a lost wake-up hangs.
- **Read-write lock.** The same counter, written under the write lock and read under the read
  lock, with reentrant holds and a writer that keeps reading. A reader that sees two values, or
  a writer that is not alone, fails the check.
- **Semaphore.** The same counter guarded by a semaphore of one permit.

The lock, the read-write lock and the semaphore each have an operation that joins the queue
and gives up at once, since its time is over as soon as it has to wait.

All of them are linearizable.

The conditions and the latch are not model checked. Their operations wait for another thread,
which a random scenario cannot promise. They are covered by the stress suites.

## Broken on purpose

Model checking explores a limited number of interleavings. With the wake-up that a thread owes
to its successor when it gives up removed, Lincheck still passed. So each rule that lets a
thread give up was removed in turn, to see a test fail. Each one does.

| What was removed | The test that fails |
|---|---|
| A thread that gives up wakes up the thread behind it | `SynchronizerTest`, in both modes |
| A thread skips a predecessor that gave up | `SynchronizerTest`, in both modes |
| A signal wakes up the thread that it puts behind one that gave up | `aSignalledThreadPassesOneThatGaveUpOnTheLock` of `PortableConditionStressTest` |
| A wait that gave up reports a signal that came first | `everySignalIsTakenByAThreadThatReportsIt` of `PortableConditionStressTest`, 4 runs out of 4 |

The third rule was missing at first. The last test of the table found it: it passed on the lock
of the JDK and ran out of time on the port.

## Repeated runs

The suites of the locks, the conditions, the semaphore, the latch and the threads were also run
30 times in a row on `macosArm64`, 5 730 test runs, without a failure or a hang.
