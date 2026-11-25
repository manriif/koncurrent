# Design

How Koncurrent is built, and why. The [README](../README.md) tells how to use it, and
[Things to know](things-to-know.md) what to keep in mind when using it.

Koncurrent gives Kotlin Multiplatform `Thread` and the classes of `java.util.concurrent`. On the JVM
and Android they are the Java classes themselves, through `actual typealias`. Elsewhere they are
ports of the JSR-166 sources of Doug Lea, which are in the public domain (CC0), or code written for
this library.

| | JVM | Android | Native | JS, wasmJs, wasmWasi |
|---|---|---|---|---|
| `ConcurrentHashMap` | Java class | Java class from API 24, the port below | Port of JSR-166 | Same port |
| `ConcurrentHashSet` | Key set of the map | Same | Same | Same |
| `ConcurrentSkipListMap`, `ConcurrentSkipListSet` | Java classes | Java classes | Sorted chunks of entries, read without a lock and changed under one | Same chunks |
| `CopyOnWriteArrayList` | Java class | Java class | Written for the library | Same |
| `CopyOnWriteArraySet` | Java class | Java class | That list, without duplicates | Same |
| `LinkedBlockingQueue`, `LinkedBlockingDeque`, `ArrayBlockingQueue`, `PriorityBlockingQueue` | Java classes | Java classes | Ports of JSR-166 | Same ports, whose waits fail |
| `ConcurrentLinkedQueue`, `ConcurrentLinkedDeque` | Java classes | Java classes | A ring of elements under one lock | Same ring |
| `ReentrantLock`, `ReentrantReadWriteLock`, `Condition` | Java classes | Java classes | Ports of JSR-166, sleep on pthread | Count their holds |
| `StampedLock` | Java class | Java class from API 24, the port below | State of JSR-166, on the queue of the other locks | Counts its holds |
| `Semaphore`, `CountDownLatch`, `CyclicBarrier` | Java classes | Java classes | Ports of JSR-166, sleep on pthread | Plain counts |
| `ThreadLocal` | Java class | Java class | A table of values per thread | A plain value |
| `Thread` | Java class | Java class | A thread of the system, on pthread | None |

## The common declarations

A common class is declared with `expect`, and is the Java class on the JVM. So its declaration must
match that class, and what cannot match is declared another way.
[Migrating](migration.md#what-differs-from-java) lists what that changes in the code. The reasons
are these.

- **A Java function that takes Java types becomes an extension.** The computations of the map take
  `java.util.function` types, and the waits with a time limit take a `long` and a `TimeUnit`. The
  common ones take Kotlin functions and a `Duration`, so they cannot be the expected members.
- **A typealias brings no static function.** So `currentThread`, `interrupted` and `sleep` stand on
  their own.
- **A member cannot be a property where the Java class has a function.** So the members keep their
  Java names, like `getName()` and `isAlive()`.
- **A Java class has no default argument to match.** So `ConcurrentHashMap()` and
  `ConcurrentHashMap(initialCapacity)` are two constructors.
- **A type that is nested in a typealias cannot be named in common code.** Java declares that
  `keySet()` returns a `KeySetView`, so the common map declares that type too, and common code types
  it as a `MutableSet`. The two locks of a `ReentrantReadWriteLock` are typed as `Lock` for the same
  reason.
- **The types that the Java classes rely on are declared too.** The exceptions, the queue
  interfaces, `ConcurrentNavigableMap` and `NavigableSet` are the Java types on the JVM and Android,
  and types of the library elsewhere. So code names and catches the same type on every target.
  Common Kotlin has no queue and no sorted map to build on.
- **`null` is ruled out by the types of the maps and of the queues**, `K : Any`, `V : Any` and
  `E : Any`, where the Java classes throw.

## Android

The platform map has no `computeIfAbsent`, `compute` or `merge` below API 24, and `StampedLock`
appeared in API 24. So both are classes of the library on Android, on every version: a class that
wraps the platform class from API 24, and the port below. An instance picks one when it is created,
from `Build.VERSION.SDK_INT`. The other classes exist at API 21, the minimum of the library, so they
are the Java ones.

The computations are also members of the map on Android. A map is a `java.util.Map` there, and
inherits `computeIfAbsent`, `compute` and `merge` from it. Those take Java functional interfaces.
They are not atomic, and they do not exist below API 24. A Kotlin lambda selects the members of the
library, for both backends. A call that passes a `java.util.function` object still reaches the Java
ones.

The two backends of the stamped lock are behind one internal interface, `StampedLockBackend`, whose
functions have the names of the Java ones. The platform lock implements it by being a subclass of
the Java class.

## Where the ports live

The ports are internal classes of `commonMain` and `concurrentMain`, in `koncurrent.internal`. The
public class of a platform without `java.util.concurrent` wraps one.
[CONTRIBUTING.md](../CONTRIBUTING.md#layout) lists the source sets.

- A typealias cannot point to an internal class. Hence the wrappers. The frequent operations of a
  port are inline functions, which the wrapper inlines in its own. So a lookup through the public
  map costs the same as on the port.
- The ports are compiled for the JVM too, where nothing uses them. This is what lets Lincheck and
  the JVM stress tests check them.
- The JS and Wasm targets use the ports of the collections as they are. Their iterators keep working
  while the collection changes, which a standard `HashMap` does not allow.

## What makes code fast on Kotlin/Native

One finding drove most of the optimizations. Kotlin/Native gives each function a frame for its
references, which the garbage collector reads. It sets the frame up on entry, after a lookup of the
thread. A function that LLVM inlines keeps its frame. A function that Kotlin inlines does not, since
it is gone before the frames are laid out. So the small functions of the hot paths are `inline`,
with the warning about it suppressed for the file.

The finding has another side. The references of a function that Kotlin inlines join the frame of its
caller, and that frame is cleared on entry, whether the call uses them or not. The queue of the
locks is such a function, with more than thirty references. So a lock keeps its wait in a regular
function, whose frame is only set up for a thread that waits. A lock taken at its first try then
clears 8 bytes, where it cleared 300.

A file that declares a thread local costs its callers too. Kotlin/Native initializes such a property
for each thread, so an access to the file from another file starts with a lookup of the thread. The
check for an interrupt, which every wait makes, reads a counter that is kept in a file without a
thread local.

The place of a field in its object matters as well. A thread that reads a field next to one that
other threads write waits for the memory that they just wrote. [The fair mode](#the-fair-mode) is
built around that.

Allocation is what costs the most. Kotlin can only update a field atomically when that field is an
object of its own, so an algorithm that updates the links of its nodes allocates several objects for
each node. That decided how [the queues that never wait](#the-queues-that-never-wait) and
[the sorted map](#the-sorted-map-and-set) are built.

[Benchmarks](benchmarks.md#evolution) has the figures behind each of these.

## The map

The map port is `PortableConcurrentHashMap`.

**The algorithm is the original one.** An empty bin takes its first node with a CAS. Any other
update locks the bin. A resize is cooperative. It leaves forwarding nodes, stamps the size control
and hands ranges of bins to the threads that help. The count is striped. The views are weakly
consistent, through a port of the `Traverser`. Tree bins are kept, with their read-write lock.

**Types replace the encoding of the original.** It marks its special nodes with negative hashes and
`null` keys. Here `Bin` is a sealed interface (list node, tree bin, forwarding node, reservation
node) and `Node` is a sealed class (list node, tree node). Every dispatch is an exhaustive `when`,
with no cast.

**The bin lock is a lock word for each bin.** The original locks the monitor of the first node of a
bin. Kotlin atomics have no field updater, so a node cannot hold a lock word that a CAS can reach.
The lock is one `Int` for each bin, in an `AtomicIntArray` beside the bins. Locking is a CAS from 0
to 1. It costs 4 bytes per bin and nothing to allocate or close. A thread that finds it taken checks
it 128 times, since a bin is held for a few nanoseconds. Then it gives way to the other threads
between two checks.

That lock has two limits.

- It is not reentrant, which is why
  [a computation must not update its map](things-to-know.md#collections). The JDK detects some of
  those updates and throws.
- An update writes two cache lines, the lock word and the node. The JDK writes one, because its lock
  is in the header of the node.

The port differs from the original in a few other ways.

- **Lookup before `computeIfAbsent` and `putIfAbsent`.** Both first look the key up without the
  lock, through the whole bin. The original only looks at the first node that way. So threads that
  ask for the same present key never wait for each other.
- **Size counter.** It is striped as in the original: a base count, then 16 cells once threads
  contend, 128 bytes apart in one `AtomicLongArray`. A thread adds to the cell of its identifier. On
  native the identifiers count up, so the first 16 threads never share a cell. The original also
  moves a thread to another cell when it contends.
- **Resize ranges.** The original sizes them from the number of processors, which common Kotlin does
  not give. A resize is split in 128 ranges of at least 16 bins.
- **Tables.** `AtomicArray` copies the array that it is given, so each new table is allocated twice.
  Nothing public on Kotlin/Native avoids it.
- **Tree bins.** A writer spins while the running lookups end, where the original parks. Keys of one
  `Comparable` class are ordered by `compareTo`. The other ties go left, where the original compares
  class names and identity hashes. `root`, `left` and `right` are volatile, because Kotlin/Native
  gives no meaning to a racy read.
- **Traversal.** Outside of a resize an iterator visits the bins in order, without the bookkeeping
  that the original keeps for the tables it left.
- **Views.** The port and its views extend the standard `AbstractMutableMap`, `AbstractMutableSet`
  and `AbstractMutableCollection`. `keys.add` throws, as for the Java map.

## The locks

A lock that owns a pthread mutex must destroy it, and destroying it while a thread uses it is
undefined. The locks here own no native resource, so they have no `close`.

### The queue

The locks are built on a port of the `AbstractQueuedSynchronizer` of JSR-166: an atomic state, and a
queue of the waiting threads. A thread that cannot acquire enqueues itself and sleeps. A release
wakes up the first thread of the queue. The port has the exclusive mode, the shared mode, the
conditions, the timeouts and the interruption of the original.

It differs from the original in five ways.

- **A lock passes the function that tries to acquire.** The original calls abstract functions,
  `tryAcquire` and `tryAcquireShared`, and throws for the mode that a lock does not implement. Here
  the queue is an inline function that takes the try as a lambda. A mode that a lock does not have
  cannot be called, and the try is not a virtual call.
- **A thread may try 16 times before it joins the queue**, giving way to the other threads between
  two tries. Sleeping and being woken up costs far more than a short wait. The original never waits
  that way, and neither does the `ReentrantLock` of the JDK, which is why both do badly when two
  threads fight for one lock. What is fair joins the queue at once.
- **The head of the queue is a plain volatile field**, set after the tail when the queue is created.
  It is one object less per lock, and one read less per release.
- **A try that throws leaves the queue cleanly.** The original cancels the node. Here the first
  thread of the queue leaves it as a thread that acquired does, and wakes up the next one.
- **A thread that gives up leaves its node behind.** The next section tells how.

### Leaving the queue

A thread gives up when its time runs out, or when it is interrupted in a wait that an interrupt
ends. The original then unlinks its node with atomic updates of the links of the neighbours. The
fields of a Kotlin class have no such update, and an atomic object for each link would cost every
waiting thread three more allocations.

So the node stays in the queue. The thread marks it as cancelled, in a volatile field of its own,
and wakes up the first thread behind it that did not give up too. Three rules make it safe.

- **A thread never sleeps behind a cancelled node.** It reads the mark of its predecessor before
  each sleep. When the mark is set, it links itself to the node before, and looks again. The thread
  that cancels writes its mark, then reads its link to the next node. The thread behind writes that
  link when it enqueues, then reads the mark. So one of the two sees the other. Either the thread
  that cancels finds its successor and wakes it up, or the successor finds the mark.
- **A wake-up is never lost in a cancelled node.** The thread that gives up may have been woken up
  to acquire, just before. Waking up its successor passes that on. The successor skips the node,
  finds itself first, and tries.
- **A signal never leaves a cancelled node in front of a thread.** A signal moves a thread from a
  condition to the queue of the lock. That thread sleeps, so the signal links its node in its place,
  and reads two marks for it. The first one is the mark of the node that it is put behind. When it
  is set, the signal wakes the thread up, as the original does. The second one is the mark of the
  node that it just linked. A wait that runs out of time just as it is signalled gives up its node
  and joins the queue again right behind it, and it may skip that node before the signal has written
  its link. The signal then links the thread behind in the place of the node.

The third rule keeps the repair out of a release. A release that finds a node after the head reads
its status and nothing else, and every lock pays for one more read there.

A cancelled node never becomes the head, so it always keeps a predecessor to go back to. The cost is
one volatile field in each node, one read of it before a sleep, and one read of it by a signal that
moves a node.

**Sleeping is per thread, not per lock.** Each thread owns one pthread mutex and condition, freed
after the thread ends. A wake-up is first an atomic exchange. It only calls pthread when the thread
really sleeps. This is the only native memory of the library.

A sleep with a time limit counts that time on a clock that the system cannot move. Apple and Windows
take the time to wait. Linux and Android take the moment to wake up, on the monotonic clock that the
condition is created with.

### `ReentrantLock`

It is the lock of JSR-166. Unless it is fair, a free lock is taken with one CAS, even when threads
wait.

- **The holds of the owner are counted in a plain field**, which only the owner accesses. The
  original counts them in the atomic state. This spares two atomic accesses per hold.
- **The owner is a thread identifier.** On native it is the identifier of the worker that Kotlin
  gives to every thread, which costs 0.1 ns to read. A thread local property costs 0.9 ns.

### `ReentrantReadWriteLock`

It is the lock of JSR-166. The state counts the write holds in its low half and the read holds in
its high half. A reader adds to the count with a CAS, and never sleeps unless a thread writes.

Readers that follow each other could keep a writer out forever. The rule of the original is kept: a
thread that does not read yet lets the first thread of the queue pass when it waits to write. A
thread that already reads goes on, since that writer waits for it.

The read lock is reentrant, so each thread counts its holds. The first reader has its count in the
lock. The others have theirs in a thread local.

Two things differ from the original, and make the read lock 2 to 3 times faster with several
readers.

- **The last reader is not cached.** The original keeps the count of the last reader in a field of
  the lock, to spare a lookup of the thread local. Readers then write that field in turn, which
  costs them more than the lookup does.
- **A read hold is released with one atomic addition.** The original retries an atomic update until
  it succeeds.

`read` and `write` behave as the functions of `kotlin.concurrent` on the JVM. `write` releases the
read holds of its caller first and takes them back after, because a reader cannot become a writer.
`getReadHoldCount` and `getWriteHoldCount` keep their Java names for that: they are the members that
`write` needs, and the Java class already has them.

### `Condition`

It is the `ConditionObject` of JSR-166. A thread that waits is kept in a list of the condition. A
signal moves the first one to the queue of the lock, where it waits to acquire again.

A thread may give its wait up, on a timeout or an interrupt, at the very moment when a signal moves
its node to the queue of the lock. The original settles which of the two came first with an atomic
update of the status of the node. Here the thread cancels its node, takes the lock again from a new
node, and reads the status once it holds the lock. A signal is always given under that lock, so the
status then tells whether one moved the node.

- If a signal did, the wait counts as signalled. The moved node is skipped in the queue of the lock,
  as any cancelled node.
- If none did, the thread takes its node out of the list, and reports the timeout or the interrupt.
  A signal that finds a cancelled node first drops it and goes to the next one, so a signal is never
  spent on a thread that gave up.

### `Semaphore` and `CountDownLatch`

They are the `Semaphore` and the `CountDownLatch` of JSR-166, on the shared mode of the queue. Their
state is the number of permits, and the count. A thread that acquires from the queue wakes up the
next one, so that permits released together, or a latch that opens, let every waiting thread
through.

`withPermit` has the name of the function of kotlinx.coroutines for its own semaphore.

### `CyclicBarrier`

It is the `CyclicBarrier` of JSR-166: a lock, one condition, and a generation that changes each time
the barrier opens or is reset. A thread that wakes up tells from the generation of its arrival
whether the barrier opened or broke. The port is written on the lock port and its condition.

`await` with a time limit throws `TimeoutException` when its time runs out, as in Java. It cannot
return `false` as the other waits do, since it returns the arrival index of the thread.

`Runnable` is in common code for the action of the barrier, though JS and Wasm have no `Thread` to
give one to. There, a barrier of one party opens at once. A wait with a time limit for another party
runs out of time at once, which breaks the barrier as it does elsewhere.

### The fair mode

`ReentrantLock`, `ReentrantReadWriteLock` and `Semaphore` can be created fair, as in Java. A fair
one serves the threads in the order of their arrival. A thread takes what is free only when no other
thread has waited longer, which the queue tells with the `hasQueuedPredecessors` of the original.
And it joins the queue without its 16 tries, which would make it lose its turn. `tryLock()` and
`tryAcquire()` without a time limit take what is free at once, as in Java.

The fair mode had to cost the locks that are not fair as little as possible. What it costs them is
one look at their class.

- **A fair lock is told by its class, not by a flag.** `FairPortableReentrantLock` and
  `FairPortableReentrantReadWriteLock` are subclasses that add nothing. Kotlin/Native lays a
  `Boolean` out after the other fields, next to those that the threads write: the owner, or the
  first reader. A thread that reads the flag before it updates the state waits for the memory that
  another thread just wrote. The class is read from the header of the object, which no thread
  writes, and the lock keeps its size.
- **Only `lock` looks at the class.** `unlock` is the same code for a fair lock. It is inlined
  twice, by Kotlin into the public class, then by LLVM into the code that calls it, and a few
  instructions more stop LLVM.
- **A reader only looks at the class when there is a queue.** It already reads the head, to let a
  waiting writer pass. A writer looks at the class straight away. The fair read-write lock creates
  its queue at once, so that no reader misses that it is fair.

The semaphore has no field that its threads write, so its flag is a plain field.

### `StampedLock`

It is the lock of JSR-166 for its state, and the queue of the other locks for its waits.

The state is one number of 64 bits. Its low 7 bits count the readers, the next bit is set while a
thread writes, and the bits above count the write locks that were released. A thread that takes a
lock gets the new state as its stamp. `tryOptimisticRead` returns the state as it is, and `validate`
compares it with the state of the moment. They are the same unless a thread took the write lock in
between. The readers beyond 126 are counted apart, as in the original, in the state of the queue,
which this lock has no other use for.

The original has a queue of its own, where the readers that wait together share a node. Here the
threads wait in the queue of the other locks, a node each, and a reader that acquires wakes up the
next one, as for `ReentrantReadWriteLock`. The queue is the part of the library that is tested the
most, and a second one would have to earn the same trust.

As in the original, `readLock` and `writeLock` first try to take the lock at once, whatever the
queue holds. A reader that failed then lets the first thread of the queue pass when it waits to
write, so that readers that follow each other do not keep a writer out forever.

The lock has no owner. Any thread releases it with its stamp, and nothing tells which thread reads.
So a read hold is released as in the original, by an atomic update that is tried again when another
reader changed the state first. The single atomic addition of `ReentrantReadWriteLock` is not safe
here: a wrong stamp could take a reader out of a state that has none.

`asReadLock`, `asWriteLock` and `asReadWriteLock` return the lock without its stamps, as in the
original. Such a view releases with the functions that need no stamp, and fails when the lock is not
held in its mode. A view implements `Lock`, which common code cannot do. So each kind of platform
has its own: over the Java interface on Android, with the time limits on native, and plain on JS and
Wasm.

`read` and `write` run a block under a lock. Java has no such function. They are the ones that
`ReentrantReadWriteLock` has here and in the standard library.

#### The two fences

A processor may run the reads and the writes of a thread in another order than the code has them,
when they go to different places of the memory. ARM processors do. The compiler may too, on any
processor: LLVM is allowed to move a plain read from before the read of an atomic to after it, and a
plain write from after the write of an atomic to before it. The original keeps the order where it
matters with two fences. Kotlin has no fence, so each is replaced by an access to an atomic, which
neither the processor nor the compiler moves that way.

- **Before `validate` reads the state.** An optimistic reader reads the state, then the data, then
  the state again. The last read may come before the reads of the data. The reader then finds the
  state unchanged and trusts data that a writer changed meanwhile. A write of an atomic comes after
  the reads made before it, and before the read of an atomic that follows. So `validate` first
  writes a cell, then reads the state. The cell is one of 64 that are 128 bytes apart, picked by the
  number of the thread. Readers that all wrote the same cell would wait for each other's memory,
  which is what an optimistic read spares.
- **After the write lock is taken.** The update of the state may reach the other threads after the
  writes that follow it. A reader could then find the new data with the old state. A read of an
  atomic comes after the update, and the writes that follow come after that read. So a writer reads
  the state again, and returns what it read as its stamp.

Without the first fence, an optimized program on an Apple M5 validated reads of data that was half
written in every run of five seconds, between 39 and 7 073 of them. With it, none in any run.
Removing the second fence showed nothing on that processor. It stays, since the architecture allows
what it prevents, and it costs one read.

A debug build cannot show any of this, since each of its accesses is a call.

### `Lock`

`Lock`, `Condition` and `ReadWriteLock` are interfaces, since they are the Java interfaces on the
JVM. The Java ones of the first two have more functions, so a class of common code that implements
them does not compile for the JVM. They are not meant to be implemented, and the compiler says so. A
class that implements one needs an opt-in to `InternalForInheritanceKoncurrentApi`, in common code
and on every target without Java. That leaves room to add functions to them later. Code written for
the JVM or Android alone sees the Java interfaces, which need no opt-in. The interfaces of the
queues and of the sorted map follow the same rule.

On the targets without Java `Lock` and `Condition` have one more member than in common code:
`tryLock` and `await` with a time limit. The common extension of the same name calls it there, where
the JVM calls the method of the Java interface. So a time limit reaches the lock without a cast. An
interface cannot have an internal member, so this one is public. It has the name and the behavior of
the extension, so users see one function.

### One thread

On JS and Wasm a lock never waits. It still counts its holds, so that an `unlock` without a `lock`
fails there as it does elsewhere. A stamped lock keeps its modes and its stamps in two plain fields.

A wait that could never end fails with `IllegalStateException` rather than hang.
[Things to know](things-to-know.md#platforms) lists those waits. A wait with a time limit would end,
so it does not fail. It answers at once what it would answer when its time is over, since waiting
could change nothing.

## The threads

`Thread` exists where threads do: on the JVM, Android and native. It is declared in the `concurrent`
source set, so code that also targets JS or Wasm cannot name it by mistake.

On native a thread is a pthread, created detached. It frees what it holds when it ends, and `join`
waits on a latch of the library that the thread opens last.

- **The current thread.** Each thread keeps its `Thread` in a thread local. A thread that the
  library did not start, like the main one, gets a `Thread` the first time it asks. Nothing tells
  when such a thread ends, so it stays alive for the library.
- **Interruption.** `interrupt` sets a flag of the thread and wakes it up. A wait that an interrupt
  ends reads the flag when it starts and each time it wakes up. A counter of the interrupted threads
  spares that read: it is zero nearly always, and reading it costs less than finding the current
  thread.
- **Sleeping.** `sleep` is a timed wait on what the thread sleeps on in a lock. So an interrupt ends
  it as it ends any other wait.
- **Daemons.** A native program ends when its main function returns, whatever its threads do. The
  daemon flag is kept, and changes nothing.
- **Uncaught exceptions.** An exception that escapes a thread ends that thread, which prints the
  stack trace. The JVM does the same by default.

## The thread local

On native a thread keeps the values of all the thread locals in one table, which goes away with the
thread. It is a hash table with open addressing, written for this use. The table does not reference
the thread locals. A collected thread local marks its key dead through a cleaner, and each thread
drops the values of dead keys when its table fills up. So neither a thread local nor its values
leak. `get` returns `null` until a value is set.

`getOrSet` has the name and the behavior of the function that the Kotlin standard library has for
the JVM.

## The list

`CopyOnWriteArrayList` was written for the library. The JSR-166 file carries a Sun copyright, so it
is not a port. It keeps an array that is never modified, in an atomic reference. A read loads it. An
update copies it, changes the copy and installs it with a CAS, and starts over if another update
came first. The Java class guards its updates with a lock instead.

The array is created as an array of anything, as the standard collections create theirs. Two
functions hold the casts that this takes.

The behavior is the one of the Java class, which the same tests check on both. An iterator goes
through the elements of its creation and cannot change the list. A `subList` is a view that updates
the list, and fails with `ConcurrentModificationException` once the list changed otherwise.

`CopyOnWriteArraySet` is that list with `addIfAbsent` as its `add`, as in JSR-166. It compares as a
set, whatever the order of its elements, on the elements of one moment.

## The blocking queues

`LinkedBlockingQueue`, `LinkedBlockingDeque`, `ArrayBlockingQueue` and `PriorityBlockingQueue` are
ports of the classes of JSR-166, with their algorithms: two locks for the linked queue, one lock and
a ring for the array, one lock and a heap for the priorities.

Each has one port, in `commonMain`, for native, JS and Wasm alike. A port takes `InternalLock`, the
lock that the ports of common code share, and its conditions. Where there are threads it is the lock
port itself, whose `lock` and `unlock` are inline. On JS and Wasm it is nothing, and its condition
fails a wait that could never end. So the same port serves there: `take` on an empty queue fails,
and a wait with a time limit answers at once.

`Queue`, `Deque`, `BlockingQueue` and `BlockingDeque` are declared too, since common Kotlin has no
queue type. They are the Java interfaces on the JVM and Android.

Some things differ from the originals.

- **The iterator of `ArrayBlockingQueue` goes through a copy of the elements.** The original keeps
  every live iterator in step with the ring, whose elements shift when one is removed from the
  middle. It finds the iterators through weak references, which common Kotlin does not have. A copy
  is still weakly consistent, as `java.util.concurrent` defines it, and it is what the
  `PriorityBlockingQueue` of the JDK does.
- **`removeAll` and `retainAll` remove through the iterator**, one element at a time. The originals
  work on batches.
- **`clear` and `drainTo` of `ArrayBlockingQueue` wake up every thread that waits for room.** The
  original wakes up as many as it made room for, which takes a count of the threads that wait on a
  condition.
- **`PriorityBlockingQueue` throws `Error` when it cannot grow any more**, at two billion elements.
  The original throws `OutOfMemoryError`, which common Kotlin does not have.

Three behaviors of the originals are kept, and are worth knowing.

- **A fair `ArrayBlockingQueue` is a queue on a fair lock.** The threads that wait to add or to take
  then go in the order of their arrival, at the price of that lock.
- **`LinkedBlockingDeque` only looks at an interrupt when it has to wait**, in its `put` and `take`
  functions. `LinkedBlockingQueue` and `ArrayBlockingQueue` look at it first.
- **`drainTo` of `LinkedBlockingQueue` updates the count last.** Until it is done, `size` still
  counts the elements that it moved, and an `offer` may find the queue full.

## The queues that never wait

`ConcurrentLinkedQueue` and `ConcurrentLinkedDeque` are the classes of the JDK on the JVM and
Android, where they are lock-free: a thread adds or takes with an atomic update of a field of a
node. Elsewhere they are not ports of that algorithm. They are one ring of elements that one lock
guards, `PortableConcurrentDeque`.

A node of the queue of JSR-166 takes three objects in Kotlin, for itself, its element and its link.
Every thread that adds allocates them, and the collector follows. A ring allocates when it grows,
and no more. So the lock wins although it lets one thread in at a time: an operation on the ring is
a dozen instructions. The ring hands over 2 times the elements of the original algorithm with one
thread, and about 4 times with several. [Benchmarks](benchmarks.md#evolution) has the candidates
that were measured.

Four things follow from that choice.

- **An operation never waits for an element nor for room**, as in Java. It may wait for the lock,
  for the time of another operation. `contains`, `remove` of an element and the creation of an
  iterator hold the lock while they go through the elements.
- **An iterator goes through a copy**, as the one of `ArrayBlockingQueue` does.
- **`size` is known at once.** The classes of the JDK count their elements.
- **A ring that a burst made large is dropped once it is empty.** A linked queue frees its nodes as
  they leave. The ring keeps its length otherwise, up to 1 024 elements for good.

The ring takes `InternalLock` too. Adding and taking are each one function, since each function of
Kotlin/Native has its own frame to set up.

## The sorted map and set

`ConcurrentSkipListMap` and `ConcurrentSkipListSet` are the classes of the JDK on the JVM and
Android, where they are a lock-free skip list. Elsewhere they are not ports of it. Their entries are
in a `SortedTable`: chunks of up to 64 entries in the order of their keys, the keys of a chunk in
one array and their values in another. One lock guards the changes. A read of an entry takes no
lock.

A skip list has a node for each entry and index nodes above them, and it updates their links
atomically. In Kotlin each of those links is an object of its own. A table of a million entries is
under a hundred thousand objects, where the skip list has several millions.

A key is found with two binary searches, among the first keys of the chunks, then in a chunk. An
insertion moves the entries of one chunk at most. A full chunk is split in two halves. Two
neighbours that hold 32 entries or less together are merged, so that the map shrinks as it empties.

A change takes the lock. So the threads that write take turns, where the skip list of the JDK lets
them work at once.

### Reads without the lock

A read of one entry takes no lock: `get`, the closest keys, the first and the last entry. A search
in a million keys takes half a microsecond, most of it waiting for the memory. Under the lock, a
reader that found it taken went to sleep, and the map got slower with each thread that joined.

The table counts its changes in a version number, which is odd while a thread makes one. A reader
takes the version, searches the chunks as they are, then takes the version again. The same even
number twice tells that no thread changed anything meanwhile. It is what a stamped lock does for an
optimistic read, with the same [fences](#the-two-fences) on both sides.

A search that runs while a thread moves entries may read anything: a chunk that is gone, a key
twice, a place that is empty. So it trusts nothing. A missing chunk or key ends it, an index that is
out of its array throws, and whatever it returns only counts once the version is found unchanged. A
read that cannot tell tries once more, then takes the lock.

A writer helps too. One that finds the lock taken first searches for its key without the lock. Its
search with the lock then finds what it needs in the cache of its processor, so it holds the lock
for less.

The reads lose where they are not the rule. When half of the operations change a small map, most
searches are thrown away.

### The map and its views

Three things follow from the way the table is built.

- **An iterator goes through copies.** It copies 32 entries with the lock held once, then finds its
  way again from the last key that it gave. So it never fails when the map changes, and it may or
  may not show what changed, as the iterators of the JDK.
- **`size` is known at once.** The JDK counts the entries. A view with bounds adds up the sizes of
  the chunks between them.
- **An operation on one key is atomic**, and so are the polls of the whole map. A poll of a view
  with bounds finds its entry, then removes it if it is still there, as the sub map of the original
  does.

One class stands for the map and for each of its views. `PortableNavigableMap` is a table, two
bounds and a direction. `subMap`, `headMap`, `tailMap` and `descendingMap` return another one on the
same table, and a key set is a `PortableNavigableSet` over one. The set is the same map with `true`
as the value of each element, as in the original.

On JS, comparing two keys of unrelated types does not throw. A map of numbers could then seem to
hold the keys of a map of strings. So `equals` looks both ways there and everywhere else: each map
must hold the entries of the other.
