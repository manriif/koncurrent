# Migrating to Koncurrent

How to move code from `java.util.concurrent`, Stately or atomicfu to Koncurrent. The
[README](../README.md) tells how to add the dependency.

Once the code is moved, read [Things to know](things-to-know.md). Three of its points bite code that
was written for a collection under one lock: `getOrPut` is not atomic, a computation must not update
its map, and `toList` is not a safe copy.

## From `java.util.concurrent`

The packages follow those of Java, so an import only changes its first part.

| Java | Koncurrent |
|---|---|
| `java.util.concurrent` | `koncurrent` |
| `java.util.concurrent.locks` | `koncurrent.locks` |
| `Thread`, `Runnable`, `ThreadLocal`, `InterruptedException` and `IllegalThreadStateException`, of `java.lang` | `koncurrent` |
| `IllegalMonitorStateException`, of `java.lang` | `koncurrent.locks` |
| `Queue`, `Deque` and `NavigableSet`, of `java.util` | `koncurrent` |

On the JVM and Android a common class is the Java one, so the code that already uses it keeps
working. Common code sees the members below, which is less than the Java class has.

| Class | In common code |
|---|---|
| `ConcurrentHashMap` | `MutableMap`, `putIfAbsent`, `remove(key, value)`, both `replace`, `computeIfAbsent`, `computeIfPresent`, `compute`, `merge` |
| `ConcurrentNavigableMap` | `MutableMap`, `putIfAbsent`, `remove(key, value)`, both `replace`, the first and the last key and entry, the closest keys and entries (`lower`, `floor`, `ceiling`, `higher`), `pollFirstEntry`, `pollLastEntry`, `subMap`, `headMap`, `tailMap`, `descendingMap`, `navigableKeySet`, `descendingKeySet`, `comparator` |
| `NavigableSet` | `MutableSet`, `first`, `last`, `lower`, `floor`, `ceiling`, `higher`, `pollFirst`, `pollLast`, `subSet`, `headSet`, `tailSet`, `descendingSet`, `descendingIterator`, `comparator` |
| `ConcurrentSkipListMap` | `ConcurrentNavigableMap`, empty, with a comparator, or from a map |
| `ConcurrentSkipListSet` | `NavigableSet`, empty, with a comparator, or from a collection |
| `CopyOnWriteArrayList` | `MutableList`, `addIfAbsent`, `addAllAbsent` |
| `CopyOnWriteArraySet` | `MutableSet`, empty or from a collection |
| `Queue` | `MutableCollection`, `offer`, `poll`, `remove`, `peek`, `element` |
| `Deque` | `Queue`, and for each end `add`, `offer`, `remove`, `poll`, `get`, `peek`. `push`, `pop`, `descendingIterator` |
| `BlockingQueue` | `Queue`, `put`, `take`, `offer` and `poll` with a time limit, `remainingCapacity`, `drainTo` |
| `BlockingDeque` | `BlockingQueue`, `Deque`, and for each end `put`, `take`, `offer` and `poll` with a time limit |
| `LinkedBlockingQueue`, `LinkedBlockingDeque` | Their interface, with or without a capacity, or from a collection |
| `ArrayBlockingQueue` | `BlockingQueue`, with a capacity, fair or not, or from a collection |
| `PriorityBlockingQueue` | `BlockingQueue`, with or without an initial capacity and a comparator, or from a collection |
| `ConcurrentLinkedQueue` | `Queue`, empty or from a collection |
| `ConcurrentLinkedDeque` | `Deque`, empty or from a collection |
| `ReentrantLock` | `lock`, `lockInterruptibly`, `tryLock`, `unlock`, `newCondition`, `isFair`, `withLock`, fair or not |
| `ReentrantReadWriteLock` | `readLock`, `writeLock`, `getReadHoldCount`, `getWriteHoldCount`, `isFair`, `read`, `write`, fair or not |
| `StampedLock` | `writeLock`, `readLock`, their `try` and interruptible forms, `tryOptimisticRead`, `validate`, `unlockWrite`, `unlockRead`, `unlock`, the three `tryConvertTo`, `tryUnlockWrite`, `tryUnlockRead`, `isWriteLocked`, `isReadLocked`, `getReadLockCount`, `asReadLock`, `asWriteLock`, `asReadWriteLock`, `read`, `write` |
| `Lock` | `lock`, `lockInterruptibly`, `tryLock`, `unlock`, `withLock` |
| `ReadWriteLock` | `readLock`, `writeLock` |
| `Condition` | `await`, `signal`, `signalAll` |
| `Semaphore` | `acquire`, `tryAcquire`, `release`, `availablePermits`, `isFair`, `withPermit`, fair or not |
| `CountDownLatch` | `await`, `countDown`, `getCount` |
| `CyclicBarrier` | `await`, `reset`, `isBroken`, `getParties`, `getNumberWaiting`, with or without an action |
| `ThreadLocal` | `get`, `set`, `remove`, `getOrSet` |
| `Thread` | `start`, `join`, `interrupt`, `isInterrupted`, `isAlive`, `getName`, `setName`, `isDaemon`, `setDaemon` |

### What differs from Java

- **The computations take Kotlin functions.** `computeIfAbsent`, `computeIfPresent`, `compute` and
  `merge` are extensions in common code. Import them, for example
  `import koncurrent.computeIfAbsent`.
- **A time limit is a `Duration`.** `tryLock(1.seconds)`, `await(1.seconds)`,
  `tryAcquire(1.seconds)`, `join(1.seconds)`, `offer(element, 1.seconds)`, `poll(1.seconds)` and the
  timed tries of `StampedLock` are extensions in common code. Import them, for example
  `import koncurrent.locks.tryLock`. `await` of a `CyclicBarrier` is one too, and throws
  `TimeoutException` as in Java. The Java functions that take a `long` and a `TimeUnit` only exist
  on the JVM and Android.
- **`null` is not a key, a value or an element** of the maps and of the queues, by `K : Any`,
  `V : Any` and `E : Any`. The Java classes throw on `null`.
- **`keys` of the map is a `MutableSet` in common code.** Its Java type, `KeySetView`, cannot be
  named there.
- **`ConcurrentHashMap.newKeySet()` is `ConcurrentHashSet()`.**
- **The locks of a `ReentrantReadWriteLock` are typed as `Lock` in common code.** Their Java types,
  `ReadLock` and `WriteLock`, cannot be named there.
- **`withLock`, `read` and `write` are those of this library in common code.** Import them from
  `koncurrent.locks`. Those of `Lock` and `ReentrantReadWriteLock` have the behavior of the ones of
  `kotlin.concurrent`, which only exist on the JVM. Those of `StampedLock` are additions of this
  library.
- **The sorted views that take one key less are members of the map, not of every sorted set.**
  `subMap(from, to)`, `headMap(to)` and `tailMap(from)` are on `ConcurrentNavigableMap`.
  `subSet(from, to)`, `headSet(to)` and `tailSet(from)` are on `ConcurrentSkipListSet` only, since
  Java declares them on `SortedSet` with another return type. A `NavigableSet` has the forms that
  tell whether each bound is included.
- **An entry of the sorted map cannot change it.** `setValue` throws on every platform. Use `put` or
  `replace`.
- **The static functions of `Thread` are top-level functions.** `Thread.currentThread()`,
  `Thread.interrupted()` and `Thread.sleep(millis)` become `currentThread()`, `interrupted()` and
  `sleep(duration)`, of `koncurrent`. `thread { }` creates and starts a thread, as the function of
  `kotlin.concurrent` does on the JVM.
- **The members of `Thread` keep their Java names in common code**, like `thread.getName()` and
  `thread.isAlive()`. The property syntax, `thread.name`, only works where the class is the Java
  one.

[Design](design.md#the-common-declarations) tells where these differences come from.

### What is not there

These are left out of common code. Code written for the JVM alone still finds them on the Java
classes.

- **Of the map**: the constructors with a load factor or a concurrency level, the copy constructor,
  the parallel bulk operations (`forEach`, `search` and `reduce` with a parallelism threshold),
  `mappingCount` and `keySet(mappedValue)`.
- **Of the sorted map**: `compute`, `computeIfAbsent`, `computeIfPresent` and `merge`. Java does not
  make them atomic for it. Use `putIfAbsent` and `replace`, which are.
- **Of a deque**: `removeFirstOccurrence` and `removeLastOccurrence`, whose Java parameter is an
  `Object`. `remove(element)` removes the first occurrence.
- **Of the locks**: a condition on the write lock, permits taken several at a time, the waits that
  ignore an interrupt, `awaitUninterruptibly` and `acquireUninterruptibly`, and the static functions
  of `StampedLock` that tell the kind of a stamp.
- **Of `Thread`**: its priority, its identifier, its state, its handler of uncaught exceptions, and
  subclasses in common code.
- **Executors**, which kotlinx.coroutines covers.
- **`Phaser`, `Exchanger`, `SynchronousQueue`, `LinkedTransferQueue` and `DelayQueue`.**

## From Stately

| Stately | Koncurrent |
|---|---|
| `ConcurrentMutableMap` | `ConcurrentHashMap` |
| `ConcurrentMutableSet` | `ConcurrentHashSet` |
| `ConcurrentMutableList` | `CopyOnWriteArrayList`, when the list is mostly read |
| `Lock`, `withLock` | `locks.ReentrantLock`, `locks.withLock` |
| `Lock.close()` | Nothing. A lock has no native resource. |
| `ThreadLocalRef` | `ThreadLocal` |
| `AtomicInt`, `AtomicLong`, `AtomicBoolean`, `AtomicReference` | `kotlin.concurrent.atomics` of the standard library |

What differs from Stately:

- **Keys, values and set elements are not null.** `ConcurrentHashMap<K : Any, V : Any>` rejects
  nullable types at compile time. Model an absent value with a sealed type or a sentinel.
- **Iteration does not follow the order of insertion.** A Stately map or set wraps a linked
  collection, so it iterates in the order of insertion. `ConcurrentHashMap` and `ConcurrentHashSet`
  iterate in the order of their table. Where the order matters and updates are rare, use a
  `CopyOnWriteArrayList` with `addIfAbsent`.
- **There is no `block { }`.** A Stately collection runs a block under its single lock, which makes
  several calls atomic. The map here has no single lock. Use `compute` or `merge` for an atomic
  update of one key, and a `ReentrantLock` of your own to guard several calls.
- **`computeIfAbsent` stays atomic**, and it no longer stops the other threads. It only locks the
  bin of its key, and nothing when the key already has a value.
- **A lock is not closed.** Remove the calls to `close`, and what guarded them. Unlocking a lock
  that the thread does not hold throws `IllegalMonitorStateException`.
- **`ConcurrentMutableList` has no direct equivalent.** `CopyOnWriteArrayList` copies its elements
  on every update. It is the right choice for a list that is read far more than it is changed. For a
  list that changes often, guard an `ArrayList` with a `ReentrantLock`.

## From atomicfu

| atomicfu | Koncurrent |
|---|---|
| `kotlinx.atomicfu.locks.ReentrantLock`, `reentrantLock()` | `koncurrent.locks.ReentrantLock()` |
| `kotlinx.atomicfu.locks.withLock` | `koncurrent.locks.withLock` |
| `SynchronizedObject`, `synchronized(lock) { }` | A `ReentrantLock` property and `withLock` |

The lock of Koncurrent is a class of its own on native. It is not a `SynchronizedObject`, so a class
cannot extend it to become its own lock. Keep the lock in a property.
