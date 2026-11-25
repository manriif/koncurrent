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

/**
 * Orders what the calling thread read before this call ahead of the atomic that it reads next.
 * It is the load fence of JSR-166, which Kotlin does not have.
 *
 * A thread that reads without a lock reads a version, then the data, then the version again.
 * The last read may come before the ones in the middle, which then prove nothing. Some
 * processors run them in that order. On every processor the compiler may too, since LLVM is
 * allowed to move a plain read past the read of an atomic. A write of an atomic comes after
 * the reads made before it, and before the read of an atomic that follows, for both of them.
 * So this writes one.
 *
 * It does nothing where there is one thread.
 */
internal expect fun loadFence()
