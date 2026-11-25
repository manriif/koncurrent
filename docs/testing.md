# Testing

How Koncurrent is verified. [CONTRIBUTING.md](../CONTRIBUTING.md#building) tells how to run the
tests.

## Results

A complete `./gradlew build` passes on a Mac with an ARM processor. It compiles the 25 targets,
checks the public API against its record and runs the tests of the targets that the machine can run.

| Target | Tests |
|---|---:|
| `jvm`, on JDK 17 | 1 156 |
| `android`, on the host | 1 189 |
| `macosArm64`, `iosSimulatorArm64`, `tvosSimulatorArm64`, `watchosSimulatorArm64` | 1 113 each |
| `js`, `wasmJs`, `wasmWasi`, on Node.js | 493 each |

The Android tests run on the host, where the API level reads 0. So they cover the ports that back
the map and the stamped lock below API 24, and the suites named `Platform` cover the platform
classes that back them from API 24.

The [`Run tests`](https://github.com/manriif/koncurrent/actions/workflows/test.yml) workflow of
GitHub Actions runs the tests for every pull request, on three systems.

| Job | Machine | What it runs |
|---|---|---|
| `test-linux` | Linux | The JVM, Android host, JS, Wasm and `linuxX64` tests |
| `test-windows` | Windows | The `mingwX64` tests |
| `test-macos` | macOS | The check of the public API of every target, and the `macosArm64` and Apple simulator tests |

## Suites

A public class and the port behind it are tested by the same suites where it matters. So on the JVM
a suite runs once on the Java class, which checks the expectations of the tests, and once on the
port.

The targets with threads are the JVM, Android and native.

### Collections

| Suites | Targets | What they check |
|---|---|---|
| `ConcurrentHashMapTest`, `ConcurrentHashSetTest` | All | The public map and set: contract, atomic updates, computations, views, iteration during updates |
| `ConcurrentHashMapStressTest`, `ConcurrentHashSetStressTest` | With threads | 8 threads: one computation per key, each `compute` and `merge` applied once, exact insertions and removals |
| `PortableConcurrentHashMapTest`, `ViewsTest`, `IterationTest` | All | The map port: contract, resizes, tree bins with their red-black invariants, 40 000 random operations against a `HashMap`, views, iteration during updates and resizes |
| `PortableConcurrentHashMapStressTest` | With threads | 8 threads: resizes, shared and colliding keys, exact counts, lookups and iterations during resizes |
| `PlatformConcurrentHashMapTest`, `PlatformConcurrentHashMapStressTest` | Android | The public map over the platform map, as from API 24 |
| `ConcurrentHashMapBindingTest`, `AtomicUpdateBindingTest` | Android, JVM | Kotlin lambdas reach the atomic computations, not the ones of `java.util.Map` |
| `ComputationExtensionsTest` | JVM, Android | The computations as the extensions that common code declares |
| `CopyOnWriteArrayListTest`, `PortableCopyOnWriteArrayListTest` | All | The list contract, absent additions, iterators of one moment, sub lists |
| `CopyOnWriteArrayListStressTest`, `PortableCopyOnWriteArrayListStressTest` | With threads | 8 threads: no lost update, one addition per absent element, readers see the elements of one update |
| `CopyOnWriteArraySetTest`, `CopyOnWriteArraySetStressTest` | All, with threads | The set on that list: an element once, in the order of its addition, an iterator of one moment that cannot change the set, equality as a set. 8 threads: one addition and one removal per element, and a reader never sees an element twice |
| `ConcurrentSkipListMapTest`, `PortableNavigableMapTest` | All | A sorted map and its views: the order of the keys, the closest keys, both ends and their polls, a comparator, 10 000 keys that fill then empty the chunks, views between two bounds and views of views, the reverse order, the key sets, an iterator that removes while the map changes. 30 000 random operations against a plain map, with a view of random bounds and direction compared now and then. The public map adds its constructors, its equality, its entries that cannot change it, and keys without an order |
| `ConcurrentSkipListSetTest` | All | The public sorted set: an element once and in order, the closest elements, the views, a comparator |
| `ConcurrentSkipListMapStressTest`, `PortableNavigableMapStressTest`, `ConcurrentSkipListSetStressTest` | With threads | 8 threads: 40 000 interleaved keys of which none is lost, a key added by one thread only, entries polled from both ends by one thread each and in order, readers that see sorted keys and a view that stays between its bounds while others change the map. Keys that stay while others come and go around them: a reader finds each one all the same, with its own value and its neighbours |

### Queues

Each suite runs on the public class and on its port.

| Suites | Targets | What they check |
|---|---|---|
| `LinkedBlockingQueueTest`, `ArrayBlockingQueueTest`, `LinkedBlockingDequeAsQueueTest` and the three of their ports | All | A queue with a capacity, on one thread: order of arrival, a full queue, `drainTo` and what it leaves when its target fails, removal from the middle, bulk operations, an iterator that removes, and iterators while the queue changes |
| `LinkedBlockingDequeTest`, `PortableLinkedBlockingDequeTest` | All | Both ends of a deque: adding, taking and reading at the head and at the tail, the stack functions, the descending iterator, iterators while the deque changes |
| `PriorityBlockingQueueTest`, `PortablePriorityBlockingQueueTest` | All | The smallest element first among 1 000, a comparator, elements without an order, removal from anywhere in the heap, `drainTo` in order, an iterator on a copy that removes |
| `LinkedBlockingQueueStressTest`, `ArrayBlockingQueueStressTest`, `LinkedBlockingDequeAsQueueStressTest` and the three of their ports | With threads | 4 producers and 4 consumers exchange 80 000 elements through a queue of 4, each once and in the order of its producer, with threads that wait, try again or give up. `put` and `take` wait, 16 waiting threads are all served, room made by `drainTo`, `clear`, `remove` or an iterator frees the threads that wait for it, the time limits, the interrupts |
| `LinkedBlockingDequeStressTest`, `PortableLinkedBlockingDequeStressTest` | With threads | The same through both ends: exchange of 80 000 elements, waits, time limits and interrupts at the head and at the tail, and an interrupt that only fails a thread that has to wait |
| `PriorityBlockingQueueStressTest`, `PortablePriorityBlockingQueueStressTest` | With threads | Exchange of 80 000 elements while the queue grows, the order once the producers are done, waits, time limits and interrupts of the threads that take |
| `FairArrayBlockingQueueTest`, `FairArrayBlockingQueueStressTest` | All, with threads | The suites of a queue with a capacity, on a fair `ArrayBlockingQueue` |
| `ConcurrentLinkedDequeTest`, `PortableConcurrentDequeTest` | All | The suite of a deque on the queue that never waits. The port also runs 60 000 random operations against the `ArrayDeque` of the standard library, with phases that fill it and phases that empty it, so that its ring grows, wraps around and closes gaps from both sides |
| `ConcurrentLinkedQueueTest` | All | The public queue: order of arrival, 10 000 elements without a capacity, an element found and removed, bulk operations, an iterator that removes |
| `ConcurrentLinkedQueueStressTest`, `ConcurrentLinkedDequeAsQueueStressTest`, `PortableConcurrentDequeStressTest` | With threads | 4 producers and 4 consumers exchange 80 000 elements, each taken once and in the order of its producer. 8 threads that add and take lose nothing. An element is removed by one thread only. An iteration goes on while threads add and take |
| `ConcurrentLinkedDequeStressTest` | With threads | 8 threads at both ends of the deque, half of them from the head to the tail and the others the other way |
| `SingleThreadQueuesTest` | JS, Wasm | `take` on an empty queue and `put` on a full one fail, and a time limit answers at once |

### Locks

Each stress suite runs on the public class and on its port.

| Suites | Targets | What they check |
|---|---|---|
| `ReentrantLockTest`, `FairReentrantLockTest` | All | Reentrancy, `withLock`, the failure of an unlock without a hold, a free lock taken in every way, and whether it is fair. The second suite runs the first one on a fair lock |
| `ReentrantLockStressTest`, `PortableReentrantLockStressTest`, and both on a fair lock | With threads | Exclusion, 16 threads sent to sleep and woken up, blocking until release, `tryLock`, reentrant holds, unlock by another thread. A time limit that runs out or not, 8 threads of which half give up all the time. An interrupt that ends `lockInterruptibly` and a timed `tryLock`, and that `lock` ignores |
| `ReentrantReadWriteLockTest`, `FairReentrantReadWriteLockTest` | All | Its two locks through `ReadWriteLock`, the hold counts, a writer that keeps reading, a reader that cannot write, `read` and `write` with the read holds given back, free locks taken in every way, a timed `tryLock` of the write lock that gives up while reading, and whether it is fair |
| `ReentrantReadWriteLockStressTest`, `PortableReentrantReadWriteLockStressTest`, and both on a fair lock | With threads | Writers alone, readers together, readers that never see a write half done, a waiting writer that passes before new readers, a reader that reads again while a writer waits, hold counts per thread. A time limit on each lock, readers that pass a writer that gave up, threads that give up while others read and write, an interrupt that ends `lockInterruptibly` of each lock |
| `StampedLockTest`, `PortableStampedLockTest` | All, with threads | What one thread sees, without a wait: each mode and what it keeps out, a stamp that is valid until a write lock, stamps that fail to unlock what they are not for, releases without a stamp, 300 read holds, time limits, and every conversion between the three modes with the cases where it must fail |
| `StampedLockViewsTest` | All | The lock without its stamps: the views as `Lock` take and release the lock of their name and fail to release a lock that is not held, a view and a stamp release the same lock, the view as `ReadWriteLock` has both, `read` and `write` hold the lock for their block and release it when the block fails |
| `StampedLockStressTest`, `PortableStampedLockStressTest` | With threads | Writers alone, readers together, and optimistic readers that never validate a write half done, on two numbers that are far apart in memory. A lock released by another thread than its own, a writer that waits for the readers, readers that get in while a writer turns its lock into a read lock, 320 read holds at once, a waiting writer that passes before the readers that ask after it, 8 threads that mix every mode and conversion. A time limit on each lock, threads that give up while others read and write, an interrupt that ends the interruptible and the timed forms and that `writeLock` ignores. On the public lock, 8 threads that write and read through the views and through `read` and `write` exclude each other as with stamps |
| `PlatformStampedLockTest`, `PlatformStampedLockViewsTest`, `PlatformStampedLockStressTest` | Android | The public lock over the platform lock, as from API 24, and the port behind it on the host |
| `ConditionTest` | All | A condition fails without the lock, a signal without a waiting thread does nothing, a timed wait that nothing signals |
| `ConditionStressTest`, `PortableConditionStressTest`, and both on a fair lock | With threads | A wait gives up every hold and takes them back, `signal` wakes one thread and `signalAll` the others, 4 producers and 4 consumers exchange 80 000 items through a buffer of 4. A time limit that runs out or not, a signal that goes past a wait that ran out or was interrupted, a signal sent just as a wait runs out, a signal that puts a thread behind one that gave up on the lock, 10 000 signals handed one at a time to threads that give up all the time, an interrupt that ends a wait with the lock held again |
| `ReentrantLockFairnessTest`, `WriteLockFairnessTest`, `SemaphoreFairnessTest`, `ReadLockFairnessTest` and the four of their ports | JVM and Android for the classes of the JDK, with threads for the ports | The order of what is fair. 9 threads that wait acquire in the order of their arrival, whether they wait with or without a time limit. A thread that releases and asks again goes behind the one that waits. A reader waits behind a writer that is not first in the queue. No test relies on a delay: each one waits for the length of the queue, which the public classes do not tell where they wrap a port |
| `SynchronizerTest` | With threads | The three things that the locks do not show one case at a time. An acquisition that throws while its thread is first in the queue lets the next thread acquire. A thread that gives up in front of another one lets that one acquire as soon as it can. A signal that links a node that gave up links the thread behind in its place, which a release then wakes up. Three more check that link on plain nodes: left to a thread that has not skipped yet, past two nodes that gave up, and untouched when the node still waits |
| `SingleThreadLocksTest` | JS, Wasm | The waits that could never end fail |

### Synchronizers, threads and thread locals

| Suites | Targets | What they check |
|---|---|---|
| `SemaphoreTest`, `FairSemaphoreTest`, `CountDownLatchTest` | All | The count of permits, `withPermit`, a negative count, a latch that stays open, a time limit on one thread, and whether the semaphore is fair |
| `SemaphoreStressTest`, `PortableSemaphoreStressTest`, and both on a fair semaphore | With threads | Never more threads inside than permits, one permit excludes, blocking until a release, 16 waiting threads that each get their permit. A time limit that runs out or not, permits neither lost nor made when threads give up, permits released together that go past the threads that gave up, an interrupt that ends `acquire` |
| `CountDownLatchStressTest`, `PortableCountDownLatchStressTest` | With threads | Blocking until zero, 16 waiting threads released together, 8 threads that count down. A time limit that runs out or not, a gate that opens for the threads behind those that gave up, an interrupt that ends `await` |
| `CyclicBarrierTest` | All | A barrier of one party: it opens at once, runs its action each time, breaks when the action fails, and `reset` repairs it |
| `CyclicBarrierStressTest`, `PortableCyclicBarrierStressTest` | With threads | 8 threads that pass once the last one arrives, each with its arrival index, 2 000 rounds with an action that runs alone between two rounds. A time limit that runs out or not. What breaks a barrier, for the thread that causes it and for the ones that wait: a time limit, an interrupt during a wait or before it, an action that fails, a reset. No test relies on a delay: each one asks the barrier how many threads wait |
| `SingleThreadWaitsTest` | JS, Wasm | The waits that could never end fail, a stamped lock included, and a wait with a time limit at a barrier of several parties runs out of time |
| `ThreadTest` | With threads | A thread from its start to its end, `join` with and without a time limit, the name, the daemon flag, the builder, `sleep`, the interrupted flag, an interrupt that ends a sleep and a join, an exception that ends its thread alone |
| `ThreadLocalTest`, `ThreadLocalStressTest` | All, with threads | A value per thread and per thread local, `getOrSet` |
| `ThreadValuesTest` | Native | The table of a thread: 100 000 random operations against a `HashMap`, and the values of collected thread locals are dropped |

## Lincheck

[Lincheck](https://github.com/JetBrains/lincheck) checks the ports on the JVM, in 17 suites. Each
suite is model checked, which explores the interleavings of the threads one by one, and stress
tested. All of them are linearizable.

- **Map.** 12 operations in three settings: keys that share two list bins, a table that is one
  insertion away from its resize, and keys that share one tree bin. 2 threads, 5 operations per
  thread. The size, the iterations, `clear` and `putAll` are left out. They are not atomic by
  design, as in the JDK.
- **List.** 9 operations, the snapshot of the elements included. 3 threads, 3 operations per thread.
- **Lock.** A plain counter guarded by the lock, with reentrant holds. 3 threads, 3 operations per
  thread. A lost exclusion loses an increment, and a lost wake-up hangs.
- **Read-write lock.** The same counter, written under the write lock and read under the read lock,
  with reentrant holds and a writer that keeps reading. A reader that sees two values, or a writer
  that is not alone, fails the check.
- **Semaphore.** The same counter guarded by a semaphore of one permit.
- **Stamped lock.** The same counter, with 9 operations: a write, a read, an optimistic read that
  falls back on the read lock, the conversions from one mode to another, each with what it does when
  the conversion fails, and both timed tries. A stamp that is valid after a write fails the check.
- **Sorted map.** 12 operations on 5 keys: the updates of a key, the closest keys, the polls of both
  ends, the size of a part and the keys in order. 3 threads, 3 operations per thread. The reads of
  an entry take no lock, so this is where a read that trusts a table in the middle of a change would
  show. The polls of a view with bounds are left out. They find an entry, then remove it if it is
  still there, as in the JDK.
- **Queue that never waits.** 11 operations at both ends of the ring, with `remove` of an element,
  `contains`, the size, `clear` and the copy that an iterator goes through. 3 threads, 3 operations
  per thread.
- **Blocking queues.** A queue of two elements, so that it is often full and often empty, with 9
  operations: `offer`, `poll`, `peek`, `remove`, `contains`, the size, the remaining capacity,
  `clear` and `drainTo`. The deque adds 3 operations at its other end. The priority queue starts
  with room for one element and grows during the scenarios. 3 threads, 3 operations per thread.
  These scenarios go through the lock port and its conditions as well, since the queues take them.

The lock, the read-write lock, the stamped lock and the semaphore each have an operation that joins
the queue and gives up at once, since its time is over as soon as it has to wait. The three that
have a fair mode are checked twice, as they are by default and in that mode, where a thread looks at
the queue before it takes what is free.

Two things are left to the stress suites.

- **The waits for another thread.** The conditions, the latch, the barrier, and `put` and `take` of
  the queues wait for a thread that a random scenario cannot promise. Model checking also stops the
  clock, so a wait with a time limit never ends in it.
- **`drainTo` of `LinkedBlockingQueue`**, which is not atomic. It lowers the count after it moved
  the elements, so until it is done the size still counts them and an `offer` may find the queue
  full. It is the algorithm of JSR-166, and the class of the JDK does the same.

## Broken on purpose

Model checking explores a limited number of interleavings, and a test may pass for the wrong reason.
So the rules that the ports rely on were removed one at a time, to see a test fail. Each run had a
limit of time, so that a hang counts as a failure.

| What was removed | The test that fails |
|---|---|
| A thread that gives up wakes up the thread behind it | `SynchronizerTest`, in both modes |
| A thread skips a predecessor that gave up | `SynchronizerTest`, in both modes |
| A signal wakes up the thread that it puts behind one that gave up | `aSignalledThreadPassesOneThatGaveUpOnTheLock` of `PortableConditionStressTest` |
| A wait that gave up reports a signal that came first | `everySignalIsTakenByAThreadThatReportsIt` of `PortableConditionStressTest` |
| A signal that linked a node that gave up links the thread behind in its place | 2 tests of `SynchronizerTest` |
| That signal leaves the link to a thread that has not skipped yet | `aSignalLeavesItsLinkToAThreadThatHasNotSkippedYet` of `SynchronizerTest` |
| A barrier that breaks wakes up the threads that wait at it | 5 tests of `PortableCyclicBarrierStressTest`, the ones where a thread waits at a barrier that another thread breaks |

Two ports were broken more widely.

- **The sorted table, 20 changes.** With the lock: a search that stays in its chunk, a step back
  that lands on the first entry, `higher` that returns the key itself, a split that leaves a hole,
  an empty chunk that stays, a merge that drops the wrong chunk, a count that misses its last entry,
  a bound with its flag reversed, an iterator that repeats a key, and `put` then `remove` without
  the lock. In the reads without the lock: a read that is not checked, a change that is not counted,
  a change that is only counted once it is done, and six mistakes in what the search returns. Each
  fails between 1 and 12 tests. The three that let a reader trust a table in the middle of a change
  fail the Lincheck suite and the stress test of the keys that stay.
- **The stamped lock, 17 changes.** A writer that ignores the readers, a reader that enters a write,
  a write that leaves the state as it was, a stamp that is valid during a write, three releases that
  wake nobody up, readers that do not let a waiting writer pass, five mistakes in the count of the
  readers beyond what the state holds, a conversion to the write lock among other readers, a read
  lock released with an old stamp, and a read lock or an optimistic stamp given during a write. Each
  fails or hangs a test.

The tests of the fair order were checked the other way round. Run on locks and on a semaphore that
are not fair, 8 of the 14 fail, on the classes of the JDK as on the ports. The 6 others check the
order of threads that are all in the queue, which a lock that is not fair keeps too.

What a test cannot break on purpose is the order of the accesses to the memory, and a race whose
window is a few instructions wide. Programs written for the purpose showed those instead.

- **The fences of the stamped lock.** Two threads change two numbers that are far apart in memory
  under the write lock, and six threads read them optimistically. [Design](design.md#the-two-fences)
  tells what it showed.
- **The reads of the sorted table without the lock.** Half of the keys stay in the map, each mapped
  to itself. Up to four threads add and remove the keys between them, while the others look for the
  keys that stay, for their neighbours and for the value of each. On maps of 2 000 and of 100 000
  keys it made 280 million reads, of which none missed a key or found a wrong entry.
- **The signal that links a node which gave up.** That race is the third rule of
  [Leaving the queue](design.md#leaving-the-queue). It was run on purpose in a copy of the library,
  with two pauses that open its window: a thread waits with a time limit of 50 microseconds and
  another one signals around that time. Without the rule the waiting thread never came back, after
  12 to 28 waits. With it, it made 23 000 waits in each run.

## Repeated runs

A failure that shows once in many runs passes a single build. So the suites with threads were also
run in loops before the release, 12 times in a row on `macosArm64` and 4 to 8 times on the JVM. That
is about 20 000 test runs on native and 5 000 on the JVM, without a failure or a hang.
