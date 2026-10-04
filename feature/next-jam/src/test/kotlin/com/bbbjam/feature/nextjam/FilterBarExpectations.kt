package com.bbbjam.feature.nextjam

import com.bbbjam.core.ui.filter.FilterChipUiModel
import com.bbbjam.core.ui.filter.InstrumentFilterBarUiModel
import com.bbbjam.core.ui.presenter.EventHandler

/** The six instrument chip labels, in chip order, written out from the approved copy (C1). */
internal val instrumentChipLabels = listOf("Guitarra", "Bajo", "Batería", "Voz", "Armónica", "Teclados")

private fun songsWith(n: Int) = when (n) {
    0 -> "ningún tema"
    1 -> "1 tema"
    else -> "$n temas"
}

/**
 * The expected instrument filter bar (`instrument-filter-chips`), written out from the approved copy
 * (C1), never read from the shipped copy: `Todos` with [total], then the six instruments with
 * [counts]; [selected] holds chip labels. Handlers without a key compare equal.
 */
internal fun bar(
    total: Int,
    counts: List<Int> = List(instrumentChipLabels.size) { total },
    selected: Set<String> = emptySet(),
    summary: String? = null,
    noResults: String? = null,
) = InstrumentFilterBarUiModel(
    heading = "Filtrá por cupo libre",
    chips = listOf(chip("Todos", "$total", "Todos los temas: $total", selected.isEmpty())) +
        instrumentChipLabels.zip(counts) { name, n ->
            chip(name, "$n", "$name: ${songsWith(n)} con cupo libre", name in selected)
        },
    summary = summary,
    noResults = noResults,
    clearLabel = "Ver todos los temas",
    events = EventHandler {},
)

private fun chip(label: String, count: String, description: String, selected: Boolean) =
    FilterChipUiModel(label, count, description, selected, EventHandler {})
