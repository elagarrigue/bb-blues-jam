package com.bbbjam.core.ui.filter

import com.bbbjam.core.model.Instrument
import com.bbbjam.core.ui.strip.InstrumentStripCopy

/**
 * The filter bar copy, approved by the user on 4 October 2026 (`docs/specs/instrument-filter-chips.md`,
 * C1 and the multi-select of F1). Rioplatense Spanish (D-12). Instrument names come from the strip's
 * copy, lowercased inside sentences.
 */
internal object InstrumentFilterCopy {
    const val HEADING = "Filtrá por cupo libre"
    const val ALL = "Todos"
    const val CLEAR = "Ver todos los temas"

    /** "Todos los temas: 13". */
    fun allDescription(total: Int): String = "Todos los temas: $total"

    /** "Bajo: 2 temas con cupo libre", "Bajo: 1 tema con cupo libre", "Bajo: ningún tema con cupo libre". */
    fun instrumentDescription(instrument: Instrument, count: Int): String {
        val songs = when (count) {
            0 -> "ningún tema"
            1 -> "1 tema"
            else -> "$count temas"
        }
        return "${name(instrument)}: $songs con cupo libre"
    }

    /**
     * "2 de 13 temas con cupo libre para bajo o voz", "1 de 13 temas …": the noun agrees with the
     * total, so it is "tema" only for a one-song setlist ("1 de 1 tema …").
     */
    fun summary(matching: Int, total: Int, selected: List<Instrument>): String {
        val songs = if (total == 1) "tema" else "temas"
        return "$matching de $total $songs con cupo libre para ${names(selected)}"
    }

    /** "Ningún tema tiene cupo libre para armónica." */
    fun noResults(selected: List<Instrument>): String = "Ningún tema tiene cupo libre para ${names(selected)}."

    fun name(instrument: Instrument): String = InstrumentStripCopy.name(instrument)

    /** "bajo"; "bajo o voz"; "guitarra, bajo o voz": lowercase, in the given (chip) order. */
    private fun names(selected: List<Instrument>): String {
        val lower = selected.map { name(it).lowercase() }
        return if (lower.size <= 1) {
            lower.joinToString()
        } else {
            lower.dropLast(1).joinToString(", ") + " o " + lower.last()
        }
    }
}
