[koncurrent_docs]: https://manriif.github.io/koncurrent/docs/
[jsr166]: https://gee.cs.oswego.edu/dl/concurrency-interest/

# Koncurrent

![Stability](https://img.shields.io/badge/stability-experimental-orange.svg)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-dokka-green)][koncurrent_docs]
[![Maven Central](https://img.shields.io/maven-central/v/io.github.manriif.koncurrent/koncurrent?label=Maven%20Central&logo=apache-maven&color=teal)](https://central.sonatype.com/artifact/io.github.manriif.koncurrent/koncurrent)

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.21-purple.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)

![platform-jvm](https://img.shields.io/badge/platform-jvm-DB413D.svg?style=flat)
![platform-android](https://img.shields.io/badge/platform-android-6EDB8D.svg?style=flat)
![platform-android--native](https://img.shields.io/badge/platform-android%20native-6EDB8D.svg?style=flat)
![platform-js](https://img.shields.io/badge/platform-js-F8DB5D.svg?style=flat)
![platform-wasmjs](https://img.shields.io/badge/platform-wasm--js-624FE8.svg?style=flat)
![platform-wasmwasi](https://img.shields.io/badge/platform-wasm--wasi-624FE8.svg?style=flat)
![platform-linux](https://img.shields.io/badge/platform-linux-2D3F6C.svg?style=flat)
![platform-windows](https://img.shields.io/badge/platform-windows-4D76CD.svg?style=flat)
![platform-macos](https://img.shields.io/badge/platform-macos-111111.svg?style=flat)
![platform-ios](https://img.shields.io/badge/platform-ios-CDCDCD.svg?style=flat)
![platform-watchos](https://img.shields.io/badge/platform-watchos-C0C0C0.svg?style=flat)
![platform-tvos](https://img.shields.io/badge/platform-tvos-808080.svg?style=flat)

`Thread` and the classes of `java.util.concurrent` for Kotlin Multiplatform, with the same names
and the same behavior on every target.

## Table of contents

- [Why Koncurrent](#why-koncurrent)
- [What it offers](#what-it-offers)
- [Requirements](#requirements)
- [Getting started](#getting-started)
- [Usage](#usage)
- [Project state](#project-state)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Why Koncurrent

Kotlin common code has atomics, in `kotlin.concurrent.atomics`, and nothing above them. There is
no thread, no lock, no concurrent map and no thread local.

Koncurrent was written to address a few pain points of existing solutions:

- **Coarse-grained locking**: A map backed by a standard collection and a single lock makes every
  thread wait for the others, even to read.
- **Manual resource management**: A native lock backed by a pthread mutex must be closed. Closing
  it while another thread is still using it results in undefined behavior.
- **Inconsistent behavior**: Concurrency primitives can behave differently across platforms.
- **Incomplete APIs**: Existing solutions provide isolated primitives rather than a complete,
  coherent concurrency toolkit.
- **Missing concurrent data structures**: Developers often have to implement their own thread-safe
  collections or settle for less efficient alternatives.

The JVM solved this long ago, with `Thread` and `java.util.concurrent`. Koncurrent gives those
classes to Kotlin Multiplatform, instead of inventing new ones.

## What it offers

| | Package | What it is |
|---|---|---|
| `ConcurrentHashMap` | `koncurrent` | A hash map with lookups that never block and atomic updates of a key |
| `ConcurrentHashSet` | `koncurrent` | A hash set backed by that map |
| `ConcurrentSkipListMap` | `koncurrent` | A map that keeps its keys in order, and finds the closest keys to a given one |
| `ConcurrentSkipListSet` | `koncurrent` | A sorted set on that map |
| `ConcurrentNavigableMap`, `NavigableSet` | `koncurrent` | The common types of the sorted map and set, and of their views |
| `CopyOnWriteArrayList` | `koncurrent` | A list for data that is read often and rarely changed |
| `CopyOnWriteArraySet` | `koncurrent` | A set on that list, for a few elements that are read often |
| `LinkedBlockingQueue` | `koncurrent` | A queue where threads wait for an element, and for room when it has a capacity |
| `ArrayBlockingQueue` | `koncurrent` | A blocking queue of fixed capacity, on an array |
| `LinkedBlockingDeque` | `koncurrent` | A blocking queue with two ends |
| `PriorityBlockingQueue` | `koncurrent` | A blocking queue that hands over its smallest element first |
| `ConcurrentLinkedQueue` | `koncurrent` | A queue that never waits: taking from an empty one returns `null` |
| `ConcurrentLinkedDeque` | `koncurrent` | The same queue with two ends |
| `Queue`, `Deque`, `BlockingQueue`, `BlockingDeque` | `koncurrent` | The common types of these queues |
| `Thread` | `koncurrent` | A thread to run code on, with `thread { }` to start one |
| `ThreadLocal` | `koncurrent` | A variable with one value for each thread |
| `Semaphore` | `koncurrent` | A count of permits that limits how many threads use a resource |
| `CountDownLatch` | `koncurrent` | A gate that opens once other threads have counted down to zero |
| `CyclicBarrier` | `koncurrent` | A point where a set number of threads wait for each other, round after round |
| `ReentrantLock` | `koncurrent.locks` | A mutual exclusion lock that its holder can take again |
| `ReentrantReadWriteLock` | `koncurrent.locks` | A read lock shared by many threads and a write lock held by one |
| `StampedLock` | `koncurrent.locks` | A read-write lock whose readers can also read without locking, then check that no thread wrote |
| `Condition` | `koncurrent.locks` | A place where threads that hold a lock wait to be signalled |
| `Lock` | `koncurrent.locks` | The common type of these locks, with `withLock` |
| `ReadWriteLock` | `koncurrent.locks` | The common type of a pair of locks, one to read and one to write |

[Migrating to Koncurrent](docs/migration.md) tells what each class has in common code, and how
to move from `java.util.concurrent`, Stately or atomicfu.

- **The Java classes on the JVM and Android.** A class of Koncurrent is a `typealias` of the
  Java class there. No wrapper stands in between, so it costs nothing and it goes wherever the
  Java class is expected. The map and the stamped lock are the two exceptions,
  [on Android](docs/design.md#android).
- **The same algorithms everywhere else.** The other targets get ports of the [JSR-166][jsr166]
  sources that the JDK is made of. A lookup never blocks, and an update locks one bin of the
  table. Only the two queues that never wait and the sorted map are built another way there,
  which is faster without atomic fields.
- **The same behavior on every target.** The same tests run on the Java classes and on the
  ports. The ports are also checked for linearizability with
  [Lincheck](https://github.com/JetBrains/lincheck).
- **Time limits and interruption.** A wait takes a `Duration` when it must not last. A thread
  that waits can be interrupted, as in Java.
- **Nothing to close.** A lock holds no native resource. It is freed as any object.
- **No dependency.** The library only needs the Kotlin standard library.

Atomics are left to the standard library, and suspending primitives to
[kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines). Koncurrent is about
threads.

### Supported platforms

| Platform | Targets | Implementation |
|---|---|---|
| JVM | `jvm` | The classes of the JDK |
| Android | `android` | The classes of the platform. The map and the stamped lock use their ports below API 24. |
| Apple | `macos`, `ios`, `tvos`, `watchos`, device and simulator | Ports, on the threads of the system |
| Linux | `linuxX64`, `linuxArm64` | Ports, on the threads of the system |
| Windows | `mingwX64` | Ports, on the threads of the system |
| Android native | `androidNativeArm32`, `androidNativeArm64`, `androidNativeX86`, `androidNativeX64` | Ports, on the threads of the system |
| JS and Wasm | `js`, `wasmJs`, `wasmWasi` | The same collections. The locks only count their holds, since there is one thread. No `Thread`. |

## Requirements

- Kotlin 2.4.21 or later.
- Java 17 or later, on the JVM.
- API 21 or later, on Android.

## Getting started

Koncurrent is on Maven Central.

```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.manriif.koncurrent:koncurrent:<version>")
        }
    }
}
```

## Usage

**Read [Things to know](docs/things-to-know.md) first.** It lists the pitfalls of these
classes, and what differs on Android, native, JS and Wasm.

### `ConcurrentHashMap`

```kotlin
val cache = ConcurrentHashMap<String, Parser>()

// The parser of a language is created once, whatever the number of threads that ask for it
val parser = cache.computeIfAbsent("kotlin") { language -> Parser(language) }

// Atomic updates of one key
val hits = ConcurrentHashMap<String, Int>()

hits.merge("home", 1) { count, one -> count + one }
hits.compute("home") { _, count -> if (count == 10) null else count }

hits.putIfAbsent("about", 0)
hits.replace("about", 0, 1)
hits.remove("about", 1)

// Iterating never throws, even while other threads update the map
for ((page, count) in hits) {
    println("$page: $count")
}
```

### `ConcurrentHashSet`

```kotlin
val seen = ConcurrentHashSet<String>()

if (seen.add(id)) {
    // Only one thread gets here for a given id
}
```

### `ConcurrentSkipListMap` and `ConcurrentSkipListSet`

```kotlin
val scores = ConcurrentSkipListMap<Int, String>()

scores[120] = "Ada"
scores[85] = "Linus"
scores[97] = "Grace"

// The keys stay in order
val lowest = scores.firstKey() // 85
val best = scores.lastEntry() // 120=Ada

// The closest keys to one that may not be there
val below = scores.floorKey(100) // 97
val above = scores.higherKey(100) // 120

// A part of the map, and the map in the reverse order. Both read and change the map itself.
val passed = scores.tailMap(90)
val ranking = scores.descendingMap()
```

`ConcurrentSkipListSet` is the same with elements instead of entries. Both use the natural
order of their keys, or the order of a `Comparator`.

### `CopyOnWriteArrayList`

```kotlin
val listeners = CopyOnWriteArrayList<Listener>()

listeners.addIfAbsent(listener)

// The loop goes through a snapshot. Listeners can be added and removed meanwhile.
for (listener in listeners) {
    listener.onEvent(event)
}
```

`CopyOnWriteArraySet` is the same list as a set: its `add` only adds an element that it does
not hold yet.

### Blocking queues

```kotlin
// At most a hundred tasks wait. A producer that is too fast is slowed down.
val tasks = LinkedBlockingQueue<Task>(100)

// Waits for room when the queue is full
tasks.put(task)

val worker = thread {
    while (true) {
        // Waits for a task when the queue is empty
        tasks.take().run()
    }
}

// Without waiting, or for some time only
val added = tasks.offer(task)
val next = tasks.poll(1.seconds)
```

`ArrayBlockingQueue` is the same queue on an array of fixed size. `LinkedBlockingDeque` adds
and takes at both ends, with `putFirst`, `takeLast` and the like. `PriorityBlockingQueue` hands
over its smallest element first, in the order of a `Comparator` or in the natural order of its
elements.

### Queues that never wait

```kotlin
val events = ConcurrentLinkedQueue<Event>()

events.offer(event)

// Returns null when the queue is empty
val next = events.poll()
```

`ConcurrentLinkedDeque` adds and takes at both ends, with `offerFirst`, `pollLast` and the
like. Neither has a capacity.

### `ReentrantLock`

```kotlin
val lock = ReentrantLock()

lock.withLock {
    // One thread at a time
}

// Without waiting
if (lock.tryLock()) {
    try {
        // The lock is held
    } finally {
        lock.unlock()
    }
}
```

A lock has nothing to close.

### `ReentrantReadWriteLock`

```kotlin
val lock = ReentrantReadWriteLock()
val settings = HashMap<String, String>()

// Many threads read at once
fun setting(name: String): String? = lock.read { settings[name] }

// One thread writes, while no other thread reads
fun set(name: String, value: String) {
    lock.write { settings[name] = value }
}
```

Its two locks are also available as `Lock`, through `readLock()` and `writeLock()`.

### `StampedLock`

```kotlin
val lock = StampedLock()
var x = 0.0
var y = 0.0

fun move(dx: Double, dy: Double) {
    lock.write {
        x += dx
        y += dy
    }
}

fun distanceFromOrigin(): Double {
    // Reads without a lock, then checks that no thread wrote meanwhile
    val stamp = lock.tryOptimisticRead()
    var currentX = x
    var currentY = y

    if (!lock.validate(stamp)) {
        lock.read {
            currentX = x
            currentY = y
        }
    }

    return sqrt(currentX * currentX + currentY * currentY)
}
```

An optimistic read takes no lock, so readers do not slow each other down. `write` and `read`
hold a lock around a block. `writeLock()` and `readLock()` return a stamp instead, which
releases the lock, and which `tryConvertToWriteLock` and the like turn into another mode.

`asReadLock()`, `asWriteLock()` and `asReadWriteLock()` give the lock to code that expects a
`Lock` or a `ReadWriteLock`. The lock is not reentrant, and it has no condition.

### `Condition`

```kotlin
val lock = ReentrantLock()
val notEmpty = lock.newCondition()
val queue = ArrayDeque<Task>()

fun put(task: Task) {
    lock.withLock {
        queue.addLast(task)
        notEmpty.signal()
    }
}

// Sleeps until a task arrives
fun take(): Task = lock.withLock {
    while (queue.isEmpty()) {
        notEmpty.await()
    }

    queue.removeFirst()
}
```

### `Semaphore`

```kotlin
// At most four threads use a connection at once
val connections = Semaphore(4)

connections.withPermit {
    // A fifth thread waits here until one of the four leaves
}
```

### Fair locks

A lock is not fair unless it is asked to be. A thread that asks for a free lock takes it, even
when others wait. A fair lock goes to the thread that has waited for the longest, so no thread
waits forever.

```kotlin
val lock = ReentrantLock(true)
val cache = ReentrantReadWriteLock(true)
val connections = Semaphore(4, true)
val tasks = ArrayBlockingQueue<Task>(100, true)
```

Fairness costs speed when many threads ask at once, since every release wakes a thread up.
`tryLock()` and `tryAcquire()` without a time limit take what is free at once, even then.

### `CountDownLatch`

```kotlin
val loaded = CountDownLatch(1)
var configuration: Configuration? = null

// On the thread that loads the configuration
fun load() {
    configuration = readConfiguration()
    loaded.countDown()
}

// On any other thread. It sleeps until the configuration is loaded.
fun configuration(): Configuration? {
    loaded.await()
    return configuration
}
```

### `CyclicBarrier`

```kotlin
// Three workers compute a part each, then meet before the next round
val parts = arrayOfNulls<Part>(3)
val barrier = CyclicBarrier(3) { merge(parts) }

repeat(3) { index ->
    thread {
        while (true) {
            parts[index] = compute(index)

            // Waits for the two others. The merge runs once the three have arrived.
            barrier.await()
        }
    }
}
```

The threads go on together or not at all. When one of them is interrupted or runs out of time
at the barrier, the others get a `BrokenBarrierException`.

### `ThreadLocal`

```kotlin
val builders = ThreadLocal<StringBuilder>()

// Each thread gets its own builder, created the first time it asks
val builder = builders.getOrSet { StringBuilder() }
```

### `Thread`

```kotlin
val worker = thread(name = "worker") {
    // Runs at the same time as the thread that called
}

// Waits for the end of the worker
worker.join()

sleep(100.milliseconds)

val current = currentThread()
```

`Thread` exists on the JVM, Android and native. A project that also targets JS or Wasm uses it
from a source set of those three platforms, not from `commonMain`.

### Time limits

Each wait has a form that takes a `Duration` and tells whether it got what it waited for.

```kotlin
if (lock.tryLock(1.seconds)) {
    try {
        // The lock is held
    } finally {
        lock.unlock()
    }
}

// `false` when no permit came in time
val acquired = connections.tryAcquire(1.seconds)

// `false` when the configuration is still not loaded
val opened = loaded.await(1.seconds)

// `false` when the worker still runs
val ended = worker.join(1.seconds)

lock.withLock {
    // `false` when nothing signalled in time
    val signalled = notEmpty.await(1.seconds)
}
```

### Interruption

`interrupt` asks a thread to stop. A thread that waits is woken up, and its wait throws
`InterruptedException`.

```kotlin
val worker = thread {
    try {
        while (true) {
            take().run()
        }
    } catch (interrupted: InterruptedException) {
        // Asked to stop while it waited for a task
    }
}

worker.interrupt()
worker.join()
```

Code that does not wait reads the flag itself, with `currentThread().isInterrupted()`, or with
`interrupted()` which also clears it.

## Project state

Koncurrent is experimental. Its API may change before version 1.0.

The tests run on [GitHub Actions](https://github.com/manriif/koncurrent/actions/workflows/test.yml)
for every pull request, on Linux, Windows and macOS. [Testing](docs/testing.md) tells what runs
where.

## Documentation

- [API reference][koncurrent_docs], generated by Dokka
- [Things to know](docs/things-to-know.md): the pitfalls, and what differs between the platforms
- [Design](docs/design.md): how each class is built, what differs from the JDK and why
- [Benchmarks](docs/benchmarks.md): the figures against the JDK and Stately, how they were
  taken, and what each optimization brought
- [Testing](docs/testing.md): what is tested, on which target
- [Migrating to Koncurrent](docs/migration.md): from `java.util.concurrent`, Stately or atomicfu,
  with what common code has of each class

## Contributing

Bug reports are welcome. The ports are tuned against benchmarks, and a change that looks
harmless can cost them up to 30% of their speed. So prefer opening an issue to fixing a
problem yourself. [CONTRIBUTING.md](CONTRIBUTING.md) tells how to build the project and run its
tests and its benchmarks.

## License

Koncurrent is licensed under the [MIT License](LICENSE).

The map, the blocking queues, the locks, the conditions, the semaphore, the latch and the
barrier are ports of the [JSR-166][jsr166] sources, written by Doug Lea with assistance from
members of the JCP JSR-166 Expert Group, and released to the public domain under
[CC0](https://creativecommons.org/publicdomain/zero/1.0/).
