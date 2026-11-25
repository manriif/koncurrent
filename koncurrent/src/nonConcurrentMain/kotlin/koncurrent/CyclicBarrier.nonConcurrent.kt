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

import koncurrent.internal.ENDLESS_WAIT

// JS and Wasm have one thread, which is alone at the barrier. It only passes a barrier of one
// party.
public actual class CyclicBarrier actual constructor(
    private val parties: Int,
    private val barrierAction: Runnable?,
) {

    private var broken = false

    init {
        require(parties > 0) { "parties must be positive, was $parties" }
    }

    public actual constructor(parties: Int) : this(parties, null)

    public actual fun getParties(): Int = parties

    // No other thread could arrive, so the wait would never end
    @IgnorableReturnValue
    public actual fun await(): Int {
        checkNotBroken()
        check(parties == 1) { ENDLESS_WAIT }

        return pass()
    }

    // The time of a wait for another party runs out at once, which breaks the barrier as it
    // does elsewhere
    internal fun awaitBriefly(): Int {
        checkNotBroken()

        if (parties > 1) {
            broken = true
            throw TimeoutException()
        }

        return pass()
    }

    private fun checkNotBroken() {
        if (broken) {
            throw BrokenBarrierException()
        }
    }

    // The barrier breaks when its action fails
    private fun pass(): Int {
        try {
            barrierAction?.run()
        } catch (failure: Throwable) {
            broken = true
            throw failure
        }

        return 0
    }

    public actual fun isBroken(): Boolean = broken

    public actual fun reset() {
        broken = false
    }

    public actual fun getNumberWaiting(): Int = 0
}
