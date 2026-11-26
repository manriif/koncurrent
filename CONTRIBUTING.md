# Contributing

Bug reports and pull requests are welcome. For anything beyond a small fix, open an issue first
to discuss the approach.

Two things make a change of a port harder than it looks.

- **Concurrent code is easy to get wrong in ways that tests miss.** A change must come with the
  reasoning that makes it correct, and with a test that fails without it when one can be
  written.
- **The ports are tuned against benchmarks.** A few instructions more on a hot path, or a field
  that moves in its object, cost up to 30% in the measurements of
  [docs/benchmarks.md](docs/benchmarks.md#evolution). A change of a hot path must come with its
  figures, taken as [Benchmarks](#benchmarks) tells.

Match the surrounding code: its naming, its comment density and its layout.

## Building

The build needs:

- **A JDK**, 17 or later, to run Gradle. Gradle downloads the JDKs that the build and the
  benchmarks ask for when the machine lacks them.
- **The Android SDK.** Set `sdk.dir` in `local.properties`, or `ANDROID_HOME`.
- **macOS**, for the Apple targets. On Linux and Windows the build skips them.

Node.js and the Kotlin/Native compiler are downloaded by the build.

The build does the same thing on macOS, Linux and Windows. Each host runs the native tests and
the native benchmarks of its own target.

| Goal | Command | Time |
|---|---|---:|
| Build every target and run all the tests | `./gradlew build` | 13 min |
| JVM tests, Lincheck included | `./gradlew :koncurrent:jvmTest` | 12 min |
| Android tests on the host | `./gradlew :koncurrent:testAndroidHostTest` | 1 min |
| Native tests on macOS | `./gradlew :koncurrent:macosArm64Test` | 2 min |
| Native tests on Linux | `./gradlew :koncurrent:linuxX64Test` | |
| Native tests on Windows | `./gradlew :koncurrent:mingwX64Test` | |
| JS and Wasm tests | `./gradlew :koncurrent:jsNodeTest :koncurrent:wasmJsNodeTest :koncurrent:wasmWasiNodeTest` | 5 s |
| API reference, in `docs/api/<version>` | `./gradlew :dokkaGenerate` | 10 s |
| Publish to the local Maven repository | `./gradlew publishToMavenLocal` | 10 s after a build |

The times are those of an Apple M-series laptop. The Gradle daemon gets 8 GB, because the build
compiles about twenty native targets at once.

## Layout

| Path | Content |
|---|---|
| `koncurrent` | The library and its benchmarks |
| `docs` | The documents that the README lists. Dokka writes the API reference to `docs/api`. |

The targets, the compiler options, the publication and Dokka are set up by
[Konventions](https://github.com/manriif/konventions). `settings.gradle.kts` names its version.

The source sets of the library follow the platforms.

| Source set | Targets | Content |
|---|---|---|
| `common` | All | The public declarations, the map port, the list, the ports of the queues, the sorted table and the lock that they share |
| `java` | JVM, Android | The type aliases to the Java classes |
| `concurrent` | JVM, Android, native | What needs threads: `Thread`, the ports of the locks, of the stamped lock, of the semaphore, of the latch and of the barrier, and their queue |
| `nonJava` | Native, JS, Wasm | The public classes that wrap the ports |
| `native` | Native | The native `Thread`, sleeping and waking up threads, the thread local table |
| `unix`, `apple`, `linux`, `androidNative`, `mingw` | Native families | The pthread calls, whose types differ by family |
| `nonConcurrent` | JS, wasmJs, wasmWasi | The locks and synchronizers for one thread |

The ports are internal, in `koncurrent.internal`.
[docs/design.md](docs/design.md#where-the-ports-live) tells why they are compiled for the JVM
too.

## Rules for the ports

- **Port from the public domain sources of JSR-166**, at
  <https://gee.cs.oswego.edu/dl/concurrency-interest/>. Never from OpenJDK, which is under the
  GPL. Check the header of each file: some files of JSR-166 carry a Sun or Oracle copyright,
  and those must be written from scratch.
- **Keep the algorithm.** A change of the algorithm must be measured, and explained in
  [docs/design.md](docs/design.md).
- **Say where a port differs** from the original, in the comment of the class.
- **Say where a file comes from**, in a note under its license header, as the ports do. A port
  names the class of JSR-166 that it is ported from. A class that is written from scratch for
  the reason above says so.

## Tests

A public class and the port behind it share their test suites, so that the same expectations
run on the Java classes and on the ports. [docs/testing.md](docs/testing.md) lists the suites.

- The tests of one thread go to `commonTest`. They run on every target.
- The tests with several threads go to `concurrentTest`. They run on the JVM, Android and
  native.
- A port gets a Lincheck test in `jvmTest`.

## Public API

The library uses the explicit API mode, and warnings are errors.

The public API is recorded in `koncurrent/api`. The build fails when the API no longer matches
the record. After an intended change, update the record with `./gradlew updateKotlinAbi` and
commit it.

## Lock file of the JS tests

`kotlin-js-store/yarn.lock` pins the npm packages that the JS and Wasm tests run with. It is
committed, and the build fails when it no longer matches. After an upgrade of Kotlin, update it
with `./gradlew kotlinUpgradeYarnLock kotlinWasmUpgradeYarnLock` and commit it.

## Releasing

The version is `koncurrent` in `gradle/libs.versions.toml`.

A release is a tag made of that version, like `0.1.0`. Pushing the tag runs two workflows:

- `Publish to Maven Central` builds every target on macOS, signs the artifacts and releases
  them.
- `Deploy Dokka to GitHub Pages` adds the API reference of that version to the `gh-pages`
  branch, and makes it the one that the documentation link opens.

Both stop at once when the tag is not the version of the catalog.

The workflows read these secrets, the first four from an environment named `publishing`:
`MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `SIGNING_KEY`, `SIGNING_KEY_PASSWORD` and
`GRADLE_ENCRYPTION_KEY`.

Before a release, run `./gradlew build` and `./gradlew publishToMavenLocal`, and check that a
project resolves the library from the local repository.

## Benchmarks

| Goal | Command | Time |
|---|---|---:|
| JVM map benchmarks | `./gradlew :koncurrent:jvmBenchmarkBenchmark` | 18 min |
| JVM lock benchmarks | `./gradlew :koncurrent:jvmBenchmarkLocksBenchmark` | 10 min |
| Native map, lock, queue and sorted map benchmarks | `./gradlew :koncurrent:nativeBenchmark` | 22 min |

The benchmarks are compiled apart, in `benchmark` compilations of the `jvm` target and of the
native target of the machine: `macosArm64`, `macosX64`, `linuxX64` or `mingwX64`. They are
never published. They see the internal declarations of the library, as its tests do, which is
how the JVM benchmarks measure the ports. Stately is a dependency of the benchmarks only. The
end of `koncurrent/build.gradle.kts` sets all of this up.

Their sources are in `commonBenchmark`, `jvmBenchmark` and `nativeBenchmark`. The pthread
mutex that the locks are compared with is in `unixBenchmark` and in `mingwBenchmark`, since its
types differ on Windows.

The native benchmark is built in
`koncurrent/build/bin/<target>/benchmarkReleaseExecutable`, as `benchmark.kexe`, or
`benchmark.exe` on Windows. Run it with `maps`, `locks`, `queues` or `sorted` to measure one
group. It prints one CSV line for each benchmark. JMH writes its reports to
`koncurrent/build/reports/benchmarks`. A second run replaces the report of the first one when
Gradle reuses its configuration, so copy a report before running again.

Run the benchmarks on an idle machine, at least twice. Figures taken in two sessions do not
compare. To tell what a change costs, build the program of before and the program of after,
and run them in turn, several times. [docs/benchmarks.md](docs/benchmarks.md) has the last
results.
