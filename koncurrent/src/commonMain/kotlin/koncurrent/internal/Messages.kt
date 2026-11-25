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
package koncurrent.internal

internal const val LOCK_NOT_HELD = "The current thread does not hold the lock"
internal const val READ_LOCK_NOT_HELD = "The current thread does not hold the read lock"
internal const val WRITE_LOCK_NOT_HELD = "The current thread does not hold the write lock"
internal const val STAMP_NOT_HELD = "The stamp is not the one of a lock that is held"
internal const val NO_READ_LOCK_HELD = "No thread holds the read lock"
internal const val NO_WRITE_LOCK_HELD = "No thread holds the write lock"

// The messages that the queues of the JDK have
internal const val QUEUE_FULL = "Queue full"
internal const val DEQUE_FULL = "Deque full"

internal const val ADDED_TO_ITSELF = "A queue cannot be added to itself"
internal const val DRAINED_TO_ITSELF = "A queue cannot be drained to itself"
