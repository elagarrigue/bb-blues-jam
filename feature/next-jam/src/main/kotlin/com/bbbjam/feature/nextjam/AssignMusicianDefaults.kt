package com.bbbjam.feature.nextjam

import com.bbbjam.core.model.Instrument

/** Presentation labels for instruments in the fixed-slot assignment flow. */
internal object AssignMusicianDefaults {
    fun instrumentName(instrument: Instrument): String = when (instrument) {
        Instrument.GUITAR -> "Guitarra"
        Instrument.BASS -> "Bajo"
        Instrument.DRUMS -> "Batería"
        Instrument.VOCALS -> "Voz"
        Instrument.HARMONICA -> "Armónica"
        Instrument.KEYBOARDS -> "Teclados"
    }

    fun slotName(instrument: Instrument, ordinal: Int): String =
        "${instrumentName(instrument)}${if (instrument == Instrument.GUITAR) " $ordinal" else ""}"
}
