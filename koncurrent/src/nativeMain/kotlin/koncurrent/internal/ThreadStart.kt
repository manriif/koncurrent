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
@file:OptIn(ExperimentalForeignApi::class)

package koncurrent.internal

import koncurrent.Thread
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.staticCFunction
import kotlin.native.concurrent.ThreadLocal

/**
 * The [Thread] of the calling thread, once it has one. A thread that the library starts gets
 * its own as it starts. Any other thread gets one when it first asks for it.
 */
@ThreadLocal
internal var CurrentThread: Thread? = null

/**
 * Starts a thread of the system that runs [thread].
 *
 * @return `false` if the system could not create the thread
 */
internal fun startThread(thread: Thread): Boolean {
    val reference = StableRef.create(thread)
    val created = createDetachedThread(staticCFunction(::runThread), reference.asCPointer())

    if (!created) {
        reference.dispose()
    }

    return created
}

// What a new thread runs. The argument is the stable reference to its Thread.
private fun runThread(argument: COpaquePointer?): COpaquePointer? {
    val reference = checkNotNull(argument).asStableRef<Thread>()
    val thread = reference.get()

    reference.dispose()
    CurrentThread = thread
    thread.run()

    return null
}

/**
 * Creates a thread of the system that calls [routine] with [argument], and that frees what it
 * holds when it ends. The type of a pthread differs between the native targets.
 *
 * @return `false` if the system could not create the thread
 */
internal expect fun createDetachedThread(
    routine: CPointer<CFunction<(COpaquePointer?) -> COpaquePointer?>>,
    argument: COpaquePointer,
): Boolean
