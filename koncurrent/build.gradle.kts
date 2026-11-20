/**
 * Copyright (C) 2026 Maanrifa Bacar Ali
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    alias(libs.plugins.android.multiplatformLibrary)
    alias(libs.plugins.kotlinx.benchmark)
    alias(konventionsLibs.plugins.common)
    alias(konventionsLibs.plugins.kmp)
}

local {
    name = "Koncurrent"
    description = "Thread and the classes of java.util.concurrent for Kotlin Multiplatform."
    namespace = "koncurrent"
}

kotlin {
    allTargets(browser = false)

    compilerOptions {
        optIn.add("koncurrent.InternalForInheritanceKoncurrentApi")
        optIn.add("kotlin.concurrent.atomics.ExperimentalAtomicApi")
    }

    sourceSets {
        jvmTest.dependencies {
            implementation(libs.lincheck)
        }
    }
}

///////////////////////////////////////////////////////////////////////////
// Benchmarks
///////////////////////////////////////////////////////////////////////////

val BenchmarkCompilation = "benchmark"

// Matches the qualified names of the lock benchmarks
val LockBenchmarks = """benchmarks\.Lock"""

val nativeBenchmark = tasks.register("nativeBenchmark") {
    group = "benchmark"
    description = "Runs the native benchmarks on the target of this machine"
}

kotlin {
    // The benchmarks are compiled apart from the library and never published. They see its
    // internal declarations, as its tests do.
    jvm {
        compilations.create(BenchmarkCompilation) {
            associateWith(target.compilations.getByName(KotlinCompilation.MAIN_COMPILATION_NAME))
        }
    }

    val commonBenchmark = sourceSets.create("commonBenchmark") {
        dependencies {
            implementation(libs.stately.concurrentCollections)
        }
    }

    sourceSets.named("jvmBenchmark") {
        dependsOn(commonBenchmark)

        dependencies {
            implementation(libs.kotlinx.benchmarkRuntime)
        }
    }

    val nativeBenchmarkSources = sourceSets.create("nativeBenchmark") {
        dependsOn(commonBenchmark)
    }

    // The pthread types of Windows are not those of the other hosts
    val hostFamily = if (HostManager.hostIsMingw) "mingw" else "unix"

    val hostBenchmarkSources = sourceSets.create("${hostFamily}Benchmark") {
        dependsOn(nativeBenchmarkSources)
    }

    // The native benchmarks run on the machine that builds them, so only its target has them
    val hostTargets = targets.withType<KotlinNativeTarget>().matching { target ->
        target.konanTarget == HostManager.host
    }

    hostTargets.configureEach {
        val benchmark = compilations.create(BenchmarkCompilation) {
            associateWith(target.compilations.getByName(KotlinCompilation.MAIN_COMPILATION_NAME))
            defaultSourceSet.dependsOn(hostBenchmarkSources)
        }

        binaries.executable(BenchmarkCompilation, listOf(NativeBuildType.RELEASE)) {
            compilation = benchmark
            entryPoint = "koncurrent.benchmarks.main"

            val run = runTaskProvider

            if (run != null) {
                nativeBenchmark.configure { dependsOn(run) }
            }
        }
    }
}

benchmark {
    targets {
        register("jvmBenchmark")
    }

    configurations {
        named("main") {
            exclude(LockBenchmarks)
        }

        register("locks") {
            include(LockBenchmarks)
        }

        configureEach {
            warmups = 3
            iterations = 5
            iterationTime = 2
            iterationTimeUnit = "s"
            mode = "thrpt"
            outputTimeUnit = "s"
        }
    }
}

// The benchmarks run on a newer JVM than the one the library is compiled for. The benchmark
// plugin gives its tasks the JVM of the project when it creates them, so this must come after.
afterEvaluate {
    tasks.withType<JavaExec>().configureEach {
        javaLauncher = javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(libs.versions.jvm.toolchain.benchmark.get())
        }
    }
}
