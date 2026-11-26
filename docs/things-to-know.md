# Things to know

The limits and the pitfalls of Koncurrent. Most of them are those of the Java classes that the
library follows. The others come from the Kotlin standard library, or from a platform.

## Collections

- **`getOrPut` is not atomic.** It is the extension of the standard library, which reads then
  writes. Use `computeIfAbsent` where a value must be created once.
- **A computation must not update its map.** The function given to `computeIfAbsent`,
  `computeIfPresent`, `compute` or `merge` runs while a part of the map is locked. Keep it short,
  and do not touch the map from it. On the ports an update of the same part never returns. The JDK
  throws `IllegalStateException` when it detects one.
- **Copy a collection through its iterator when other threads update it.** `toList`, `toSet`,
  `sorted`, `first`, `ArrayList(collection)` and `addAll(collection)` of the standard library read
  the size, then the elements. They can throw or miss elements when the collection changes in
  between, on every platform. The JVM hides most of it, because its bulk copies go through the
  `toArray` of the Java classes. Native does not, and throws `NoSuchElementException`. `for`,
  `forEach`, `map` and `filter` use one iterator and are safe everywhere.
- **An iterator never fails, and promises little.** It may or may not show what changed since it was
  created. Only the iterators of `CopyOnWriteArrayList` and `CopyOnWriteArraySet` show the elements
  of one moment, the one of their creation. To take everything out of a blocking queue at once, use
  `drainTo`.
- **Give a large map its size.** A new map grows 17 times before it holds a million keys, which is
  where it is the slowest. `ConcurrentHashMap(1_000_000)` skips that.
  [Benchmarks](benchmarks.md#reading) tells what it costs.

## Locks

- **`lock()` ignores an interrupt**, as in Java. It keeps waiting, and the thread finds its flag set
  afterwards. `lockInterruptibly()` and `tryLock` with a time limit are the ones that an interrupt
  ends.
- **A fair lock is far slower when threads compete for it.** Every release then hands it over to a
  thread that sleeps. Ask for fairness where a thread must not wait forever, and nowhere else.
  [Benchmarks](benchmarks.md#locks) has the figures.
- **A read lock only pays when reading takes longer than taking a lock.** Readers do not wait for
  each other, but each one still writes the state of the lock. For a few instructions a
  `ReentrantLock` is faster. [Benchmarks](benchmarks.md#reading) tells more.
- **`StampedLock` is not reentrant.** A thread that holds it and asks for it again may wait for
  itself forever, even to read again. It has no owner either: any thread releases a lock with its
  stamp.
- **An optimistic read may see data half written.** Between `tryOptimisticRead` and `validate`, only
  copy what is read to local variables. Use the copies once `validate` returned `true`.

## Platforms

- **Android.** `ConcurrentHashMap` and `StampedLock` are classes of the library there, where the
  other classes are those of the platform. So an instance of one does not go where code expects the
  Java class of the same name. [Design](design.md#android) tells why.
- **Native.** A program ends when its main function returns, whether threads still run or not, and
  the daemon flag changes nothing. Join the threads that must finish. An interrupt does not wake a
  thread up from a call to the system, like a read of a file.
- **JS and Wasm.** They have one thread, so they have no `Thread`. The collections work as
  elsewhere, and a lock never waits. A wait that could never end fails with `IllegalStateException`:
  `await` on a condition, on a latch that is not open or at a barrier of several parties, `acquire`
  on a semaphore without a permit, taking the write lock while holding only the read lock, `take` on
  an empty queue, `put` on a full one, and `writeLock` or `readLock` on a stamped lock that is held
  in a mode that keeps them out. A wait with a time limit answers at once. Nothing interrupts the
  one thread.
- **Two families are only lock-free on the JVM and Android.** Elsewhere `ConcurrentLinkedQueue` and
  `ConcurrentLinkedDeque` are a ring of elements under one lock. `ConcurrentSkipListMap` and
  `ConcurrentSkipListSet` keep their entries in sorted chunks there: their readers do not wait for
  each other, but the threads that change them take turns, and an iteration takes the lock for a
  moment every 32 entries. Both families behave as the Java classes do.
  [Design](design.md#the-queues-that-never-wait) tells why they are built that way.
