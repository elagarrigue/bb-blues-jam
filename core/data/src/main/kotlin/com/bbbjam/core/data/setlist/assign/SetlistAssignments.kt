package com.bbbjam.core.data.setlist.assign

import android.database.SQLException
import com.bbbjam.core.data.DataScope
import com.bbbjam.core.data.admin.AdminAnswer
import com.bbbjam.core.data.admin.AdminWriter
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.cache.CatalogDao
import com.bbbjam.core.data.cache.SetlistDao
import com.bbbjam.core.data.remote.ServiceCodes
import com.bbbjam.core.data.setlist.engine.PendingEntries
import com.bbbjam.core.data.setlist.engine.QueuedWrite
import com.bbbjam.core.data.setlist.engine.SetlistMutationQueue
import com.bbbjam.core.data.setlist.engine.SetlistRequestFields
import com.bbbjam.core.data.setlist.engine.displayedTitle
import com.bbbjam.core.data.setlist.engine.enqueue
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.MusicianName
import com.bbbjam.core.model.SlotPosition
import com.bbbjam.core.model.SongId
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Assignment entries, request payload and confirmed single-cell cache mirror. */
internal class SetlistAssignments(
    private val writer: AdminWriter,
    private val setlistDao: SetlistDao,
    private val catalogDao: CatalogDao,
    private val scope: DataScope,
    private val queue: SetlistMutationQueue,
) : SetlistAssignmentRepository {
    private val entries = PendingEntries<Assignment>({ it.id }) { it.state is Assignment.State.Failed }

    override suspend fun assignSlot(
        jamDate: LocalDate,
        songId: SongId,
        instrument: Instrument,
        ordinal: SlotPosition,
        musicianName: MusicianName,
    ): AssignSlotOutcome = queue.enqueue(scope) { id ->
        val (entry, target, localError) = start(id, jamDate, songId, instrument, ordinal, musicianName)
        QueuedWrite.Send { send(entry, target, localError) }
    }

    override fun observeAssignments(): Flow<List<Assignment>> = entries.observe()

    override fun dismissAssignment(id: Long) = entries.dismiss(id)

    private suspend fun start(
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
            song == null || slot == null -> ServiceCodes.SLOT_NOT_IN_LINEUP
            slot.musicianName != null -> SLOT_TAKEN
            else -> null
        }
        val entry = Assignment(id, date, songId, title, instrument, ordinal, name, Assignment.State.Sending)
        entries.add(entry)
        return Triple(entry, target, localError)
    }

    private suspend fun send(entry: Assignment, target: Target?, localError: String?): AssignSlotOutcome {
        val outcome = when {
            target == null -> AssignSlotOutcome.NotAssigned(
                WriteOutcome.Rejected(localError ?: ServiceCodes.SLOT_NOT_IN_LINEUP),
            )

            else -> sendValid(entry, target)
        }
        when (outcome) {
            AssignSlotOutcome.Assigned -> entries.remove(entry.id)

            is AssignSlotOutcome.NotAssigned -> entries.fail(entry.id) {
                it.copy(state = Assignment.State.Failed(outcome.reason))
            }
        }
        return outcome
    }

    private suspend fun sendValid(entry: Assignment, target: Target): AssignSlotOutcome {
        val fields = mapOf(
            SetlistRequestFields.DATE to JsonPrimitive(entry.jamDate.toString()),
            SetlistRequestFields.SONG_ID to JsonPrimitive(entry.songId.value),
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

    private data class Target(val position: Int, val columnIndex: Int)

    private companion object {
        const val SLOT_TAKEN = "slot_taken"
    }
}
