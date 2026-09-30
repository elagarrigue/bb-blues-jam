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

        /** The default lineup: seven open slots in [DEFAULT_INSTRUMENTS] order. */
        fun default(): Lineup = Lineup(DEFAULT_INSTRUMENTS.map { Slot(it) })
    }
}
