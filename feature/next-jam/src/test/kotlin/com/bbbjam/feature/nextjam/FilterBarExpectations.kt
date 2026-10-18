package com.bbbjam.feature.nextjam

import com.bbbjam.core.model.Instrument
import com.bbbjam.core.ui.R as CoreR
import com.bbbjam.core.ui.filter.FilterChipUiModel
import com.bbbjam.core.ui.filter.InstrumentFilterBarUiModel
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.core.ui.text.fullName
import com.bbbjam.core.ui.text.lowercaseName

/** The six instrument chip labels, in chip order, as the approved copy writes them (C1). */
internal val instrumentChipLabels = listOf("Guitarra", "Bajo", "Batería", "Voz", "Armónica", "Teclados")

private val allText = UiText.res(CoreR.string.filter_all)

/** The chip labels' instruments, in chip order. */
private val chipInstruments = Instrument.entries.zip(instrumentChipLabels).associate { (instrument, label) ->
    label to
        instrument
}

/** The text of the chip named [label] ("Todos" or an instrument name). */
internal fun chipText(label: String): UiText = if (label ==
    "Todos"
) {
    allText
} else {
    checkNotNull(chipInstruments[label]) { "Unknown chip $label" }.fullName()
}

/** The name of the chip a text belongs to; the inverse of [chipText]. */
internal fun UiText.chipName(): String = (listOf("Todos") + instrumentChipLabels).single { chipText(it) == this }

private fun songsWith(n: Int): UiText = when (n) {
    0 -> UiText.res(CoreR.string.filter_songs_none)
    else -> UiText.plural(CoreR.plurals.filter_songs_count, n)
}

/** The selected instruments' names inside a sentence: "bajo", "bajo o voz", "guitarra, bajo o voz". */
private fun namesOf(selected: List<Instrument>): UiText {
    val lower = selected.map { it.lowercaseName() }
    return when (lower.size) {
        1 -> lower[0]

        2 -> UiText.res(CoreR.string.filter_names_or, lower[0], lower[1])

        else -> UiText.res(
            CoreR.string.filter_names_or,
            UiText.res(CoreR.string.filter_names_comma, lower[0], lower[1]),
            lower[2],
        )
    }
}

/** "2 de 13 temas con cupo libre para bajo o voz". */
internal fun filterSummary(matching: Int, total: Int, vararg selected: Instrument): UiText =
    UiText.plural(CoreR.plurals.filter_summary, total, matching, total, namesOf(selected.toList()))

/** "Ningún tema tiene cupo libre para armónica." */
internal fun filterNoResults(vararg selected: Instrument): UiText =
    UiText.res(CoreR.string.filter_no_results, namesOf(selected.toList()))

/**
 * The expected instrument filter bar (`instrument-filter-chips`): `Todos` with [total], then the six
 * instruments with [counts]; [selected] holds chip labels. Handlers without a key compare equal.
 */
internal fun bar(
    total: Int,
    counts: List<Int> = List(instrumentChipLabels.size) { total },
    selected: Set<String> = emptySet(),
    summary: UiText? = null,
    noResults: UiText? = null,
) = InstrumentFilterBarUiModel(
    heading = UiText.res(CoreR.string.filter_heading),
    chips =
        listOf(chip("Todos", "$total", UiText.res(CoreR.string.filter_all_description, total), selected.isEmpty())) +
            instrumentChipLabels.zip(counts) { name, n ->
                chip(
                    name,
                    "$n",
                    UiText.res(CoreR.string.filter_instrument_description, chipText(name), songsWith(n)),
                    name in selected,
                )
            },
    summary = summary,
    noResults = noResults,
    clearLabel = UiText.res(CoreR.string.filter_clear),
    events = EventHandler {},
)

private fun chip(label: String, count: String, description: UiText, selected: Boolean) =
    FilterChipUiModel(chipText(label), count, description, selected, EventHandler {})
