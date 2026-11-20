# Migrating to Koncurrent

How to move code from `java.util.concurrent`, Stately or atomicfu to Koncurrent, and what
changes on the way. The [README](../README.md) tells how to add the dependency.

## From `java.util.concurrent`

Replace `java.util.concurrent` with `koncurrent` in the imports. `Thread`, `Runnable`,
`InterruptedException`, `IllegalThreadStateException` and `ThreadLocal` of `java.lang` become
those of `koncurrent`.

On the JVM the common class is the Java one, so the JVM code that already uses it keeps
working. Common code sees the members below, which is less than the Java class has.

| Class | In common code |
|---|---|
| `ConcurrentHashMap` | `MutableMap`, `putIfAbsent`, `remove(key, value)`, both `replace`, `computeIfAbsent`, `computeIfPresent`, `compute`, `merge` |
| `CopyOnWriteArrayList` | `MutableList`, `addIfAbsent`, `addAllAbsent` |
| `ReentrantLock` | `lock`, `lockInterruptibly`, `tryLock`, `unlock`, `newCondition`, `withLock` |
| `ReentrantReadWriteLock` | `readLock`, `writeLock`, `getReadHoldCount`, `getWriteHoldCount`, `read`, `write` |
| `Lock` | `lock`, `lockInterruptibly`, `tryLock`, `unlock`, `withLock` |
| `Condition` | `await`, `signal`, `signalAll` |
| `Semaphore` | `acquire`, `tryAcquire`, `release`, `availablePermits`, `withPermit` |
| `CountDownLatch` | `await`, `countDown`, `getCount` |
| `ThreadLocal` | `get`, `set`, `remove`, `getOrSet` |
| `Thread` | `start`, `join`, `interrupt`, `isInterrupted`, `isAlive`, `getName`, `setName`, `isDaemon`, `setDaemon` |

What differs from Java:

- **The computations take Kotlin functions.** `computeIfAbsent`, `computeIfPresent`, `compute`
  and `merge` are extensions in common code. Import them, for example
  `import koncurrent.computeIfAbsent`.
- **No constructor with a load factor or a concurrency level.** The map has `ConcurrentHashMap()`
  and `ConcurrentHashMap(initialCapacity)`.
- **`keys` is a `MutableSet` in common code.** Its Java type, `KeySetView`, cannot be named
  there.
- **`ConcurrentHashMap.newKeySet()` is `ConcurrentHashSet()`.**
- **A time limit is a `Duration`.** `tryLock(1.seconds)`, `await(1.seconds)`,
  `tryAcquire(1.seconds)` and `join(1.seconds)` are extensions in common code. Import them, for
  example `import koncurrent.locks.tryLock`. The Java functions that take a `long` and a
  `TimeUnit` only exist on the JVM and Android.
- **The static functions of `Thread` are top-level functions.** `Thread.currentThread()`,
  `Thread.interrupted()` and `Thread.sleep(millis)` become `currentThread()`, `interrupted()`
  and `sleep(duration)`, of `koncurrent`. `thread { }` creates and starts a thread, as the
  function of `kotlin.concurrent` does on the JVM.
- **The members of `Thread` keep their Java names in common code**, like `thread.getName()` and
  `thread.isAlive()`. The property syntax, `thread.name`, only works where the class is the
  Java one.
- **`Thread` does not exist on JS and Wasm.** A project that targets them uses it from a source
  set of the JVM, Android and native, not from `commonMain`.
- **`withLock`, `read` and `write` are those of this library in common code.** Import them from
  `koncurrent.locks`. They have the behavior of the ones of `kotlin.concurrent`, which only
  exist on the JVM.
- **The locks of a `ReentrantReadWriteLock` are typed as `Lock` in common code.** Their Java
  types, `ReadLock` and `WriteLock`, cannot be named there.

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
  collection, so it iterates in the order of insertion. `ConcurrentHashMap` and
  `ConcurrentHashSet` iterate in the order of their table. Where the order matters and updates
  are rare, use a `CopyOnWriteArrayList` with `addIfAbsent`.
- **There is no `block { }`.** A Stately collection runs a block under its single lock, which
  makes several calls atomic. The map here has no single lock. Use `compute` or `merge` for an
  atomic update of one key, and a `ReentrantLock` of your own to guard several calls.
- **`computeIfAbsent` stays atomic**, and it no longer stops the other threads. It only locks
  the bin of its key, and nothing when the key already has a value.
- **Iteration is weakly consistent.** An iterator never throws
  `ConcurrentModificationException`. It may or may not show the updates made after its
  creation.
- **A lock is not closed.** Remove the calls to `close`, and what guarded them. Unlocking a lock
  that the thread does not hold throws `IllegalMonitorStateException`.
- **`ConcurrentMutableList` has no direct equivalent.** `CopyOnWriteArrayList` copies its
  elements on every update. It is the right choice for a list that is read far more than it is
  changed. For a list that changes often, guard an `ArrayList` with a `ReentrantLock`.

## From atomicfu

| atomicfu | Koncurrent |
|---|---|
| `kotlinx.atomicfu.locks.ReentrantLock`, `reentrantLock()` | `koncurrent.locks.ReentrantLock()` |
| `kotlinx.atomicfu.locks.withLock` | `koncurrent.locks.withLock` |
| `SynchronizedObject`, `synchronized(lock) { }` | A `ReentrantLock` property and `withLock` |

The lock of Koncurrent is a class of its own on native. It is not a `SynchronizedObject`, so a
class cannot extend it to become its own lock. Keep the lock in a property.

## What to check after the move

- Replace `getOrPut` with `computeIfAbsent` where the value must be created once.
- A function given to `computeIfAbsent`, `compute`, `computeIfPresent` or `merge` must not
  update the same map.
- Copy a collection that other threads update with a loop, not with `toList`. See
  [What to know](design.md#what-to-know).
