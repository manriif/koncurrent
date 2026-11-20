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
@file:Suppress("RedundantVisibilityModifier", "unused")

package koncurrent.benchmarks

import co.touchlab.stately.collections.ConcurrentMutableMap
import koncurrent.internal.PortableConcurrentHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * The maps under test on the JVM. The map of the library is the JDK one there, so the port that
 * backs it on the other platforms is measured in its place.
 */
public enum class Implementation {

    Koncurrent {

        override fun newMap(): MutableMap<Key, Key> = PortableConcurrentHashMap()
    },

    Jdk {

        override fun newMap(): MutableMap<Key, Key> = ConcurrentHashMap()
    },

    Stately {

        override fun newMap(): MutableMap<Key, Key> = ConcurrentMutableMap()
    };

    public abstract fun newMap(): MutableMap<Key, Key>
}
