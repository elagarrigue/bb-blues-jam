package com.bbbjam.core.ui.strip

import com.bbbjam.core.model.Instrument

/**
 * The instrument strip copy, approved by the user on 2 October 2026
 * (`docs/specs/instrument-strip-component.md`, C1 and E1). Rioplatense Spanish (D-12). It lives in
 * `:core:ui` because Próxima jam and the song detail draw the same strip and may not share code
 * between features (D-03).
 */
internal object InstrumentStripCopy {
    private const val OPEN_WORD = "LIBRE"
    private const val OPEN_DESCRIPTION_WORD = "libre"
    private const val EXTRAS_DESCRIPTION_PREFIX = "Otros"

    /** "GTR: LIBRE": the abbreviation and the word in uppercase. */
    fun openLabel(instrument: Instrument): String = "${abbreviation(instrument).uppercase()}: $OPEN_WORD"

    /** "Gtr: Tincho": the musician's name exactly as in the Sheet. */
    fun filledLabel(instrument: Instrument, musicianName: String): String = "${abbreviation(instrument)}: $musicianName"

    /** "Guitarra: libre". */
    fun openDescription(instrument: Instrument): String = "${name(instrument)}: $OPEN_DESCRIPTION_WORD"

    /** "Guitarra: Tincho". */
    fun filledDescription(instrument: Instrument, musicianName: String): String = "${name(instrument)}: $musicianName"

    /** "+ saxo: Juan": the instrument with the case the admin typed. The "+" is the chip's glyph. */
    fun extraLabel(instrument: String, name: String): String = "+ $instrument: $name"

    /** "Otros: saxo, Juan". */
    fun extraDescription(instrument: String, name: String): String = "$EXTRAS_DESCRIPTION_PREFIX: $instrument, $name"

    private fun abbreviation(instrument: Instrument): String = when (instrument) {
        Instrument.GUITAR -> "Gtr"
        Instrument.BASS -> "Bajo"
        Instrument.DRUMS -> "Bat"
        Instrument.VOCALS -> "Voz"
        Instrument.HARMONICA -> "Arm"
        Instrument.KEYBOARDS -> "Tec"
    }

    /** "Guitarra": the full instrument name, also used by the expanded lineup panel. */
    fun name(instrument: Instrument): String = when (instrument) {
        Instrument.GUITAR -> "Guitarra"
        Instrument.BASS -> "Bajo"
        Instrument.DRUMS -> "Batería"
        Instrument.VOCALS -> "Voz"
        Instrument.HARMONICA -> "Armónica"
        Instrument.KEYBOARDS -> "Teclados"
    }
}

/** The shared Spanish instrument name. */
fun Instrument.fullName(): String = InstrumentStripCopy.name(this)
