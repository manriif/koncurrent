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
package koncurrent.locks

import koncurrent.InternalForInheritanceKoncurrentApi

/**
 * A pair of locks over the same data: one to read, that many threads may hold at once, and one
 * to write, that one thread holds alone.
 *
 * It is the common type of [ReentrantReadWriteLock] and of the view that
 * [StampedLock.asReadWriteLock] returns.
 *
 * This interface is not meant to be implemented outside of this library. On the JVM and Android
 * it is `java.util.concurrent.locks.ReadWriteLock`.
 */
@SubclassOptInRequired(InternalForInheritanceKoncurrentApi::class)
public expect interface ReadWriteLock {

    /**
     * Returns the lock used for reading.
     */
    public fun readLock(): Lock

    /**
     * Returns the lock used for writing.
     */
    public fun writeLock(): Lock
}
