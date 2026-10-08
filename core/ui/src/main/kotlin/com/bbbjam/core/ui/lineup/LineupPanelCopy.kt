package com.bbbjam.core.ui.lineup

/**
 * The expanded lineup panel copy, approved by the user on 3 October 2026
 * (`docs/specs/song-row-expansion.md`, C1 and H1). Rioplatense Spanish with vos (D-12). Instrument
 * names and line descriptions come from the strip's approved copy (`InstrumentStripCopy`).
 */
internal object LineupPanelCopy {
    /** Section headings, drawn in uppercase. */
    const val OPEN_HEADING = "Cupos libres"
    const val FILLED_HEADING = "Cupos cubiertos"
    const val EXTRAS_HEADING = "Otros"

    /** The detail of an open line: "Guitarra  LIBRE". */
    const val OPEN_DETAIL = "LIBRE"
    const val ASSIGN = "Anotar"

    const val NO_OPEN_SLOTS = "No quedan cupos libres."

    /** H1: after the open lines, only when at least one slot is open. */
    const val HINT = "Para tocar, anotate en la jam: la organización te suma a un tema."

    /** "+ saxo": the instrument exactly as the admin typed it, after the extra's "+" glyph. */
    fun extraInstrument(instrument: String): String = "+ $instrument"
}
