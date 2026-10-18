package com.bbbjam.feature.nextjam

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.bbbjam.core.model.ExtraParticipant
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Slot
import com.bbbjam.core.ui.filter.instrumentFilterBar
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.strip.toInstrumentChips
import com.bbbjam.core.ui.text.SongListText
import com.bbbjam.core.ui.theme.BluesJamTheme

internal fun previewRow(
    position: Int,
    title: String,
    key: String,
    lineup: Lineup,
    extras: List<ExtraParticipant> = emptyList(),
) = SongRowUiModel(
    position = position,
    positionLabel = SongListText.positionLabel(position),
    title = title,
    key = key,
    keyDescription = SongListText.keyDescription(key),
    instruments = lineup.toInstrumentChips(extras),
    openDetailLabel = NextJamCopy.OPEN_DETAIL,
    events = EventHandler {},
)

@Preview
@Composable
private fun NextJamScreenPreview() {
    val mixed = Lineup(
        listOf(
            Slot(Instrument.GUITAR),
            Slot(Instrument.GUITAR, "Tincho"),
            Slot(Instrument.BASS, "Nico"),
            Slot(Instrument.DRUMS),
            Slot(Instrument.VOCALS),
            Slot(Instrument.HARMONICA, "Mono"),
        ),
    )
    val saxo = listOf(ExtraParticipant("Juan", "saxo"))
    val rows = listOf(
        previewRow(1, "Sweet Little Angel", "B", Lineup.default()),
        previewRow(2, "Walking Thru the Park", "A", mixed, saxo),
        previewRow(4, "Blues Del Politico", "C", Lineup(emptyList())),
        previewRow(5, "The Thrill Is Gone", "Bm", Lineup.default()),
    )
    val lineups = listOf(Lineup.default(), mixed, Lineup(emptyList()), Lineup.default())
    val model = NextJamUiModel.Jam(
        header = JamHeaderUiModel("Sábado 31 de octubre · 21:00", "La Macanuda", "En 29 días"),
        setlist = SetlistUiModel.Songs(
            rows = rows,
            droppedRowsNote = "Falta 1 tema: no se pudo leer.",
            filterBar = instrumentFilterBar(lineups, emptySet()) {},
        ),
        staleness = null,
    )
    BluesJamTheme { NextJamContent(model = model, modifier = Modifier, contentPadding = PaddingValues()) }
}
