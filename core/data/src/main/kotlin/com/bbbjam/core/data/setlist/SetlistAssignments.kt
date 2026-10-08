package com.bbbjam.core.data.setlist

import android.database.SQLException
import com.bbbjam.core.data.admin.AdminAnswer
import com.bbbjam.core.data.admin.AdminWriter
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.cache.CatalogDao
import com.bbbjam.core.data.cache.SetlistDao
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.MusicianName
import com.bbbjam.core.model.SlotPosition
import com.bbbjam.core.model.SongId
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Assignment entries, request payload and confirmed single-cell cache mirror. */
internal class SetlistAssignments(
    private val writer: AdminWriter,
    private val setlistDao: SetlistDao,
    private val catalogDao: CatalogDao,
) {
    private val entries = MutableStateFlow<List<Assignment>>(emptyList())

    fun observe() = entries.asStateFlow()

    suspend fun start(
        id: Long,
        date: LocalDate,
        songId: SongId,
        instrument: Instrument,
        ordinal: SlotPosition,
        name: MusicianName,
    ): Triple<Assignment, Target?, String?> {
        val title = displayedTitle(catalogDao, setlistDao, date, songId)
        val songs = if (setlistDao.setlistState(date.toString()) == "AVAILABLE") {
            setlistDao.songsOf(date.toString())
        } else {
            emptyList()
        }
        val song = songs.singleOrNull { it.songId == songId.value }
        val slots = song?.let { setlistDao.slotsOf(date.toString(), it.position) }.orEmpty()
        val instrumentSlots = slots.filter { it.instrument == instrument.name }.sortedBy { it.columnIndex }
        val slot = instrumentSlots.getOrNull(ordinal.value - 1)
        val target = if (song != null &&
            slot?.musicianName == null
        ) {
            slot?.let { Target(song.position, it.columnIndex) }
        } else {
            null
        }
        val localError = when {
            song == null || slot == null -> UNAVAILABLE_SLOT
            slot.musicianName != null -> SLOT_TAKEN
            else -> null
        }
        val entry = Assignment(id, date, songId, title, instrument, ordinal, name, Assignment.State.Sending)
        entries.update { it + entry }
        return Triple(entry, target, localError)
    }

    suspend fun send(entry: Assignment, target: Target?, localError: String?): AssignSlotOutcome {
        val outcome = when {
            target == null -> AssignSlotOutcome.NotAssigned(WriteOutcome.Rejected(localError ?: UNAVAILABLE_SLOT))
            else -> sendValid(entry, target)
        }
        entries.update { list ->
            when (outcome) {
                AssignSlotOutcome.Assigned -> list.filterNot { it.id == entry.id }

                is AssignSlotOutcome.NotAssigned -> list.map {
                    if (it.id == entry.id) it.copy(state = Assignment.State.Failed(outcome.reason)) else it
                }
            }
        }
        return outcome
    }

    private suspend fun sendValid(entry: Assignment, target: Target): AssignSlotOutcome {
        val fields = mapOf(
            "date" to JsonPrimitive(entry.jamDate.toString()),
            "songId" to JsonPrimitive(entry.songId.value),
            "instrument" to JsonPrimitive(entry.instrument.name.lowercase(Locale.ROOT)),
            "ordinal" to JsonPrimitive(entry.ordinal.value),
            "name" to JsonPrimitive(entry.musicianName.value),
        )
        return when (val answer = writer.send("assignSlot", fields)) {
            is AdminAnswer.Refused -> AssignSlotOutcome.NotAssigned(answer.outcome)

            is AdminAnswer.Ok -> {
                val column = (answer.body["column"] as? JsonPrimitive)
                    ?.takeUnless { it.isString }
                    ?.intOrNull
                val confirmedName = (answer.body["name"] as? JsonPrimitive)
                    ?.takeIf { it.isString }
                    ?.content
                val schemaVersion = (answer.body["schemaVersion"] as? JsonPrimitive)
                    ?.takeUnless { it.isString }
                    ?.intOrNull
                if (schemaVersion != 1 || column != target.columnIndex || confirmedName != entry.musicianName.value) {
                    return AssignSlotOutcome.NotAssigned(WriteOutcome.Unavailable)
                }
                try {
                    setlistDao.assignOpenSlot(
                        entry.jamDate.toString(),
                        entry.songId.value,
                        target.position,
                        target.columnIndex,
                        confirmedName,
                    )
                } catch (ignored: SQLException) {
                    // Sheet is authoritative; refresh repairs Room.
                }
                AssignSlotOutcome.Assigned
            }
        }
    }

    fun dismiss(id: Long) {
        entries.update { list -> list.filterNot { it.id == id && it.state is Assignment.State.Failed } }
    }

    data class Target(val position: Int, val columnIndex: Int)

    private companion object {
        const val UNAVAILABLE_SLOT = "slot_not_in_lineup"
        const val SLOT_TAKEN = "slot_taken"
    }
}
