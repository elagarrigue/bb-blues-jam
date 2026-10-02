package com.bbbjam.core.data.cache

import com.bbbjam.core.model.ExtraParticipant
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SetlistProblem
import com.bbbjam.core.model.Slot
import com.bbbjam.core.model.SongId
import java.time.LocalDate
import java.time.LocalTime

private const val AVAILABLE = "AVAILABLE"
private const val WITHHELD = "WITHHELD"
private const val UNAVAILABLE = "UNAVAILABLE"

/**
 * The rows for [jams], each paired with the slot columns of its setlist positions (the mapper's
 * `MappedJam.slotColumns`), in response order: `sheet_order` is 1-based.
 */
internal fun jamRows(jams: List<Pair<Jam, Map<Int, List<Int>>>>): JamRows {
    val songs = mutableListOf<JamSongEntity>()
    val slots = mutableListOf<JamSlotEntity>()
    val extras = mutableListOf<JamExtraEntity>()
    val entities = jams.mapIndexed { index, (jam, slotColumns) ->
        val date = jam.date.toString()
        (jam.setlist as? Setlist.Available)?.songs.orEmpty().forEach { song ->
            songs += JamSongEntity(date, song.position, song.songId.value, song.title, song.artist, song.key.value)
            val columns = slotColumns.getValue(song.position)
            slots += song.lineup.slots.zip(columns) { slot, column ->
                JamSlotEntity(date, song.position, column, slot.instrument.name, slot.musicianName)
            }
            extras += song.extraParticipants.mapIndexed { order, extra ->
                JamExtraEntity(date, song.position, order + 1, extra.name, extra.instrument)
            }
        }
        jam.toEntity(sheetOrder = index + 1)
    }
    return JamRows(entities, songs, slots, extras)
}

private fun Jam.toEntity(sheetOrder: Int): JamEntity {
    val (state, problem, dropped) = when (val setlist = setlist) {
        is Setlist.Available -> Triple(AVAILABLE, null, setlist.droppedRows)
        Setlist.Withheld -> Triple(WITHHELD, null, 0)
        is Setlist.Unavailable -> Triple(UNAVAILABLE, setlist.problem.name, 0)
    }
    return JamEntity(date.toString(), sheetOrder, startTime.toString(), venue, status.name, state, problem, dropped)
}

/**
 * The jam these rows hold, built through the domain constructors. Rows are written only from mapped
 * jams, so every value is valid; an unknown setlist state or problem reads as unavailable for an
 * unknown reason rather than failing.
 */
internal fun JamWithChildren.toDomain(): Jam = Jam(
    date = LocalDate.parse(jam.date),
    startTime = LocalTime.parse(jam.startTime),
    venue = jam.venue,
    status = JamStatus.valueOf(jam.status),
    setlist = when (jam.setlistState) {
        AVAILABLE -> Setlist.Available(songs.sortedBy { it.position }.map(::toJamSong), jam.droppedRows)

        WITHHELD -> Setlist.Withheld

        else -> Setlist.Unavailable(
            SetlistProblem.entries.firstOrNull { it.name == jam.setlistProblem } ?: SetlistProblem.UNKNOWN,
        )
    },
)

private fun JamWithChildren.toJamSong(song: JamSongResolved): JamSong = JamSong(
    position = song.position,
    songId = SongId(song.songId),
    title = song.title,
    artist = song.artist,
    key = Key(song.key),
    lineup = Lineup(
        slots.filter { it.position == song.position }
            .sortedBy { it.columnIndex }
            .map { Slot(Instrument.valueOf(it.instrument), it.musicianName) },
    ),
    extraParticipants = extras.filter { it.position == song.position }
        .sortedBy { it.entryOrder }
        .map { ExtraParticipant(it.name, it.instrument) },
)
