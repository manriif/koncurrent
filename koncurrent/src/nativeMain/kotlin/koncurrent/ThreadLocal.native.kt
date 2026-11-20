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
@file:OptIn(ExperimentalNativeApi::class)

package koncurrent

import koncurrent.internal.CurrentThreadValues
import koncurrent.internal.ThreadLocalKey
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner

public actual class ThreadLocal<T> {

    private val key = ThreadLocalKey()

    // Lets the threads drop the values of this once it is no longer referenced
    @Suppress("unused")
    private val cleaner = createCleaner(key) { it.dead.store(true) }

    // Only this stores values under its key, and they are all of type T
    @Suppress("UNCHECKED_CAST")
    public actual fun get(): T? = CurrentThreadValues[key] as T?

    public actual fun set(value: T) {
        CurrentThreadValues[key] = value
    }

    public actual fun remove(): Unit = CurrentThreadValues.remove(key)
}
