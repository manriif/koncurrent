# Module Koncurrent

`Thread` and the classes of `java.util.concurrent` for Kotlin Multiplatform, with the same names
and the same behavior on every target.

On the JVM and Android they are the Java classes themselves. On the other targets they are
ports of the JSR-166 sources, or code written for this library.

# Package koncurrent

Threads, concurrent collections, synchronizers and thread local variables.

# Package koncurrent.locks

Locks that block threads, and the conditions that threads wait on.
