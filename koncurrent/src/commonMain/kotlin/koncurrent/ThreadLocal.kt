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
package koncurrent

/**
 * A variable that holds one value for each thread. A thread only reads and writes its own
 * value.
 *
 * The value of a thread is dropped when the thread ends, and when this thread local is no
 * longer referenced.
 *
 * ### Platforms
 *
 * - On the JVM and Android this is `java.lang.ThreadLocal`.
 * - On native each thread keeps its values in a table of its own.
 * - JS and Wasm have one thread. This is a plain variable there.
 *
 * @param T the type of the value
 */
public expect class ThreadLocal<T>() {

    /**
     * Returns the value of the current thread, or `null` if this thread has set none.
     */
    public fun get(): T?

    /**
     * Sets the value of the current thread.
     */
    public fun set(value: T)

    /**
     * Drops the value of the current thread. [get] returns `null` afterwards.
     */
    public fun remove()
}

/**
 * Returns the value of the current thread. If this thread has set none, calls [default], sets
 * its result as the value and returns it.
 *
 * It is the usual way to give each thread its own instance of an object that is not thread
 * safe.
 */
public inline fun <T : Any> ThreadLocal<T>.getOrSet(default: () -> T): T {
    return get() ?: default().also(::set)
}
