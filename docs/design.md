# Design

How Koncurrent is built, and why. The [README](../README.md) tells how to use it.

Koncurrent gives Kotlin Multiplatform `Thread` and the classes of `java.util.concurrent`. On the
JVM and Android they are the Java classes themselves, through `actual typealias`. Elsewhere they
are ports of the JSR-166 sources of Doug Lea, which are in the public domain (CC0), or code
written for this library.

| | JVM | Android | Native | JS, wasmJs, wasmWasi |
|---|---|---|---|---|
| `ConcurrentHashMap` | Java class | Java class from API 24, the port below | Port of JSR-166 | Same port |
| `ConcurrentHashSet` | Key set of the map | Same | Same | Same |
| `CopyOnWriteArrayList` | Java class | Java class | Written for the library | Same |
| `ReentrantLock`, `ReentrantReadWriteLock`, `Condition` | Java classes | Java classes | Ports of JSR-166, sleep on pthread | Count their holds |
| `Semaphore`, `CountDownLatch` | Java classes | Java classes | Ports of JSR-166, sleep on pthread | Plain counts |
| `ThreadLocal` | Java class | Java class | A table of values per thread | A plain value |
| `Thread` | Java class | Java class | A thread of the system, on pthread | None |


## Packages

The packages follow those of Java, so that an import is found where a Java developer looks for
it.

| Koncurrent | Java |
|---|---|
| `koncurrent` | `java.util.concurrent` |
| `koncurrent.locks` | `java.util.concurrent.locks` |

`Thread`, `Runnable`, `InterruptedException`, `IllegalThreadStateException` and `ThreadLocal`
are in `java.lang`, which has no counterpart here. They sit in `koncurrent`. `ConcurrentHashSet`
has no Java class. It stands for `ConcurrentHashMap.newKeySet()`.

Everything else is in `koncurrent.internal`, and is internal.

## The shape that the Java classes impose

A common declaration must match the Java class that it is on the JVM. That decides a few things.

- **No default argument.** `ConcurrentHashMap()` and `ConcurrentHashMap(initialCapacity)` are two
  constructors. A Java class has no default value to match.
- **The computations of the map are extensions.** `computeIfAbsent`, `computeIfPresent`,
  `compute` and `merge` take Kotlin functions. The Java members take `java.util.function` types,
  so they cannot be the expected members. Each one runs its function at most once per call,
  atomically for the key. `putIfAbsent`, `remove(key, value)` and both `replace` are members.
- **`keys` has its own type.** Java declares that `keySet()` returns a `KeySetView`, so the
  common map declares `ConcurrentHashMap.KeySetView` too. Common code cannot name it, because it
  is nested in a typealias on the JVM. Type it as a `MutableSet`.
- **Keys and values are not null**, by `K : Any` and `V : Any`. The Java class throws on `null`.
- **The exceptions are declared too.** Unlocking a lock that the thread does not hold throws
  `IllegalMonitorStateException`, an interrupted wait throws `InterruptedException`, and
  starting a thread twice throws `IllegalThreadStateException`. They are the Java classes on
  the JVM and Android, and classes of the library elsewhere. So code catches the same type on
  every target.
- **A time limit is a `Duration`, given to an extension.** The Java functions take a `long` and
  a `TimeUnit`, so they cannot be the expected members. `tryLock`, `await`, `tryAcquire` and
  `join` each have an extension that takes a `Duration`.
- **The static functions of `Thread` are top-level functions.** A typealias brings no static
  function to common code. So `currentThread`, `interrupted` and `sleep` stand on their own.
- **The members keep their Java names**, like `getName()` and `isAlive()`. A common declaration
  cannot be a property where the Java class has a function.

## Android

The platform map has no `computeIfAbsent`, `compute` or `merge` below API 24. So on Android the
map is a class that wraps the platform map from API 24, and the port below. It picks one when it
is created, from `Build.VERSION.SDK_INT`. An application whose minimum API is 24 or more does
not need the port. R8 should remove it there, which was not verified. The library declares API
21 as its minimum.

On Android the computations are also members of the map. A map is a `java.util.Map` there, and
inherits `computeIfAbsent`, `compute` and `merge` from it. Those take Java functional interfaces.
They are not atomic, and they do not exist below API 24. A Kotlin lambda selects the members of
the library, which `ConcurrentHashMapBindingTest` checks for both backends. A call that passes a
`java.util.function` object still reaches the Java ones.

The other classes exist since API 1, so they are the Java ones on every Android version.

## Where the ports live

The ports are internal classes of `commonMain` and `concurrentMain`, in `koncurrent.internal`.
The public class of a platform without `java.util.concurrent` wraps one.

- They are compiled for the JVM too, where nothing uses them. This is what lets Lincheck and
  the JVM stress tests check them. It makes the JVM jar about 150 KB.
- A typealias cannot point to an internal class. Hence the wrappers. The frequent operations of
  a port are inline functions, which the wrapper inlines in its own. So a lookup through the
  public map costs the same as on the port, 6.8 ns against 6.7 ns through `MutableMap`.
- The JS and Wasm targets use the map and list ports as they are. Their iterators keep working
  while the collection changes, which a standard `HashMap` does not allow.

The source sets follow the platforms: `java` for the typealiases, `concurrent` for what
needs threads, which is the JVM, Android and native, `nonConcurrent` for JS, wasmJs and
wasmWasi, `nonJava` for the wrappers of native and of the targets without threads, and `unix`
for the native targets whose pthread types are structures.

## What makes code fast on Kotlin/Native

One finding drove most of the optimizations. Kotlin/Native gives each function a frame for its
references, which the garbage collector reads. It sets the frame up on entry, after a lookup of
the thread. A function that LLVM inlines keeps its frame. A function that Kotlin inlines does
not, since it is gone before the frames are laid out.

So the small functions of the hot paths are `inline`, with the warning about it suppressed for
the file. Inlining three of them made lookups 25% faster. The figures of each change are in
[Benchmarks](benchmarks.md#what-the-optimizations-brought).

The finding has another side. The references of a function that Kotlin inlines join the frame
of its caller, and that frame is cleared on entry, whether the call uses them or not. The queue
of the locks is such a function, with more than thirty references. Next to the first try of a
lock, it had every call clear 300 bytes. So a lock keeps its wait in a regular function, whose
frame is only set up for a thread that waits. A lock taken at its first try then clears 8
bytes, which made the read-write lock 19% faster with one thread. The JIT of the JVM prefers
the wait in place, by 8% to 13%, but these ports only run on native.

A file that declares a thread local costs its callers too. Kotlin/Native initializes such a
property for each thread, so an access to the file from another file starts with a lookup of
the thread. The check for an interrupt, which every wait makes, reads a counter that is kept
in a file without a thread local.

## The map

The map port is `PortableConcurrentHashMap`, about 2400 lines.

**The algorithm is the original one.** An empty bin takes its first node with a CAS. Any other
update locks the bin. A resize is cooperative. It leaves forwarding nodes, stamps the size
control and hands ranges of bins to the threads that help. The count is striped. The views are
weakly consistent, through a port of the `Traverser`. Tree bins are kept, with their read-write
lock.

**Types replace the encoding of the original.** It marks its special nodes with negative hashes
and `null` keys. Here `Bin` is a sealed interface (list node, tree bin, forwarding node,
reservation node) and `Node` is a sealed class (list node, tree node). Every dispatch is an
exhaustive `when`, with no cast.

**The bin lock is a lock word for each bin.** The original locks the monitor of the first node
of a bin. Kotlin atomics have no field updater, so a node cannot hold a lock word that a CAS
can reach. The lock is one `Int` for each bin, in an `AtomicIntArray` beside the bins. Locking
is a CAS from 0 to 1. It costs 4 bytes per bin and nothing to allocate or close. A thread that
finds it taken checks it 128 times, since a bin is held for a few nanoseconds. Then it gives
way to the other threads between two checks.

Its limits:

- It is not reentrant. A function given to a computation must not update the map. An update of
  the same bin never returns. The JDK throws `IllegalStateException` when it detects one.
- An update writes two cache lines, the lock word and the node. The JDK writes one, because its
  lock is in the header of the node.

Other differences with the original:

- **Lookup before `computeIfAbsent` and `putIfAbsent`.** Both first look the key up without the
  lock, through the whole bin. The original only looks at the first node that way. So threads
  that ask for the same present key never wait for each other.
- **Size counter.** It is striped as in the original: a base count, then 16 cells once threads
  contend, 128 bytes apart in one `AtomicLongArray`. A thread adds to the cell of its
  identifier. On native the identifiers count up, so the first 16 threads never share a cell.
  The original also moves a thread to another cell when it contends.
- **Resize ranges.** The original sizes them from the number of processors, which common Kotlin
  does not give. A resize is split in 128 ranges of at least 16 bins.
- **Tree bins.** A writer spins while the running lookups end, where the original parks. Keys of
  one `Comparable` class are ordered by `compareTo`. The other ties go left, where the original
  compares class names and identity hashes. `root`, `left` and `right` are volatile, because
  Kotlin/Native gives no meaning to a racy read.
- **Traversal.** Outside of a resize an iterator visits the bins in order, without the
  bookkeeping that the original keeps for the tables it left.
- **Views.** The port and its views extend the standard `AbstractMutableMap`,
  `AbstractMutableSet` and `AbstractMutableCollection`. `keys.add` throws, as for the Java map.

Left out of the map: the parallel bulk operations (`forEach`, `search` and `reduce` with a
parallelism threshold), `mappingCount`, `keySet(mappedValue)`, the load factor and concurrency
level parameters, the copy constructor and the detection of recursive updates. On the JVM the
Java class has them all.

## The locks

A lock that owns a pthread mutex must destroy it, and destroying it while a thread uses it is
undefined. The locks here own no native resource, so they have no `close`.

### The queue

The locks are built on a port of the `AbstractQueuedSynchronizer` of JSR-166: an atomic state,
and a queue of the waiting threads. A thread that cannot acquire enqueues itself and sleeps. A
release wakes up the first thread of the queue. The port has the exclusive mode, the shared
mode, the conditions, the timeouts and the interruption of the original.

It differs from the original in five ways.

- **A lock passes the function that tries to acquire.** The original calls abstract functions,
  `tryAcquire` and `tryAcquireShared`, and throws for the mode that a lock does not implement.
  Here the queue is an inline function that takes the try as a lambda. A mode that a lock does
  not have cannot be called, and the try is not a virtual call.
- **A thread may try 16 times before it joins the queue**, giving way to the other threads
  between two tries. Sleeping and being woken up costs far more than a short wait. The original
  never waits that way, and neither does the `ReentrantLock` of the JDK, which is why both do
  badly when two threads fight for one lock.
- **The head of the queue is a plain volatile field**, set after the tail when the queue is
  created. It is one object less per lock, and one read less per release.
- **A try that throws leaves the queue cleanly.** The original cancels the node. Here the first
  thread of the queue leaves it as a thread that acquired does, and wakes up the next one.
- **A thread that gives up leaves its node behind.** The next section tells how.

### Leaving the queue

A thread gives up when its time runs out, or when it is interrupted in a wait that an interrupt
ends. The original then unlinks its node with atomic updates of the links of the neighbours.
The fields of a Kotlin class have no such update, and an atomic object for each link would cost
every waiting thread three more allocations.

So the node stays in the queue. The thread marks it as cancelled, in a volatile field of its
own, and wakes up the first thread behind it that did not give up too. Two rules make it safe.

- **A thread never sleeps behind a cancelled node.** It reads the mark of its predecessor before
  each sleep. When the mark is set, it links itself to the node before, and looks again. The
  thread that cancels writes its mark, then reads its link to the next node. The thread behind
  writes that link when it enqueues, then reads the mark. So one of the two sees the other. Either
  the thread that cancels finds its successor and wakes it up, or the successor finds the mark.
- **A wake-up is never lost in a cancelled node.** The thread that gives up may have been woken
  up to acquire, just before. Waking up its successor passes that on. The successor skips the
  node, finds itself first, and tries.

One thread does not enqueue itself: the one that a signal moves from a condition to the queue
of the lock. It sleeps, and cannot read the mark of the node that it is put behind. So the
thread that signals reads it in its place, and wakes the sleeping thread up when the mark is
set. The original does the same.

No update of a link can be lost. Only the thread of a node writes its link to its predecessor.
And only that thread writes the link from its new predecessor back to it, since it is the one
thread right behind. A cancelled node never becomes the head, so it always keeps a predecessor
to go back to.

The cost is one volatile field in each node, and one read of it before a sleep. A release does
not look for cancelled nodes at all.

**Sleeping is per thread, not per lock.** Each thread owns one pthread mutex and condition,
freed after the thread ends. A wake-up is first an atomic exchange. It only calls pthread when
the thread really sleeps. This is the only native memory of the library.

A sleep with a time limit counts that time on a clock that the system cannot move. Apple and
Windows take the time to wait. Linux and Android take the moment to wake up, on the monotonic
clock that the condition is created with.

### `ReentrantLock`

It is the non-fair lock of JSR-166. A free lock is taken with one CAS, even when threads wait.

- **The holds of the owner are counted in a plain field**, which only the owner accesses. The
  original counts them in the atomic state. This spares two atomic accesses per hold.
- **The owner is a thread identifier.** On native it is the identifier of the worker that
  Kotlin gives to every thread, which costs 0.1 ns to read. A thread local property costs
  0.9 ns.

### `ReentrantReadWriteLock`

It is the non-fair lock of JSR-166. The state counts the write holds in its low half and the
read holds in its high half. A reader adds to the count with a CAS, and never sleeps unless a
thread writes.

Readers that follow each other could keep a writer out forever. The rule of the original is
kept: a thread that does not read yet lets the first thread of the queue pass when it waits to
write. A thread that already reads goes on, since that writer waits for it.

The read lock is reentrant, so each thread counts its holds. The first reader has its count in
the lock. The others have theirs in a thread local.

Two things differ from the original. Both were measured with several readers on macosArm64.

- **The last reader is not cached.** The original keeps the count of the last reader in a field
  of the lock, to spare a lookup of the thread local. Readers then write that field in turn,
  which costs them more than the lookup does.
- **A read hold is released with one atomic addition.** The original retries an atomic update
  until it succeeds.

With 8 readers the read lock went from 7.0 to 22.5 million pairs of lock and unlock per second.

Every reader still updates the state of the lock, so readers slow each other down when they
hold the lock for a very short time. This lock pays off when reading takes longer than taking
a lock. For a few instructions, a `ReentrantLock` is faster.

`read` and `write` behave as the functions of `kotlin.concurrent` on the JVM. `write` releases
the read holds of its caller first and takes them back after, because a reader cannot become a
writer. `getReadHoldCount` and `getWriteHoldCount` keep their Java names for that: they are
the members that `write` needs, and the Java class already has them.

The write lock has no condition in common code. The one of the JDK has.

### `Condition`

It is the `ConditionObject` of JSR-166. A thread that waits is kept in a list of the condition.
A signal moves the first one to the queue of the lock, where it waits to acquire again.

A thread may give its wait up, on a timeout or an interrupt, at the very moment when a signal
moves its node to the queue of the lock. The original settles which of the two came first with
an atomic update of the status of the node. Here the thread cancels its node, takes the lock
again from a new node, and reads the status once it holds the lock. A signal is always given
under that lock, so the status then tells whether one moved the node.

- If a signal did, the wait counts as signalled. The moved node is skipped in the queue of the
  lock, as any cancelled node.
- If none did, the thread takes its node out of the list, and reports the timeout or the
  interrupt. A signal that finds a cancelled node first drops it and goes to the next one, so a
  signal is never spent on a thread that gave up.

### `Semaphore` and `CountDownLatch`

They are the non-fair `Semaphore` and the `CountDownLatch` of JSR-166, on the shared mode of
the queue. Their state is the number of permits, and the count. A thread that acquires from the
queue wakes up the next one, so that permits released together, or a latch that opens, let
every waiting thread through.

They are in `koncurrent`, as they are in `java.util.concurrent`. `withPermit` has the name of
the function of kotlinx.coroutines for its own semaphore.

### `Lock`

`Lock` and `Condition` are interfaces, since they are the Java interfaces on the JVM. The Java
ones have more functions, so a class of common code that implements them does not compile for
the JVM. They are not meant to be implemented, and the compiler says so. A class that implements
one needs an opt-in to `InternalForInheritanceKoncurrentApi`, in common code and on every target
without Java. That leaves room to add functions to them later. Code written for the JVM or
Android alone sees the Java interfaces, which need no opt-in.

On the targets without Java each interface has one more member than in common code: `tryLock`
and `await` with a time limit. The common extension of the same name calls it there, where the
JVM calls the method of the Java interface. So a time limit reaches the lock without a cast. An
interface cannot have an internal member, so this one is public. It has the name and the
behavior of the extension, so users see one function. An interface that the JVM replaces with a
Java one cannot be sealed, which would have been the other way.

A common code cannot name `ReentrantReadWriteLock.ReadLock`, as for the key set of the map.
`Lock` is what the two locks are declared as.

### One thread

On JS and Wasm a lock never waits. It still counts its holds, so that an `unlock` without a
`lock` fails there as it does elsewhere. What would wait forever with threads fails with
`IllegalStateException`: `await` on a condition, which no other thread could signal, the write
lock asked by a thread that only reads, `acquire` on a semaphore without a permit, and `await`
on a latch whose count is not zero.

A wait with a time limit would end, so it does not fail. It answers at once what it would
answer when its time is over, since waiting could change nothing. Nothing can interrupt the one
thread, so `InterruptedException` is never thrown there.

## The threads

`Thread` exists where threads do: on the JVM, Android and native. It is declared in the
`concurrent` source set, so code that also targets JS or Wasm cannot name it by mistake.

On native a thread is a pthread, created detached. It frees what it holds when it ends, and
`join` waits on a latch of the library that the thread opens last.

- **The current thread.** Each thread keeps its `Thread` in a thread local. A thread that the
  library did not start, like the main one, gets a `Thread` the first time it asks. Nothing
  tells when such a thread ends, so it stays alive for the library.
- **Interruption.** `interrupt` sets a flag of the thread and wakes it up. A wait that an
  interrupt ends reads the flag when it starts and each time it wakes up. A counter of the
  interrupted threads spares that read: it is zero nearly always, and reading it costs less
  than finding the current thread.
- **Sleeping.** `sleep` is a timed wait on what the thread sleeps on in a lock. So an interrupt
  ends it as it ends any other wait.
- **Daemons.** A native program ends when its main function returns, whatever its threads do.
  The daemon flag is kept, and changes nothing.
- **Uncaught exceptions.** An exception that escapes a thread ends that thread, which prints the
  stack trace. The JVM does the same by default.

## The thread local

On native a thread keeps the values of all the thread locals in one table, which goes away with
the thread. It is a hash table with open addressing, written for this use. The table does not
reference the thread locals. A collected thread local marks its key dead through a cleaner, and
each thread drops the values of dead keys when its table fills up. So neither a thread local
nor its values leak. `get` returns `null` until a value is set.

`getOrSet` has the name and the behavior of the function that the Kotlin standard library has
for the JVM.

## The list

`CopyOnWriteArrayList` was written for the library. The JSR-166 file carries a Sun copyright, so
it is not a port. It keeps an array that is never modified, in an atomic reference. A read
loads it. An update copies it, changes the copy and installs it with a CAS, and starts over if
another update came first. The Java class guards its updates with a lock instead.

The array is created as an array of anything, as the standard collections create theirs. Two
functions hold the casts that this takes.

The behavior is the one of the Java class, which the same tests check on both. An iterator goes
through the elements of its creation and cannot change the list. A `subList` is a view that
updates the list, and fails with `ConcurrentModificationException` once the list changed
otherwise.

## What to know

- **Copy a collection through its iterator when other threads update it.** `toList`, `toSet`,
  `sorted`, `first`, `ArrayList(collection)` and `addAll(collection)` of the standard library
  read the size, then the elements. They can throw or miss elements when another thread updates
  the collection in between, on every platform. The JVM hides most of it, because its bulk
  copies go through the `toArray` of the Java classes. Native does not, and throws
  `NoSuchElementException`. `for`, `forEach`, `map` and `filter` use one iterator and are safe
  everywhere.
- **`getOrPut` is the standard extension and is not atomic.** Use `computeIfAbsent`.
- **A computation must not update its map**, on any platform.

## What was tried and dropped

Each of these was measured on macosArm64 and did not pay.

- **Lock words further apart.** Neighbouring bins share the cache line of their lock words. One
  word per line changed nothing at 8 threads beyond noise, and slowed inserts on one thread by
  8% to 47%, as the table grows with it.
- **Fewer lock words on a large table**, shared by the bins. No gain beyond noise. The processor
  fetches the lock word and the bin at once.
- **A lookup without the lock before `remove` and `replace`.** It made removals of absent keys
  3.8 times faster at 8 threads, and removals of present keys 21% slower. A key is usually
  present when it is removed.
- **Keeping the slow path of the lock out of line** with `kotlin.native.NoInline`. 1% to 3%,
  within noise, for three more files.
- **No copy of a new table.** `AtomicArray` copies the array that it is given, which costs 5
  times a plain array, 1.4 ms per million slots. Nothing public on Kotlin/Native avoids it.

## What Kotlin atomics lack

- A field updater. A node cannot carry its lock, hence the array of locks.
- A way to create an `AtomicArray` without copying an array. Each new table is allocated twice.
- A thread identity and a yield in common code. The library has its own, internal.

## Left out

- **Fair locks.**
- **`StampedLock`**, conditions on the write lock, and permits taken several at a time.
- **The waits that ignore an interrupt**, `awaitUninterruptibly` and `acquireUninterruptibly`.
- **Of `Thread`**: its priority, its identifier, its state, its handler of uncaught exceptions,
  and subclasses in common code. Java deprecated `getId()` for a function that Java 17 and
  Android below API 36 do not have.
- **Executors.** kotlinx.coroutines covers them.
- **`CyclicBarrier`, `Phaser` and `Exchanger`.**
- **`ConcurrentLinkedQueue`**, whose source in JSR-166 is in the public domain.
