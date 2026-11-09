package com.bbbjam.core.ui.filter

import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.RosterEntry
import com.bbbjam.core.model.musicianMatchKey
import com.bbbjam.core.ui.R
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.text.UiText

/**
 * The musician block for a setlist whose [roster] is given. [selected] is the remembered or chosen
 * name (null: none). [instruments] is the instrument selection; when it is not empty the combined
 * messages name it. Pure: the presenter filters its rows and passes the [counts], so the line number
 * is the number of rows drawn.
 */
fun musicianFilter(
    roster: List<RosterEntry>,
    selected: String?,
    counts: MusicianFilterCounts,
    instruments: Set<Instrument>,
    onChange: (MusicianFilterChange) -> Unit,
): MusicianFilterUiModel {
    val phrase = instrumentNames(instruments)
    val selectedKey = selected?.let(::musicianMatchKey)
    val shownName = selected?.let { name -> roster.firstOrNull { it.matchKey == selectedKey }?.name ?: name }
    val options = roster.map { entry ->
        MusicianOptionUiModel(
            name = entry.name,
            label = UiText.plural(R.plurals.musician_filter_option, entry.songCount, entry.name, entry.songCount),
            isSelected = entry.matchKey == selectedKey,
            events = EventHandler { onChange(MusicianFilterChange.Select(entry.name)) },
        )
    }
    val chosen = shownName != null
    return MusicianFilterUiModel(
        heading = UiText.res(R.string.musician_filter_heading),
        selectedName = shownName,
        pickLabel = UiText.res(R.string.musician_filter_pick),
        clearLabel = UiText.res(R.string.musician_filter_clear),
        pickerTitle = UiText.res(R.string.musician_filter_picker_title),
        allLabel = UiText.res(R.string.musician_filter_all),
        options = options,
        summary = if (chosen && counts.shown > 0) summary(shownName, counts, phrase) else null,
        noResults = if (chosen && counts.shown == 0) noResults(shownName, counts, phrase) else null,
        noResultsClearLabel = UiText.res(R.string.filter_clear),
        events = EventHandler { event ->
            onChange(
                when (event) {
                    MusicianFilterUiModel.Event.Clear -> MusicianFilterChange.Clear
                    MusicianFilterUiModel.Event.ClearAll -> MusicianFilterChange.ClearAll
                },
            )
        },
    )
}

private fun summary(name: String, counts: MusicianFilterCounts, instrumentNames: UiText?): UiText =
    if (instrumentNames == null) {
        UiText.plural(R.plurals.musician_filter_summary, counts.shown, counts.shown, counts.total, name)
    } else {
        UiText.plural(
            R.plurals.musician_filter_summary_instrument,
            counts.shown,
            counts.shown,
            counts.total,
            name,
            instrumentNames,
        )
    }

private fun noResults(name: String, counts: MusicianFilterCounts, instrumentNames: UiText?): UiText =
    if (instrumentNames != null && counts.withMusician > 0) {
        UiText.res(R.string.musician_filter_no_results_combined, name, instrumentNames)
    } else {
        UiText.res(R.string.musician_filter_no_results, name)
    }
