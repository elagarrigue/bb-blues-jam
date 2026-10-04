package com.bbbjam.core.ui.filter

import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.ui.presenter.EventHandler

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
        label = InstrumentFilterCopy.ALL,
        count = total.toString(),
        contentDescription = InstrumentFilterCopy.allDescription(total),
        isSelected = selected.isEmpty(),
        events = EventHandler { onChange(InstrumentFilterChange.Clear) },
    )
    val instruments = Instrument.entries.map { instrument ->
        val count = lineups.count { it.hasOpenSlotFor(instrument) }
        FilterChipUiModel(
            label = InstrumentFilterCopy.name(instrument),
            count = count.toString(),
            contentDescription = InstrumentFilterCopy.instrumentDescription(instrument, count),
            isSelected = instrument in selected,
            events = EventHandler { onChange(InstrumentFilterChange.Toggle(instrument)) },
        )
    }
    val inChipOrder = Instrument.entries.filter { it in selected }
    val matching = lineups.count { it.matchesInstrumentFilter(selected) }
    return InstrumentFilterBarUiModel(
        heading = InstrumentFilterCopy.HEADING,
        chips = listOf(all) + instruments,
        summary = if (selected.isNotEmpty() && matching > 0) {
            InstrumentFilterCopy.summary(matching, total, inChipOrder)
        } else {
            null
        },
        noResults = if (selected.isNotEmpty() && matching == 0) InstrumentFilterCopy.noResults(inChipOrder) else null,
        clearLabel = InstrumentFilterCopy.CLEAR,
        events = EventHandler { onChange(InstrumentFilterChange.Clear) },
    )
}
