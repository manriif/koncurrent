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
/*
 * Ported from the ConcurrentHashMap of JSR-166, which was written by Doug Lea with assistance
 * from members of JCP JSR-166 Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

// Kotlin/Native gives each function its own frame of GC roots, even where LLVM inlines it. The
// functions of the hot paths are inlined by Kotlin, which merges their frames.
@file:Suppress("NOTHING_TO_INLINE")

package koncurrent.internal

import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicInt
import kotlin.math.max

// The largest table size. The top bits of the size control hold the resize stamp.
private const val MAXIMUM_CAPACITY = 1 shl 30

// The default table size. Must be a power of two.
private const val DEFAULT_CAPACITY = 16

// Only sizes the initial table. A table is always resized when three quarters full.
private const val LOAD_FACTOR = 0.75f

// The bin count from which a list becomes a tree
private const val TREEIFY_THRESHOLD = 8

// The smallest table size for which a bin may become a tree. A smaller table is resized.
private const val MIN_TREEIFY_CAPACITY = 64

// The minimum number of bins that a resizer moves per step
private const val MIN_TRANSFER_STRIDE = 16

// A resize is split in at most that power of two of steps. The original derives the step from
// the number of processors, which common Kotlin does not give.
private const val TRANSFER_STEPS_SHIFT = 7

// The number of bits of the generation stamp in the size control
private const val RESIZE_STAMP_BITS = 16

// The shift of the generation stamp in the size control
private const val RESIZE_STAMP_SHIFT = 32 - RESIZE_STAMP_BITS

// The maximum number of threads that can help to resize
private const val MAX_RESIZERS = (1 shl (32 - RESIZE_STAMP_BITS)) - 1

/**
 * The `ConcurrentHashMap` of JSR-166 in common Kotlin. It backs the public map where the
 * platform has none.
 *
 * The size control is negative while the table is created or resized. It is then -1, or the
 * resize stamp plus one plus the number of resizers. Otherwise it is the size of the table to
 * create, zero for the default one, or the count upon which to resize the table once created.
 */
internal class PortableConcurrentHashMap<K : Any, V : Any> private constructor(
    private val sizeCtl: AtomicInt,
) : AbstractMutableMap<K, V>(), ConcurrentMap<K, V> {

    // The bins. Created upon the first insertion.
    @Volatile
    internal var table: Table<K, V>? = null
        private set

    // The next table to use. Not null only while resizing.
    @Volatile
    private var nextTable: Table<K, V>? = null

    // The next table index, plus one, to split while resizing
    private val transferIndex = AtomicInt(0)

    private val counter = StripedCounter()

    @Volatile
    private var keysView: KeySetView<K, V>? = null

    @Volatile
    private var valuesView: ValuesView<K, V>? = null

    @Volatile
    private var entriesView: EntrySetView<K, V>? = null

    constructor() : this(AtomicInt(0))

    // Throws an IllegalArgumentException if the capacity is negative
    constructor(initialCapacity: Int) : this(AtomicInt(initialTableSize(initialCapacity)))

    override val size: Int
        get() = counter.sum().coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    override val keys: MutableSet<K>
        get() = keysView ?: KeySetView(this).also { keysView = it }

    override val values: MutableCollection<V>
        get() = valuesView ?: ValuesView(this).also { valuesView = it }

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = entriesView ?: EntrySetView(this).also { entriesView = it }

    ///////////////////////////////////////////////////////////////////////////
    // Map
    ///////////////////////////////////////////////////////////////////////////

    // The count may be transiently negative
    override fun isEmpty(): Boolean = counter.sum() <= 0L

    override fun get(key: K): V? = findValue(key)

    override fun containsKey(key: K): Boolean = findValue(key) != null

    // The lookup. A class that wraps this map inlines it in its own lookups.
    inline fun findValue(key: K): V? = table?.find(spread(key.hashCode()), key)?.value

    override fun containsValue(value: V): Boolean {
        val traverser = Traverser(table)

        while (true) {
            val node = traverser.advance() ?: return false

            if (matches(value, node.value)) {
                return true
            }
        }
    }

    @IgnorableReturnValue
    override fun put(key: K, value: V): V? = putVal(key, value, onlyIfAbsent = false)

    override fun putAll(from: Map<out K, V>) {
        tryPresize(from.size)

        for ((key, value) in from) {
            val _ = putVal(key, value, onlyIfAbsent = false)
        }
    }

    @IgnorableReturnValue
    override fun remove(key: K): V? = replaceNode(key, null, null)

    override fun clear() {
        var table = table ?: return
        var delta = 0L // Negative number of deletions
        var index = 0

        while (index < table.size) {
            when (val head = table.binAt(index)) {
                null -> index++

                is ForwardingNode -> {
                    table = helpTransfer(table, head)
                    index = 0 // Restarts
                }

                else -> table.lockBin(index, head) {
                    val first = when (head) {
                        is ListNode -> head
                        is TreeBin -> head.first
                        is ForwardingNode, is ReservationNode -> null
                    }

                    delta -= first?.count() ?: 0
                    table.setBinAt(index++, null)
                }
            }
        }

        if (delta != 0L) {
            addCount(delta, -1)
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Atomic updates
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Associates [key] with [value] unless it already has a value. Returns the value that [key]
     * already had, or `null` when it had none.
     */
    @IgnorableReturnValue
    override fun putIfAbsent(key: K, value: V): V? = findValue(key) ?: putAbsent(key, value)

    // The lookup comes first and without the lock, as for a computation
    private fun putAbsent(key: K, value: V): V? = putVal(key, value, onlyIfAbsent = true)

    /**
     * Removes [key] only if it is currently mapped to [value]. Returns `true` when removed.
     */
    @IgnorableReturnValue
    override fun remove(key: K, value: V): Boolean = replaceNode(key, null, value) != null

    /**
     * Replaces the value of [key] with [newValue] only if it is currently [oldValue]. Returns
     * `true` when replaced.
     */
    @IgnorableReturnValue
    override fun replace(key: K, oldValue: V, newValue: V): Boolean =
        replaceNode(key, newValue, oldValue) != null

    /**
     * Replaces the value of [key] with [value] only if it has one. Returns the previous value,
     * or `null` when it had none.
     */
    @IgnorableReturnValue
    override fun replace(key: K, value: V): V? = replaceNode(key, value, null)

    /**
     * Associates [key] with the value of [mappingFunction] unless it already has a value. The
     * function is called at most once, and only when [key] has no value. Returns the value of
     * [key] after the call.
     *
     * Other updates of the map may wait while the function runs, so it should be short. It must
     * not update this map. An update of the same bin would never complete.
     */
    @IgnorableReturnValue
    override fun computeIfAbsent(key: K, mappingFunction: (K) -> V): V =
        findValue(key) ?: computeAbsent(key, mappingFunction)

    /**
     * Does [computeIfAbsent] for a key that had no value a moment ago.
     *
     * A key that has a value is the common case, so the lookup comes first and without the
     * lock. The original only looks at the first node of the bin that way. Looking at them all
     * keeps the threads that ask for the same key from waiting for each other.
     */
    fun computeAbsent(key: K, mappingFunction: (K) -> V): V {
        val hash = spread(key.hashCode())
        var table = table ?: initTable()

        while (true) {
            val index = (table.size - 1) and hash
            val head = table.binAt(index)
            var value: V? = null
            var added = false
            var binCount = 0

            if (head == null) {
                added = table.reserve(index) {
                    val computed = mappingFunction(key)

                    value = computed
                    ListNode(hash, key, computed, null)
                }
            } else if (head is ForwardingNode) {
                table = helpTransfer(table, head)
            } else if (head is ListNode && head.matches(hash, key)) {
                // Checks the first node without the lock
                return head.value
            } else {
                table.lockBin(index, head) {
                    when (head) {
                        is ListNode -> binCount = head.scan(
                            hash = hash,
                            key = key,
                            found = { node, _ ->
                                value = node.value
                            },
                            missing = { last ->
                                val computed = mappingFunction(key)

                                added = true
                                value = computed
                                last.next = ListNode(hash, key, computed, null)
                            },
                        )

                        is TreeBin -> {
                            binCount = 2
                            val node = head.findLocked(hash, key)

                            if (node != null) {
                                value = node.value
                            } else {
                                val computed = mappingFunction(key)

                                added = true
                                value = computed
                                head.putTreeVal(hash, key, computed)
                            }
                        }

                        is ForwardingNode, is ReservationNode -> Unit
                    }
                }

                if (binCount >= TREEIFY_THRESHOLD) {
                    treeifyBin(table, index)
                }
            }

            val result = value

            if (result != null) {
                if (added) {
                    addCount(1L, binCount)
                }

                return result
            }
        }
    }

    /**
     * Replaces the value of [key] with the value of [remappingFunction] if it has one. The
     * function is called at most once, and only when [key] has a value. A `null` result removes
     * [key]. Returns the new value of [key], or `null` when it has none.
     *
     * Other updates of the map may wait while the function runs, so it should be short. It must
     * not update this map. An update of the same bin would never complete.
     */
    @IgnorableReturnValue
    override fun computeIfPresent(key: K, remappingFunction: (K, V) -> V?): V? {
        val hash = spread(key.hashCode())
        var table = table ?: return null
        var value: V? = null
        var delta = 0
        var binCount = 0

        while (binCount == 0) {
            val index = (table.size - 1) and hash
            val head = table.binAt(index) ?: break

            if (head is ForwardingNode) {
                table = helpTransfer(table, head)
                continue
            }

            table.lockBin(index, head) {
                when (head) {
                    is ListNode -> binCount = head.scan(
                        hash = hash,
                        key = key,
                        found = { node, pred ->
                            value = remappingFunction(key, node.value)
                            delta = table.update(index, node, pred, value)
                        },
                        missing = {},
                    )

                    is TreeBin -> {
                        binCount = 2
                        val node = head.findLocked(hash, key)

                        if (node != null) {
                            value = remappingFunction(key, node.value)
                            delta = table.update(index, head, node, value)
                        }
                    }

                    is ForwardingNode, is ReservationNode -> Unit
                }
            }
        }

        if (delta != 0) {
            addCount(delta.toLong(), binCount)
        }

        return value
    }

    /**
     * Associates [key] with the value of [remappingFunction], which is given the current value
     * of [key] or `null` when it has none. The function is called exactly once. A `null` result
     * removes [key]. Returns the new value of [key], or `null` when it has none.
     *
     * Other updates of the map may wait while the function runs, so it should be short. It must
     * not update this map. An update of the same bin would never complete.
     */
    @IgnorableReturnValue
    override fun compute(key: K, remappingFunction: (K, V?) -> V?): V? {
        val hash = spread(key.hashCode())
        var table = table ?: initTable()
        var value: V? = null
        var delta = 0
        var binCount = 0

        while (binCount == 0) {
            val index = (table.size - 1) and hash
            val head = table.binAt(index)

            if (head == null) {
                val reserved = table.reserve(index) {
                    value = remappingFunction(key, null)
                    value?.let { ListNode(hash, key, it, null) }
                }

                if (reserved) {
                    delta = if (value != null) 1 else 0
                    binCount = 1
                }
            } else if (head is ForwardingNode) {
                table = helpTransfer(table, head)
            } else {
                table.lockBin(index, head) {
                    when (head) {
                        is ListNode -> binCount = head.scan(
                            hash = hash,
                            key = key,
                            found = { node, pred ->
                                value = remappingFunction(key, node.value)
                                delta = table.update(index, node, pred, value)
                            },
                            missing = { last ->
                                val computed = remappingFunction(key, null)

                                if (computed != null) {
                                    delta = 1
                                    last.next = ListNode(hash, key, computed, null)
                                }

                                value = computed
                            },
                        )

                        is TreeBin -> {
                            binCount = 1
                            val node = head.findLocked(hash, key)
                            val computed = remappingFunction(key, node?.value)

                            if (node != null) {
                                delta = table.update(index, head, node, computed)
                            } else if (computed != null) {
                                delta = 1
                                head.putTreeVal(hash, key, computed)
                            }

                            value = computed
                        }

                        is ForwardingNode, is ReservationNode -> Unit
                    }
                }

                if (binCount >= TREEIFY_THRESHOLD) {
                    treeifyBin(table, index)
                }
            }
        }

        if (delta != 0) {
            addCount(delta.toLong(), binCount)
        }

        return value
    }

    /**
     * Associates [key] with [value] if it has no value. Otherwise replaces its value with the
     * value of [remappingFunction], which is given the current value and [value]. A `null`
     * result removes [key]. Returns the new value of [key], or `null` when it has none.
     *
     * Other updates of the map may wait while the function runs, so it should be short. It must
     * not update this map. An update of the same bin would never complete.
     */
    @IgnorableReturnValue
    override fun merge(key: K, value: V, remappingFunction: (V, V) -> V?): V? {
        val hash = spread(key.hashCode())
        var table = table ?: initTable()
        var merged: V? = null
        var delta = 0
        var binCount = 0

        while (delta == 0 && binCount == 0) {
            val index = (table.size - 1) and hash
            val head = table.binAt(index)

            if (head == null) {
                // An empty bin takes its first node without the lock
                if (table.casBinAt(index, null, ListNode(hash, key, value, null))) {
                    delta = 1
                    merged = value
                }
            } else if (head is ForwardingNode) {
                table = helpTransfer(table, head)
            } else {
                table.lockBin(index, head) {
                    when (head) {
                        is ListNode -> binCount = head.scan(
                            hash = hash,
                            key = key,
                            found = { node, pred ->
                                merged = remappingFunction(node.value, value)
                                delta = table.update(index, node, pred, merged)
                            },
                            missing = { last ->
                                delta = 1
                                merged = value
                                last.next = ListNode(hash, key, value, null)
                            },
                        )

                        is TreeBin -> {
                            binCount = 2
                            val node = head.findLocked(hash, key)

                            if (node != null) {
                                merged = remappingFunction(node.value, value)
                                delta = table.update(index, head, node, merged)
                            } else {
                                delta = 1
                                merged = value
                                head.putTreeVal(hash, key, value)
                            }
                        }

                        is ForwardingNode, is ReservationNode -> Unit
                    }
                }

                if (binCount >= TREEIFY_THRESHOLD) {
                    treeifyBin(table, index)
                }
            }
        }

        if (delta != 0) {
            addCount(delta.toLong(), binCount)
        }

        return merged
    }

    ///////////////////////////////////////////////////////////////////////////
    // Updates
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Associates [key] with [value], unless it has a value and [onlyIfAbsent] is set. Returns
     * the value that [key] had, or `null` when it had none.
     */
    private inline fun putVal(key: K, value: V, onlyIfAbsent: Boolean): V? {
        val hash = spread(key.hashCode())
        var table = table ?: initTable()
        var oldValue: V? = null
        var binCount = 0

        while (binCount == 0) {
            val index = (table.size - 1) and hash
            val head = table.binAt(index)

            if (head == null) {
                // An empty bin takes its first node without the lock
                if (table.casBinAt(index, null, ListNode(hash, key, value, null))) {
                    break
                }
            } else if (head is ForwardingNode) {
                table = helpTransfer(table, head)
            } else if (onlyIfAbsent && head is ListNode && head.matches(hash, key)) {
                // Checks the first node without the lock
                return head.value
            } else {
                table.lockBin(index, head) {
                    when (head) {
                        is ListNode -> binCount = head.scan(
                            hash = hash,
                            key = key,
                            found = { node, _ ->
                                oldValue = node.value

                                if (!onlyIfAbsent) {
                                    node.value = value
                                }
                            },
                            missing = { it.next = ListNode(hash, key, value, null) },
                        )

                        is TreeBin -> {
                            binCount = 2
                            val node = head.putTreeVal(hash, key, value)

                            if (node != null) {
                                oldValue = node.value

                                if (!onlyIfAbsent) {
                                    node.value = value
                                }
                            }
                        }

                        is ForwardingNode, is ReservationNode -> Unit
                    }
                }

                if (binCount >= TREEIFY_THRESHOLD) {
                    treeifyBin(table, index)
                }
            }
        }

        if (oldValue == null) {
            addCount(1L, binCount)
        }

        return oldValue
    }

    /**
     * Replaces the value of [key] with [value], or removes [key] when [value] is `null`. Only
     * applies when the current value equals [expected], unless [expected] is `null`. Returns the
     * previous value, or `null` when nothing changed.
     */
    private inline fun replaceNode(key: K, value: V?, expected: V?): V? {
        val hash = spread(key.hashCode())
        var table = table ?: return null

        while (true) {
            val index = (table.size - 1) and hash
            val head = table.binAt(index) ?: return null

            if (head is ForwardingNode) {
                table = helpTransfer(table, head)
                continue
            }

            var oldValue: V? = null
            var delta = 0
            var validated = false

            table.lockBin(index, head) {
                when (head) {
                    is ListNode -> {
                        validated = true

                        val _ = head.scan(
                            hash = hash,
                            key = key,
                            found = { node, pred ->
                                if (expected == null || matches(expected, node.value)) {
                                    oldValue = node.value
                                    delta = table.update(index, node, pred, value)
                                }
                            },
                            missing = {},
                        )
                    }

                    is TreeBin -> {
                        validated = true
                        val node = head.findLocked(hash, key)

                        if (node != null && (expected == null || matches(expected, node.value))) {
                            oldValue = node.value
                            delta = table.update(index, head, node, value)
                        }
                    }

                    is ForwardingNode, is ReservationNode -> Unit
                }
            }

            if (validated) {
                if (delta != 0) {
                    addCount(delta.toLong(), -1)
                }

                return oldValue
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Table creation and resizing
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Returns the table, created with the size recorded in the size control if needed.
     */
    private fun initTable(): Table<K, V> {
        while (true) {
            val table = table

            if (table != null) {
                return table
            }

            val control = sizeCtl.load()

            // Spins while another thread creates the table, which a negative control tells
            if (control >= 0 && sizeCtl.compareAndSet(control, -1)) {
                return createTable(if (control > 0) control else DEFAULT_CAPACITY, control)
            }
        }
    }

    /**
     * Creates the table with [size] bins, unless another thread just did. The caller changed the
     * size control from [control] to -1.
     */
    private fun createTable(size: Int, control: Int): Table<K, V> {
        var threshold = control

        try {
            val table = table

            if (table != null) {
                return table
            }

            val created = Table<K, V>(size)

            this.table = created
            threshold = size - (size ushr 2)

            return created
        } finally {
            sizeCtl.store(threshold)
        }
    }

    /**
     * Adds [delta] to the count. Then resizes the table if it is too small, or helps the running
     * resize. [check] is the number of nodes seen in the bin. A negative one skips the resize
     * check. So does one of at most one when the count is contended.
     */
    private inline fun addCount(delta: Long, check: Int) {
        var count = counter.add(delta)

        if (count == CONTENDED_COUNT) {
            if (check <= 1) {
                return
            }

            count = counter.sum()
        }

        if (check >= 0 && count >= sizeCtl.load()) {
            resizeWhileFull(count)
        }
    }

    /**
     * Resizes the table until it fits the count, which is [initialCount] at first. Joins the
     * running resize if there is one.
     */
    private fun resizeWhileFull(initialCount: Long) {
        var count = initialCount

        while (true) {
            val control = sizeCtl.load()

            if (count < control) {
                return
            }

            val table = table

            if (table == null || table.size >= MAXIMUM_CAPACITY) {
                return
            }

            val stamp = resizeStamp(table.size) shl RESIZE_STAMP_SHIFT

            if (control >= 0) {
                if (sizeCtl.compareAndSet(control, stamp + 2)) {
                    startTransfer(table)
                }
            } else {
                val nextTable = nextTable

                if (nextTable == null || isTransferClosed(control, stamp)) {
                    return
                }

                if (sizeCtl.compareAndSet(control, control + 1)) {
                    transfer(table, nextTable)
                }
            }

            count = counter.sum()
        }
    }

    /**
     * Helps the resize that left [forwarding] in [table] if it is still running. Returns the
     * next table.
     */
    private fun helpTransfer(
        table: Table<K, V>,
        forwarding: ForwardingNode<K, V>,
    ): Table<K, V> {
        val nextTable = forwarding.nextTable
        val stamp = resizeStamp(table.size) shl RESIZE_STAMP_SHIFT

        while (nextTable === this.nextTable && table === this.table) {
            val control = sizeCtl.load()

            if (control >= 0 || isTransferClosed(control, stamp)) {
                break
            }

            if (sizeCtl.compareAndSet(control, control + 1)) {
                transfer(table, nextTable)
                break
            }
        }

        return nextTable
    }

    /**
     * Tells whether the resize stamped [stamp] is over or takes no more helper. [control] is the
     * size control read during that resize.
     */
    private fun isTransferClosed(control: Int, stamp: Int): Boolean =
        control == stamp + MAX_RESIZERS || control == stamp + 1 || transferIndex.load() <= 0

    /**
     * Tries to presize the table to accommodate [size] mappings.
     */
    private fun tryPresize(size: Int) {
        val capacity = if (size >= MAXIMUM_CAPACITY ushr 1) {
            MAXIMUM_CAPACITY
        } else {
            tableSizeFor(size + (size ushr 1) + 1)
        }

        while (true) {
            val control = sizeCtl.load()

            if (control < 0) {
                return
            }

            val table = table

            if (table == null) {
                if (sizeCtl.compareAndSet(control, -1)) {
                    val _ = createTable(max(control, capacity), control)
                }
            } else if (capacity <= control || table.size >= MAXIMUM_CAPACITY) {
                return
            } else if (table === this.table) {
                val stamp = resizeStamp(table.size) shl RESIZE_STAMP_SHIFT

                if (sizeCtl.compareAndSet(control, stamp + 2)) {
                    startTransfer(table)
                }
            }
        }
    }

    /**
     * Starts the resize of [table] to a table twice as large. The caller stamped the size
     * control.
     */
    private fun startTransfer(table: Table<K, V>) {
        val nextTable = try {
            Table<K, V>(table.size shl 1)
        } catch (_: Throwable) {
            // Copes with a lack of memory by never resizing again
            sizeCtl.store(Int.MAX_VALUE)
            return
        }

        this.nextTable = nextTable
        transferIndex.store(table.size)
        transfer(table, nextTable)
    }

    /**
     * Moves the bins of [table] to [nextTable] along with the other resizers. Each one claims
     * ranges of bins from the transfer index, from the last bin down to the first.
     */
    private fun transfer(table: Table<K, V>, nextTable: Table<K, V>) {
        val size = table.size
        val stride = max(size ushr TRANSFER_STEPS_SHIFT, MIN_TRANSFER_STRIDE)
        val forwarding = ForwardingNode(nextTable)
        var finishing = false // To sweep the bins again before committing the next table
        var index = 0
        var bound = 0

        while (true) {
            index--

            if (index < bound && !finishing) {
                val end = claimBins(stride)

                bound = max(end - stride, 0)
                index = end - 1
            }

            @Suppress("ConvertTwoComparisonsToRangeCheck")
            if (index < 0 || index >= size || index + size >= nextTable.size) {
                if (finishing) {
                    this.nextTable = null
                    this.table = nextTable
                    sizeCtl.store((size shl 1) - (size ushr 1))

                    return
                }

                // Leaves the resize. The last resizer to leave commits it.
                val control = sizeCtl.fetchAndAdd(-1)

                if (control - 2 != resizeStamp(size) shl RESIZE_STAMP_SHIFT) {
                    return
                }

                finishing = true
                index = size
                bound = 0
            } else {
                transferBin(table, nextTable, index, forwarding)
            }
        }
    }

    /**
     * Claims the next range of at most [stride] bins to move. Returns the index after its last
     * bin, or zero when no bin is left to claim.
     */
    private inline fun claimBins(stride: Int): Int {
        while (true) {
            val end = transferIndex.load()

            if (end <= 0) {
                return 0
            }

            if (transferIndex.compareAndSet(end, max(end - stride, 0))) {
                return end
            }
        }
    }

    /**
     * Moves the nodes of the bin of [table] at [index] to [nextTable], unless another resizer
     * did. Leaves [forwarding] in the bin.
     */
    private inline fun transferBin(
        table: Table<K, V>,
        nextTable: Table<K, V>,
        index: Int,
        forwarding: ForwardingNode<K, V>,
    ) {
        while (true) {
            val head = table.binAt(index)

            if (head == null) {
                if (table.casBinAt(index, null, forwarding)) {
                    return
                }
            } else if (head is ForwardingNode) {
                return
            } else {
                table.lockBin(index, head) {
                    when (head) {
                        is ListNode -> transferList(head, table.size, nextTable, index)
                        is TreeBin -> transferTree(head, table.size, nextTable, index)
                        is ForwardingNode, is ReservationNode -> return@lockBin
                    }

                    table.setBinAt(index, forwarding)
                    return
                }
            }
        }
    }

    /**
     * Splits the list of [head] between the bins of [nextTable] at [index] and at [index] plus
     * [size], the size of the table it comes from.
     */
    private inline fun transferList(
        head: ListNode<K, V>,
        size: Int,
        nextTable: Table<K, V>,
        index: Int,
    ) {
        var runBit = head.hash and size
        var lastRun = head
        var node = head.next

        // The nodes of the last run all go to the same bin. They are reused as they are.
        while (node != null) {
            val bit = node.hash and size

            if (bit != runBit) {
                runBit = bit
                lastRun = node
            }

            node = node.next
        }

        var low = if (runBit == 0) lastRun else null
        var high = if (runBit == 0) null else lastRun

        node = head

        while (node != null && node !== lastRun) {
            if ((node.hash and size) == 0) {
                low = ListNode(node.hash, node.key, node.value, low)
            } else {
                high = ListNode(node.hash, node.key, node.value, high)
            }

            node = node.next
        }

        // The bins of the next table are still empty, so only a list needs storing
        if (low != null) {
            nextTable.setBinAt(index, low)
        }

        if (high != null) {
            nextTable.setBinAt(index + size, high)
        }
    }

    /**
     * Splits the tree of [bin] between the bins of [nextTable] at [index] and at [index] plus
     * [size], the size of the table it comes from.
     */
    private fun transferTree(bin: TreeBin<K, V>, size: Int, nextTable: Table<K, V>, index: Int) {
        val low = TreeNodeList<K, V>()
        val high = TreeNodeList<K, V>()
        var node = bin.first

        while (node != null) {
            if ((node.hash and size) == 0) {
                low.add(node)
            } else {
                high.add(node)
            }

            node = node.next
        }

        nextTable.setBinAt(index, low.toBin(bin, high))
        nextTable.setBinAt(index + size, high.toBin(bin, low))
    }

    /**
     * Turns the list of the bin of [table] at [index] into a tree. Resizes the table instead
     * when it is small.
     */
    private fun treeifyBin(table: Table<K, V>, index: Int) {
        if (table.size < MIN_TREEIFY_CAPACITY) {
            tryPresize(table.size shl 1)
            return
        }

        val head = table.binAt(index)

        if (head !is ListNode) {
            return
        }

        table.lockBin(index, head) {
            val nodes = TreeNodeList<K, V>()
            var node: ListNode<K, V>? = head

            while (node != null) {
                nodes.add(node)
                node = node.next
            }

            table.setBinAt(index, nodes.toTreeBin())
        }
    }
}

///////////////////////////////////////////////////////////////////////////
// Hashing and sizing
///////////////////////////////////////////////////////////////////////////

/**
 * Spreads the higher bits of [hash] to the lower ones, since the table masks the higher ones.
 */
internal inline fun spread(hash: Int): Int = hash xor (hash ushr 16)

@Suppress("SuspiciousEqualsCombination")
private fun matches(expected: Any, value: Any): Boolean = expected === value || expected == value

/**
 * Returns the power of two table size for [capacity].
 */
private fun tableSizeFor(capacity: Int): Int {
    val size = -1 ushr (capacity - 1).countLeadingZeroBits()

    return when {
        size < 0 -> 1
        size >= MAXIMUM_CAPACITY -> MAXIMUM_CAPACITY
        else -> size + 1
    }
}

/**
 * Returns the size of the table to create for [initialCapacity] mappings.
 */
private fun initialTableSize(initialCapacity: Int): Int {
    require(initialCapacity >= 0) { "initialCapacity must not be negative, was $initialCapacity" }

    val size = (1.0 + max(initialCapacity, 1) / LOAD_FACTOR).toLong()

    return if (size >= MAXIMUM_CAPACITY) MAXIMUM_CAPACITY else tableSizeFor(size.toInt())
}

/**
 * Returns the stamp of the resize of a table of [size] bins. It is negative once shifted left
 * by the stamp shift.
 */
private fun resizeStamp(size: Int): Int =
    size.countLeadingZeroBits() or (1 shl (RESIZE_STAMP_BITS - 1))
