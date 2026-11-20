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
- [Things to know](#things-to-know)
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
| `CopyOnWriteArrayList` | `koncurrent` | A list for data that is read often and rarely changed |
| `Thread` | `koncurrent` | A thread to run code on, with `thread { }` to start one |
| `ThreadLocal` | `koncurrent` | A variable with one value for each thread |
| `Semaphore` | `koncurrent` | A count of permits that limits how many threads use a resource |
| `CountDownLatch` | `koncurrent` | A gate that opens once other threads have counted down to zero |
| `ReentrantLock` | `koncurrent.locks` | A mutual exclusion lock that its holder can take again |
| `ReentrantReadWriteLock` | `koncurrent.locks` | A read lock shared by many threads and a write lock held by one |
| `Condition` | `koncurrent.locks` | A place where threads that hold a lock wait to be signalled |
| `Lock` | `koncurrent.locks` | The common type of these locks, with `withLock` |

The packages follow `java.util.concurrent`. Replace `java.util.concurrent` with `koncurrent` in
an import to get the common class. `Thread`, `Runnable`, `InterruptedException`,
`IllegalThreadStateException` and `ThreadLocal`, which Java has in `java.lang`, are in
`koncurrent` too.

- **The Java classes on the JVM and Android.** A class of Koncurrent is a `typealias` of the
  Java class there. No wrapper stands in between, so it costs nothing and it goes wherever the
  Java class is expected. The map on Android is the one exception, for the versions that lack
  atomic computations.
- **The same algorithms everywhere else.** The other targets get ports of the [JSR-166][jsr166]
  sources that the JDK is made of. A lookup never blocks, and an update locks one bin of the
  table.
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
| Android | `android` | The classes of the platform. The map uses the port below API 24. |
| Apple | `macos`, `ios`, `tvos`, `watchos`, device and simulator | Ports, on the threads of the system |
| Linux | `linuxX64`, `linuxArm64` | Ports, on the threads of the system |
| Windows | `mingwX64` | Ports, on the threads of the system |
| Android native | `androidNativeArm32`, `androidNativeArm64`, `androidNativeX86`, `androidNativeX64` | Ports, on the threads of the system |
| Web | `js`, `wasmJs`, `wasmWasi` | The same collections. The locks only count their holds, since there is one thread. No `Thread`. |

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

### `CopyOnWriteArrayList`

```kotlin
val listeners = CopyOnWriteArrayList<Listener>()

listeners.addIfAbsent(listener)

// The loop goes through a snapshot. Listeners can be added and removed meanwhile.
for (listener in listeners) {
    listener.onEvent(event)
}
```

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

## Things to know

- **`getOrPut` is not atomic.** It is the extension of the standard library, which reads then
  writes. Use `computeIfAbsent`.
- **A computation must not update its map.** The function given to `computeIfAbsent`,
  `computeIfPresent`, `compute` or `merge` runs while a part of the map is locked. Keep it
  short, and do not touch the map from it. An update of the same bin never returns on the
  ports.
- **Copy a collection through its iterator when other threads update it.** `toList`, `toSet`,
  `sorted` and `ArrayList(collection)` read the size, then the elements. They can throw or miss
  elements when the collection changes in between. `for`, `forEach`, `map` and `filter` use one
  iterator and are safe.
- **Keys and values of the map are not null**, as in the JDK.
- **Android.** `ConcurrentHashMap` is a class of the library there, not the platform class,
  because the platform class has no atomic computation before API 24. The other classes are
  the platform ones.
- **`lock()` ignores an interrupt**, as in Java. It keeps waiting, and the thread finds its
  flag set afterwards. `lockInterruptibly()` and `tryLock` with a time limit are the ones that
  an interrupt ends.
- **Native threads.** A native program ends when its main function returns, whether threads
  still run or not. Join the threads that must finish. An interrupt does not wake a thread up
  from a call to the system, like a read of a file.
- **JS and Wasm.** They have one thread, so they have no `Thread`. The collections work as
  elsewhere, and a lock never waits. A wait that could never end fails with
  `IllegalStateException`: `await` on a condition or on a latch that is not open, `acquire` on
  a semaphore without a permit, and taking the write lock while holding only the read lock. A
  wait with a time limit answers at once.

The [design notes](docs/design.md) explain each of these.

## Project state

Koncurrent is experimental. Its API may change before version 1.0.

The JVM, Android host, Apple simulator, macOS, JS and Wasm tests run on every build. The Linux,
Windows and Android native targets compile, and their tests have not run yet. They share the
native code of the Apple targets, except the small files of pthread calls.
[Testing](docs/testing.md) tells what runs where.

## Documentation

- [API reference][koncurrent_docs], generated by Dokka
- [Design](docs/design.md): how each class is built, what differs from the JDK and why
- [Benchmarks](docs/benchmarks.md): the figures against the JDK and Stately, and how they were
  taken
- [Testing](docs/testing.md): what is tested, on which target
- [Migrating to Koncurrent](docs/migration.md): from `java.util.concurrent`, Stately or atomicfu,
  and what changes on the way

## Contributing

Bug reports and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for how to
build the project, run its tests and its benchmarks.

## License

Koncurrent is licensed under the [MIT License](LICENSE).

The map, the locks, the conditions, the semaphore and the latch are ports of the
[JSR-166][jsr166] sources, written by Doug Lea with assistance from members of the JCP JSR-166
Expert Group, and released to the public domain under
[CC0](https://creativecommons.org/publicdomain/zero/1.0/).
