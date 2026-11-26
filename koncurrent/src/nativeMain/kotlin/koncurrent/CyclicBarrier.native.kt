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

import koncurrent.internal.PortableCyclicBarrier

public actual class CyclicBarrier actual constructor(parties: Int, barrierAction: Runnable?) {

    private val barrier = PortableCyclicBarrier(parties, barrierAction)

    public actual constructor(parties: Int) : this(parties, null)

    public actual fun getParties(): Int = barrier.parties

    @IgnorableReturnValue
    public actual fun await(): Int = barrier.await()

    internal fun await(nanos: Long): Int = barrier.await(nanos)

    public actual fun isBroken(): Boolean = barrier.isBroken()

    public actual fun reset(): Unit = barrier.reset()

    public actual fun getNumberWaiting(): Int = barrier.numberWaiting()
}
