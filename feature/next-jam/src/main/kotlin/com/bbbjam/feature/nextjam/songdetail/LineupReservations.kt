package com.bbbjam.feature.nextjam.songdetail

import androidx.compose.runtime.mutableStateMapOf
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.core.ui.text.lowercaseName
import com.bbbjam.feature.nextjam.LineupEditorLineUiModel
import com.bbbjam.feature.nextjam.LineupEditorUiModel
import com.bbbjam.feature.nextjam.R
import com.bbbjam.feature.nextjam.admin.removalKey
import java.time.LocalDate

/** Immediate target reservation: retained handlers see earlier taps before recomposition. */
internal class LineupReservations {
    private data class Target(val count: Int, val token: Long)
    private val targets = mutableStateMapOf<Triple<LocalDate, SongId, Instrument>, Target>()
    private var nextToken = 0L

    fun reserve(date: LocalDate, id: SongId, instrument: Instrument, count: Int): Long {
        val token = ++nextToken
        targets[Triple(date, id, instrument)] = Target(count, token)
        return token
    }

    fun release(date: LocalDate, id: SongId, instrument: Instrument, token: Long) {
        val key = Triple(date, id, instrument)
        if (targets[key]?.token == token) targets.remove(key)
    }

    fun has(date: LocalDate, id: SongId): Boolean = targets.keys.any { it.first == date && it.second == id }

    fun overlay(date: LocalDate, id: SongId, confirmed: Lineup): Lineup = targets.entries
        .filter { it.key.first == date && it.key.second == id }
        .fold(confirmed) { lineup, (key, target) -> lineup.withSlotCount(key.third, target.count) ?: lineup }
}

internal fun SongAdminState.lineupEditor(date: LocalDate, song: JamSong): LineupEditorUiModel = LineupEditorUiModel(
    heading = UiText.res(R.string.next_jam_lineup_heading),
    lines = Instrument.entries.map { instrument -> lineupLine(date, song, instrument) },
)

private fun SongAdminState.lineupLine(date: LocalDate, song: JamSong, instrument: Instrument): LineupEditorLineUiModel {
    val count = song.lineup.count(instrument)
    val canRemove = count > 0 && song.lineup.withSlotCount(instrument, count - 1) != null
    val name = instrument.lowercaseName()
    return LineupEditorLineUiModel(
        instrument = instrument,
        countLabel = lineupCount(count),
        removeDescription = UiText.res(R.string.next_jam_lineup_remove_slot, name),
        addDescription = UiText.res(R.string.next_jam_lineup_add_slot, name),
        canRemove = canRemove,
        canAdd = count < Lineup.maxCount(instrument),
        blockedNote = if (count > 0 && !canRemove) UiText.res(R.string.next_jam_lineup_blocked) else null,
        events = EventHandler(key = "${removalKey(date, song.songId)}|$instrument|count") {
            onLineupCount(date, song.songId, instrument, it)
        },
    )
}

/** "No va en este tema", "1 cupo", "3 cupos". */
private fun lineupCount(count: Int): UiText = if (count == 0) {
    UiText.res(R.string.next_jam_lineup_count_none)
} else {
    UiText.plural(R.plurals.next_jam_lineup_count, count)
}
