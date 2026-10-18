package com.bbbjam.core.ui.filter

import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.ui.R
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.core.ui.text.fullName
import com.bbbjam.core.ui.text.lowercaseName

/**
 * True when this lineup is shown under [selected]: nothing selected, or an open slot for **any**
 * selected instrument (OR). Only slots count, through [Lineup.hasOpenSlotFor]; an extra participant
 * is never open (D-18), and a lineup with no open slot matches no instrument.
 */
fun Lineup.matchesInstrumentFilter(selected: Set<Instrument>): Boolean =
    selected.isEmpty() || selected.any { hasOpenSlotFor(it) }

/**
 * The filter bar for a setlist whose lineups are [lineups], in position order. Chips: `Todos` with
 * the number of songs, then one per [Instrument] in declaration order with the number of songs that
 * have an open slot for it. Each chip's handler sends its [InstrumentFilterChange] to [onChange]
 * (`Todos` and the clear action send [InstrumentFilterChange.Clear]).
 *
 * Pure: the presenter calls it and filters its rows with [matchesInstrumentFilter], so the summary's
 * count is always the number of rows drawn.
 */
fun instrumentFilterBar(
    lineups: List<Lineup>,
    selected: Set<Instrument>,
    onChange: (InstrumentFilterChange) -> Unit,
): InstrumentFilterBarUiModel {
    val total = lineups.size
    val all = FilterChipUiModel(
        label = UiText.res(R.string.filter_all),
        count = total.toString(),
        contentDescription = UiText.res(R.string.filter_all_description, total),
        isSelected = selected.isEmpty(),
        events = EventHandler { onChange(InstrumentFilterChange.Clear) },
    )
    val instruments = Instrument.entries.map { instrument ->
        val count = lineups.count { it.hasOpenSlotFor(instrument) }
        FilterChipUiModel(
            label = instrument.fullName(),
            count = count.toString(),
            contentDescription = instrumentDescription(instrument, count),
            isSelected = instrument in selected,
            events = EventHandler { onChange(InstrumentFilterChange.Toggle(instrument)) },
        )
    }
    val inChipOrder = Instrument.entries.filter { it in selected }
    val matching = lineups.count { it.matchesInstrumentFilter(selected) }
    return InstrumentFilterBarUiModel(
        heading = UiText.res(R.string.filter_heading),
        chips = listOf(all) + instruments,
        summary = if (selected.isNotEmpty() && matching > 0) {
            UiText.plural(R.plurals.filter_summary, total, matching, total, names(inChipOrder))
        } else {
            null
        },
        noResults = if (selected.isNotEmpty() &&
            matching == 0
        ) {
            UiText.res(R.string.filter_no_results, names(inChipOrder))
        } else {
            null
        },
        clearLabel = UiText.res(R.string.filter_clear),
        events = EventHandler { onChange(InstrumentFilterChange.Clear) },
    )
}

/** "Bajo: 2 temas con cupo libre", "Bajo: 1 tema con cupo libre", "Bajo: ningún tema con cupo libre". */
private fun instrumentDescription(instrument: Instrument, count: Int): UiText {
    val songs = if (count == 0) {
        UiText.res(R.string.filter_songs_none)
    } else {
        UiText.plural(R.plurals.filter_songs_count, count)
    }
    return UiText.res(R.string.filter_instrument_description, instrument.fullName(), songs)
}

/** "bajo"; "bajo o voz"; "guitarra, bajo o voz": lowercase, in the given (chip) order. */
private fun names(selected: List<Instrument>): UiText {
    val lower = selected.map { it.lowercaseName() }
    val head = lower.dropLast(1).reduceOrNull { joined, next -> UiText.res(R.string.filter_names_comma, joined, next) }
    return if (head == null) lower.single() else UiText.res(R.string.filter_names_or, head, lower.last())
}
