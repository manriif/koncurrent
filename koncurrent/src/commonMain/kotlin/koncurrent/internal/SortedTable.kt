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
// Kotlin/Native gives each function its own frame of GC roots, even where LLVM inlines it. The
// small functions of the hot paths are inlined by Kotlin, which merges their frames.
@file:Suppress("NOTHING_TO_INLINE")

package koncurrent.internal

import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.incrementAndFetch

/**
 * The entry before a key.
 */
internal const val LOWER = 0

/**
 * The entry of a key, or else the one before it.
 */
internal const val FLOOR = 1

/**
 * The entry of a key, or else the one after it.
 */
internal const val CEILING = 2

/**
 * The entry after a key.
 */
internal const val HIGHER = 3

// What a read without the lock looks for, besides the four relations above
private const val EXACT = 4
private const val FIRST = 5
private const val LAST = 6

private const val CHUNK_CAPACITY = 64

// Two neighbours become one chunk when they hold this at most together. The chunk that they
// make is half full, so that it does not split at the next insertion.
private const val MERGED_SIZE = CHUNK_CAPACITY / 2

private const val INITIAL_CHUNK_COUNT = 8

/**
 * The entries of a sorted map, in the order of their keys, that one lock guards. It backs
 * the public `ConcurrentSkipListMap` and `ConcurrentSkipListSet` where the platform has none.
 *
 * It is not the skip list of JSR-166, which is lock-free and takes several objects with
 * atomic fields for each entry. What costs on Kotlin/Native is the allocation, as the queues
 * showed. So the entries are in chunks of [CHUNK_CAPACITY], two arrays each, and a map of a
 * million entries is under a hundred thousand objects, where the skip list has several
 * millions.
 *
 * A key is found with two binary searches, among the first keys of the chunks, then in a
 * chunk. An insertion moves the entries of one chunk at most. A full chunk is split in two,
 * and two neighbours that got small are merged.
 *
 * A change takes the lock. A read of one entry does not, so that readers do not wait for each
 * other. It searches the chunks as they are, which may be while a thread changes them, then
 * checks that none did. The table counts its changes for that, in [version], as a stamped
 * lock counts its writes. A read that cannot tell is made again with the lock.
 *
 * An iteration takes the lock once for each batch of entries, and finds its way again from
 * the last key that it gave, so it never fails when the map changes.
 */
internal class SortedTable<K : Any, V : Any>(val comparator: Comparator<in K>?) {

    private val lock = InternalLock()

    // The lock guards the chunks and the counts. The chunks are in the order of their first
    // keys, and none is empty.
    private var chunks = arrayOfNulls<Chunk>(INITIAL_CHUNK_COUNT)
    private var chunkCount = 0
    private var entryCount = 0

    // Where the last search ended: a chunk and a place in it. Only read with the lock held.
    private var chunkAt = 0
    private var indexAt = 0

    // Counts the changes of the chunks, twice each, so it is odd while a thread makes one. A
    // read without the lock is right when it finds the same even number before and after.
    private val version = AtomicLong(0L)

    val size: Int
        get() = lock.locked { entryCount }

    /**
     * Compares two keys in the order of this table.
     *
     * @throws ClassCastException if the table has no comparator and [first] is not comparable
     */
    @Suppress("UNCHECKED_CAST", "IfThenToElvis")
    fun compare(first: K, second: K): Int {
        val comparator = comparator

        return if (comparator != null) {
            comparator.compare(first, second)
        } else {
            (first as Comparable<K>).compareTo(second)
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // One key
    ///////////////////////////////////////////////////////////////////////////

    fun get(key: K): V? = read(key, EXACT) { if (locate(key)) valueAtCursor() else null }

    /**
     * Maps [key] to [value], unless [onlyIfAbsent] and the key already has a value. Returns the
     * value that the key had.
     */
    fun put(key: K, value: V, onlyIfAbsent: Boolean): V? = lockedFor(key) {
        if (locate(key)) {
            val previous = valueAtCursor()

            if (!onlyIfAbsent) {
                change { chunk(chunkAt).values[indexAt] = value }
            }

            return previous
        }

        if (entryCount == 0) {
            // Nothing told yet whether the key can be compared, as the first one of the JDK
            val _ = compare(key, key)
        }

        change { insertAtCursor(key, value) }
        null
    }

    fun remove(key: K): V? = lockedFor(key) {
        if (!locate(key)) {
            return null
        }

        val previous = valueAtCursor()

        change { removeAtCursor() }
        previous
    }

    /**
     * Removes [key] if it is mapped to [value].
     */
    fun remove(key: K, value: V): Boolean = lockedFor(key) {
        if (!locate(key) || valueAtCursor() != value) {
            return false
        }

        change { removeAtCursor() }
        true
    }

    /**
     * Maps [key] to [value] if it has a value. Returns the value that it had.
     */
    fun replace(key: K, value: V): V? = lockedFor(key) {
        if (!locate(key)) {
            return null
        }

        val previous = valueAtCursor()

        change { chunk(chunkAt).values[indexAt] = value }
        previous
    }

    /**
     * Maps [key] to [newValue] if it is mapped to [oldValue].
     */
    fun replace(key: K, oldValue: V, newValue: V): Boolean = lockedFor(key) {
        if (!locate(key) || valueAtCursor() != oldValue) {
            return false
        }

        change { chunk(chunkAt).values[indexAt] = newValue }
        true
    }

    fun clear() {
        lock.locked {
            change {
                chunks = arrayOfNulls(INITIAL_CHUNK_COUNT)
                chunkCount = 0
                entryCount = 0
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Neighbours
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Returns the entry that is the closest to [key] in the way that [relation] tells:
     * [LOWER], [FLOOR], [CEILING] or [HIGHER]. Returns `null` when there is none.
     */
    fun near(key: K, relation: Int): SortedEntry<K, V>? = read(key, relation) {
        if (locateNear(key, relation)) entryAtCursor() else null
    }

    fun first(): SortedEntry<K, V>? = read(null, FIRST) {
        if (locateFirst()) entryAtCursor() else null
    }

    fun last(): SortedEntry<K, V>? = read(null, LAST) {
        if (locateLast()) entryAtCursor() else null
    }

    /**
     * Removes the first entry and returns it, or returns `null` when the table is empty.
     */
    fun pollFirst(): SortedEntry<K, V>? = lock.locked {
        if (!locateFirst()) {
            return null
        }

        val entry = entryAtCursor()

        change { removeAtCursor() }
        entry
    }

    /**
     * Removes the last entry and returns it, or returns `null` when the table is empty.
     */
    fun pollLast(): SortedEntry<K, V>? = lock.locked {
        if (!locateLast()) {
            return null
        }

        val entry = entryAtCursor()

        change { removeAtCursor() }
        entry
    }

    /**
     * Counts the entries from [low] to [high]. A bound that is `null` is open, and the flags
     * tell whether a key that equals its bound counts.
     */
    fun count(low: K?, lowInclusive: Boolean, high: K?, highInclusive: Boolean): Int = lock.locked {
        val start = when {
            low == null -> 0
            locateNear(low, if (lowInclusive) CEILING else HIGHER) -> rankOfCursor()
            else -> return 0
        }

        val end = when {
            high == null -> entryCount
            locateNear(high, if (highInclusive) FLOOR else LOWER) -> rankOfCursor() + 1
            else -> return 0
        }

        maxOf(end - start, 0)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Batches, for the iterators
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Copies the entries that follow [from] into [keys] and [values], in ascending order, as
     * many as the arrays hold. The first ones are copied when [from] is `null`. [inclusive]
     * tells whether the entry of [from] itself counts. Returns the number of entries copied.
     */
    fun copyAscending(
        from: K?,
        inclusive: Boolean,
        keys: Array<Any?>,
        values: Array<Any?>,
    ): Int = lock.locked {
        val found = when (from) {
            null -> locateFirst()
            else -> locateNear(from, if (inclusive) CEILING else HIGHER)
        }

        var copied = 0
        var more = found

        while (more && copied < keys.size) {
            val chunk = chunk(chunkAt)

            keys[copied] = chunk.keys[indexAt]
            values[copied] = chunk.values[indexAt]
            copied++
            more = stepForward()
        }

        copied
    }

    /**
     * Copies the entries that precede [from] into [keys] and [values], in descending order, as
     * many as the arrays hold. The last ones are copied when [from] is `null`. [inclusive]
     * tells whether the entry of [from] itself counts. Returns the number of entries copied.
     */
    fun copyDescending(
        from: K?,
        inclusive: Boolean,
        keys: Array<Any?>,
        values: Array<Any?>,
    ): Int = lock.locked {
        val found = when (from) {
            null -> locateLast()
            else -> locateNear(from, if (inclusive) FLOOR else LOWER)
        }

        var copied = 0
        var more = found

        while (more && copied < keys.size) {
            val chunk = chunk(chunkAt)

            keys[copied] = chunk.keys[indexAt]
            values[copied] = chunk.values[indexAt]
            copied++
            more = stepBack()
        }

        copied
    }

    ///////////////////////////////////////////////////////////////////////////
    // Reads without the lock
    ///////////////////////////////////////////////////////////////////////////

    // Returns what a read without the lock found, or what [locked] finds with the lock when
    // that read could not tell
    @Suppress("UNCHECKED_CAST")
    private inline fun <T : Any> read(key: K?, relation: Int, locked: () -> T?): T? {
        var found = readUnlocked(key, relation)

        if (found === Unstable) {
            // Once more before the lock, which the readers would then wait for in turn
            found = readUnlocked(key, relation)
        }

        return when {
            found === Unstable -> lock.locked(locked)
            found === Absent -> null
            else -> found as T
        }
    }

    /**
     * Searches the chunks without the lock, then checks that no thread changed them meanwhile.
     *
     * Returns the value of [key] for [EXACT], else the entry that [relation] tells, or
     * [Absent]. Returns [Unstable] when a thread changed the chunks, or when the search failed,
     * be it on what it read or on a key that cannot be compared. The search with the lock then
     * tells which.
     */
    private fun readUnlocked(key: K?, relation: Int): Any {
        val seen = version.load()

        if ((seen and 1L) != 0L) {
            return Unstable
        }

        val found = try {
            searchUnlocked(key, relation)
        } catch (_: Throwable) {
            return Unstable
        }

        // Orders the reads of the search before the read of the version that follows
        loadFence()

        return if (version.load() == seen) found else Unstable
    }

    /**
     * Searches as [locate] and [locateNear] do, on what may be half of a change. So it trusts
     * nothing that it reads. A chunk or a key that is missing ends it, since a key must not
     * reach the comparison as `null`. An index that is out of its array throws, which the
     * caller catches. Whatever it returns only counts once the version is found unchanged.
     */
    @Suppress("UNCHECKED_CAST")
    private fun searchUnlocked(key: K?, relation: Int): Any {
        val chunks = chunks
        val count = minOf(chunkCount, chunks.size)

        if (count == 0) {
            return Absent
        }

        var at = 0
        var index = 0
        var exact = relation == FIRST

        if (relation == LAST) {
            at = count - 1
            index = (chunks[at] ?: return Unstable).size - 1
            exact = true
        } else if (key != null) {
            var low = 0
            var high = count - 1

            // The last chunk whose first key is not after the key
            while (low <= high) {
                val middle = (low + high) ushr 1
                val first = chunks[middle]?.keys?.get(0) ?: return Unstable

                if (compare(first as K, key) <= 0) {
                    at = middle
                    low = middle + 1
                } else {
                    high = middle - 1
                }
            }

            val chunk = chunks[at] ?: return Unstable

            low = 0
            high = chunk.size - 1

            while (low <= high && !exact) {
                val middle = (low + high) ushr 1
                val order = compare((chunk.keys[middle] ?: return Unstable) as K, key)

                if (order < 0) {
                    low = middle + 1
                } else if (order > 0) {
                    high = middle - 1
                } else {
                    low = middle
                    exact = true
                }
            }

            index = low
        }

        // The place of a key that is not there is the one of the entry after it
        when (relation) {
            EXACT -> if (!exact) return Absent
            FLOOR -> if (!exact) index--
            LOWER -> index--
            HIGHER -> if (exact) index++
        }

        // A place before or after its chunk is in the neighbour
        if (index < 0) {
            if (--at < 0) {
                return Absent
            }

            index = (chunks[at] ?: return Unstable).size - 1
        } else if (index >= (chunks[at] ?: return Unstable).size) {
            if (++at >= count) {
                return Absent
            }

            index = 0
        }

        val chunk = chunks[at] ?: return Unstable
        val foundKey = chunk.keys[index] ?: return Unstable
        val value = chunk.values[index] ?: return Unstable

        return if (relation == EXACT) value else SortedEntry(foundKey as K, value as V)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Searches, with the lock held. They leave the cursor on what they find.
    ///////////////////////////////////////////////////////////////////////////

    private inline fun chunk(index: Int): Chunk = chunks[index]!!

    @Suppress("UNCHECKED_CAST")
    private inline fun keyAt(chunk: Chunk, index: Int): K = chunk.keys[index] as K

    @Suppress("UNCHECKED_CAST")
    private inline fun valueAtCursor(): V = chunk(chunkAt).values[indexAt] as V

    private fun entryAtCursor(): SortedEntry<K, V> {
        return SortedEntry(keyAt(chunk(chunkAt), indexAt), valueAtCursor())
    }

    // Puts the cursor where [key] is, or where it would be inserted, which may be right after
    // the last entry of a chunk. Returns whether the key is there.
    private fun locate(key: K): Boolean {
        var low = 0
        var high = chunkCount - 1
        var found = 0

        // The last chunk whose first key is not after the key
        while (low <= high) {
            val middle = (low + high) ushr 1

            if (compare(keyAt(chunk(middle), 0), key) <= 0) {
                found = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }

        chunkAt = found
        indexAt = 0

        if (chunkCount == 0) {
            return false
        }

        val chunk = chunk(found)

        low = 0
        high = chunk.size - 1

        while (low <= high) {
            val middle = (low + high) ushr 1
            val order = compare(keyAt(chunk, middle), key)

            if (order < 0) {
                low = middle + 1
            } else if (order > 0) {
                high = middle - 1
            } else {
                indexAt = middle
                return true
            }
        }

        indexAt = low
        return false
    }

    // Puts the cursor on the entry that is the closest to [key] in the way of [relation].
    // Returns whether there is one.
    private fun locateNear(key: K, relation: Int): Boolean {
        val exact = locate(key)

        if (chunkCount == 0) {
            return false
        }

        return when (relation) {
            FLOOR -> exact || stepBack()
            LOWER -> stepBack()
            CEILING -> exact || onEntryOrNext()
            else -> if (exact) stepForward() else onEntryOrNext()
        }
    }

    // The cursor of a key that is not there is the place of the next entry, which may be the
    // first one of the next chunk
    private fun onEntryOrNext(): Boolean {
        if (indexAt < chunk(chunkAt).size) {
            return true
        }

        indexAt = 0
        return ++chunkAt < chunkCount
    }

    private fun locateFirst(): Boolean {
        chunkAt = 0
        indexAt = 0
        return chunkCount > 0
    }

    private fun locateLast(): Boolean {
        if (chunkCount == 0) {
            return false
        }

        chunkAt = chunkCount - 1
        indexAt = chunk(chunkAt).size - 1
        return true
    }

    private fun stepForward(): Boolean {
        if (++indexAt < chunk(chunkAt).size) {
            return true
        }

        indexAt = 0
        return ++chunkAt < chunkCount
    }

    private fun stepBack(): Boolean {
        if (--indexAt >= 0) {
            return true
        }

        if (--chunkAt < 0) {
            return false
        }

        indexAt = chunk(chunkAt).size - 1
        return true
    }

    // The number of entries before the cursor
    private fun rankOfCursor(): Int {
        var rank = indexAt

        for (index in 0..<chunkAt) {
            rank += chunk(index).size
        }

        return rank
    }

    ///////////////////////////////////////////////////////////////////////////
    // Changes, with the lock held
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Runs [action] with the lock held, to change the entry of [key].
     *
     * A thread that finds the lock taken first searches for the key without it. The search with
     * the lock then finds what it reads in the cache of its processor, and the search is what
     * a change holds the lock for the longest. So the threads that wait shorten the wait of
     * the next ones. A thread that finds the lock free takes it at once.
     */
    private inline fun <T> lockedFor(key: K, action: () -> T): T {
        if (!lock.tryLock()) {
            val _ = readUnlocked(key, EXACT)
            lock.lock()
        }

        try {
            return action()
        } finally {
            lock.unlock()
        }
    }

    /**
     * Makes a change of the chunks known to the threads that read without the lock.
     *
     * The version is read again once it is updated, as the stamp of a write lock is. On some
     * processors the update may reach the other threads after the writes that follow it, and
     * a reader would then find the new entries with the old version. A read of an atomic comes
     * after the update, and the writes that follow come after that read.
     */
    private inline fun change(action: () -> Unit) {
        val _ = version.incrementAndFetch()
        val odd = version.load()

        action()
        version.store(odd + 1L)
    }

    // Inserts an entry at the cursor of a key that is not there
    private fun insertAtCursor(key: K, value: V) {
        if (chunkCount == 0) {
            insertChunk(0, Chunk())
        }

        var chunk = chunk(chunkAt)
        var index = indexAt

        if (chunk.size == CHUNK_CAPACITY) {
            val upper = split(chunk)

            insertChunk(chunkAt + 1, upper)

            // The entry goes to the end of the lower half rather than to the start of the
            // upper one, whose first key then stays what it is
            if (index > chunk.size) {
                index -= chunk.size
                chunk = upper
            }
        }

        val _ = chunk.keys.copyInto(chunk.keys, index + 1, index, chunk.size)
        val _ = chunk.values.copyInto(chunk.values, index + 1, index, chunk.size)

        chunk.keys[index] = key
        chunk.values[index] = value
        chunk.size++
        entryCount++
    }

    // Moves the upper half of a full [chunk] to a new one, and returns it
    private fun split(chunk: Chunk): Chunk {
        val upper = Chunk()
        val half = CHUNK_CAPACITY / 2

        val _ = chunk.keys.copyInto(upper.keys, 0, half, CHUNK_CAPACITY)
        val _ = chunk.values.copyInto(upper.values, 0, half, CHUNK_CAPACITY)

        chunk.keys.fill(null, half, CHUNK_CAPACITY)
        chunk.values.fill(null, half, CHUNK_CAPACITY)
        chunk.size = half
        upper.size = CHUNK_CAPACITY - half

        return upper
    }

    private fun removeAtCursor() {
        val chunk = chunk(chunkAt)
        val last = chunk.size - 1

        val _ = chunk.keys.copyInto(chunk.keys, indexAt, indexAt + 1, chunk.size)
        val _ = chunk.values.copyInto(chunk.values, indexAt, indexAt + 1, chunk.size)

        chunk.keys[last] = null
        chunk.values[last] = null
        chunk.size = last
        entryCount--

        if (last == 0) {
            removeChunk(chunkAt)
        } else if (last <= MERGED_SIZE / 2) {
            mergeAround(chunkAt)
        }
    }

    // Merges the chunk at [index] with a neighbour when both are small enough
    private fun mergeAround(index: Int) {
        val chunk = chunk(index)

        if (index + 1 < chunkCount && chunk.size + chunk(index + 1).size <= MERGED_SIZE) {
            append(chunk(index + 1), chunk)
            removeChunk(index + 1)
        } else if (index > 0 && chunk(index - 1).size + chunk.size <= MERGED_SIZE) {
            append(chunk, chunk(index - 1))
            removeChunk(index)
        }
    }

    // Moves the entries of [source] to the end of [target]
    private fun append(source: Chunk, target: Chunk) {
        val _ = source.keys.copyInto(target.keys, target.size, 0, source.size)
        val _ = source.values.copyInto(target.values, target.size, 0, source.size)

        target.size += source.size
    }

    private fun insertChunk(index: Int, chunk: Chunk) {
        if (chunkCount == chunks.size) {
            chunks = chunks.copyOf(chunkCount * 2)
        }

        val _ = chunks.copyInto(chunks, index + 1, index, chunkCount)

        chunks[index] = chunk
        chunkCount++
    }

    private fun removeChunk(index: Int) {
        val _ = chunks.copyInto(chunks, index, index + 1, chunkCount)

        chunks[--chunkCount] = null
    }
}

// What a read without the lock answers when the entry is not there
private object Absent

// What it answers when it cannot tell, since a thread changed the table meanwhile
private object Unstable

/**
 * A part of a [SortedTable]: up to [CHUNK_CAPACITY] entries in the order of their keys, the
 * keys in one array and their values at the same places of another.
 */
private class Chunk {

    val keys = arrayOfNulls<Any>(CHUNK_CAPACITY)
    val values = arrayOfNulls<Any>(CHUNK_CAPACITY)
    var size = 0
}

/**
 * An entry of a sorted map as it was at one moment. It cannot change the map.
 */
internal class SortedEntry<K, V>(
    override val key: K,
    override val value: V,
) : MutableMap.MutableEntry<K, V> {

    override fun setValue(newValue: V): V = throw UnsupportedOperationException()

    override fun equals(other: Any?): Boolean =
        other is Map.Entry<*, *> && other.key == key && other.value == value

    override fun hashCode(): Int = key.hashCode() xor value.hashCode()

    override fun toString(): String = "$key=$value"
}
