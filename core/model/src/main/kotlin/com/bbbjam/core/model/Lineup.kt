package com.bbbjam.core.model

/**
 * The [Slot]s of one [JamSong]: the default seven with some possibly removed, never more of an
 * instrument than the default (D-18). Zero of an instrument is valid. Slot order is kept: the k-th
 * slot of an instrument is the k-th Sheet column of that instrument not holding `-`.
 */
data class Lineup(val slots: List<Slot>) {
    init {
        slots.groupingBy { it.instrument }.eachCount().forEach { (instrument, count) ->
            val allowed = DEFAULT_COUNTS[instrument] ?: 0
            require(count <= allowed) {
                "Lineup has $count $instrument slots; the default allows at most $allowed (D-18)"
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
        require(count in 0..defaultCount(instrument)) {
            "Lineup slot count $count for $instrument must be in 0..${defaultCount(instrument)}"
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

        /** The default lineup: seven open slots in [DEFAULT_INSTRUMENTS] order. */
        fun default(): Lineup = Lineup(DEFAULT_INSTRUMENTS.map { Slot(it) })
    }
}
