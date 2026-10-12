package com.bbbjam.core.model

/**
 * The [Slot]s of one [JamSong]: the default seven, with some removed or some added, never more
 * than [maxCount] of an instrument added by the app (D-18, amended 12 October 2026). Zero of an instrument
 * is valid. [maxCount] limits growth only: a Sheet tab with a filled column beyond an instrument's ceiling
 * (for example `Batería 2`) is still read, so a lineup may hold up to one slot per Sheet column and no
 * assigned musician is ever hidden. The app never writes columns beyond a ceiling.
 * Slot order is kept: the k-th slot of an instrument is the k-th Sheet column of that instrument not holding `-`.
 */
data class Lineup(val slots: List<Slot>) {
    init {
        slots.groupingBy { it.instrument }.eachCount().forEach { (instrument, count) ->
            val allowed = MAX_INSTRUMENTS.count { it == instrument }
            require(count <= allowed) {
                "Lineup has $count $instrument slots; at most $allowed are allowed (D-18)"
            }
        }
    }

    /** The slots nobody is assigned to, in lineup order. */
    val openSlots: List<Slot>
        get() = slots.filter { it.isOpen }

    /** True when this lineup has at least one open slot for [instrument]. */
    fun hasOpenSlotFor(instrument: Instrument): Boolean = slots.any { it.instrument == instrument && it.isOpen }

    /** The number of slots for [instrument] in this lineup. */
    fun count(instrument: Instrument): Int = slots.count { it.instrument == instrument }

    /** The instrument ordinal for a slot at [lineupIndex], before any presentation reordering. */
    fun positionOf(lineupIndex: Int): SlotPosition? {
        if (lineupIndex !in slots.indices) return null
        val instrument = slots[lineupIndex].instrument
        val ordinal = slots.take(lineupIndex + 1).count { it.instrument == instrument }
        return SlotPosition(ordinal)
    }

    /** The slot at [position] in [instrument]'s lineup order, independent of other instruments. */
    fun slotAt(instrument: Instrument, position: SlotPosition): Slot? =
        slots.asSequence().filter { it.instrument == instrument }.drop(position.value - 1).firstOrNull()

    /**
     * Returns this lineup with [count] slots for [instrument], or null when reducing it would
     * remove a filled slot. Open slots are removed from the end of their instrument's order.
     */
    fun withSlotCount(instrument: Instrument, count: Int): Lineup? {
        require(count in 0..maxCount(instrument)) {
            "Lineup slot count $count for $instrument must be in 0..${maxCount(instrument)}"
        }

        val currentCount = count(instrument)
        return if (count == currentCount) {
            this
        } else if (count < currentCount) {
            val toRemove = currentCount - count
            val removable = slots.indices.filter { slots[it].instrument == instrument && slots[it].isOpen }
            if (removable.size < toRemove) {
                null
            } else {
                val removedIndices = removable.takeLast(toRemove).toSet()
                Lineup(slots.filterIndexed { index, _ -> index !in removedIndices })
            }
        } else {
            val additions = List(count - currentCount) { Slot(instrument) }
            val lastMatchingIndex = slots.indexOfLast { it.instrument == instrument }
            val insertAt = if (lastMatchingIndex >= 0) {
                lastMatchingIndex + 1
            } else {
                val instrumentOrder = DEFAULT_INSTRUMENTS.indexOf(instrument)
                slots.indexOfFirst { DEFAULT_INSTRUMENTS.indexOf(it.instrument) > instrumentOrder }
                    .takeIf { it >= 0 } ?: slots.size
            }
            Lineup(slots.toMutableList().apply { addAll(insertAt, additions) })
        }
    }

    companion object {
        /** The default lineup's instruments, in the Sheet's column order. */
        val DEFAULT_INSTRUMENTS: List<Instrument> = listOf(
            Instrument.GUITAR,
            Instrument.GUITAR,
            Instrument.BASS,
            Instrument.DRUMS,
            Instrument.VOCALS,
            Instrument.HARMONICA,
            Instrument.KEYBOARDS,
        )

        private val DEFAULT_COUNTS: Map<Instrument, Int> = DEFAULT_INSTRUMENTS.groupingBy { it }.eachCount()

        /** The number of slots assigned to [instrument] in the default lineup. */
        fun defaultCount(instrument: Instrument): Int = DEFAULT_COUNTS[instrument] ?: 0

        private const val MAX_SLOTS_PER_INSTRUMENT = 3

        /** Per-instrument growth ceilings: Batería and Teclados stay at one (user decision, 12 October 2026). */
        private val CEILINGS: Map<Instrument, Int> = mapOf(
            Instrument.GUITAR to 3,
            Instrument.BASS to 3,
            Instrument.VOCALS to 3,
            Instrument.HARMONICA to 3,
            Instrument.DRUMS to 1,
            Instrument.KEYBOARDS to 1,
        )

        /**
         * Every Sheet slot column, three per instrument, in the Sheet's column order (the order of
         * the `slots` keys). The k-th entry of an instrument is its k-th slot column.
         */
        val MAX_INSTRUMENTS: List<Instrument> = listOf(
            Instrument.GUITAR,
            Instrument.BASS,
            Instrument.DRUMS,
            Instrument.VOCALS,
            Instrument.HARMONICA,
            Instrument.KEYBOARDS,
        ).flatMap { instrument -> List(MAX_SLOTS_PER_INSTRUMENT) { instrument } }

        /**
         * The [MAX_INSTRUMENTS] column of each [DEFAULT_INSTRUMENTS] entry: the k-th default slot of an
         * instrument is that instrument's k-th column (0, 1, 3, 6, 9, 12, 15).
         */
        val DEFAULT_COLUMNS: List<Int> = DEFAULT_INSTRUMENTS.mapIndexed { index, instrument ->
            val ordinal = DEFAULT_INSTRUMENTS.take(index).count { it == instrument }
            MAX_INSTRUMENTS.indices.filter { MAX_INSTRUMENTS[it] == instrument }[ordinal]
        }

        /**
         * The most slots the app lets [instrument] grow to in one song's lineup, independent of its
         * default. A ceiling, not a column count: [MAX_INSTRUMENTS] stays the wire shape.
         */
        fun maxCount(instrument: Instrument): Int = CEILINGS.getValue(instrument)

        /** The default lineup: seven open slots in [DEFAULT_INSTRUMENTS] order. */
        fun default(): Lineup = Lineup(DEFAULT_INSTRUMENTS.map { Slot(it) })
    }
}
