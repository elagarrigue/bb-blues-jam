package com.bbbjam.core.data.setlist.lineup

import android.database.SQLException
import com.bbbjam.core.data.DataScope
import com.bbbjam.core.data.admin.AdminAnswer
import com.bbbjam.core.data.admin.AdminWriter
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.cache.CatalogDao
import com.bbbjam.core.data.cache.JamSlotEntity
import com.bbbjam.core.data.cache.SetlistDao
import com.bbbjam.core.data.remote.SlotKeys
import com.bbbjam.core.data.setlist.engine.PendingEntries
import com.bbbjam.core.data.setlist.engine.QueuedWrite
import com.bbbjam.core.data.setlist.engine.SetlistMutationQueue
import com.bbbjam.core.data.setlist.engine.SetlistRequestFields
import com.bbbjam.core.data.setlist.engine.displayedTitle
import com.bbbjam.core.data.setlist.engine.enqueue
import com.bbbjam.core.data.setlist.engine.slotEntities
import com.bbbjam.core.data.setlist.engine.validCell
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.SongId
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Lineup-size entries, request and confirmed cache mirror, queued on the shared [SetlistMutationQueue]. */
internal class SetlistLineupChanges(
    private val writer: AdminWriter,
    private val setlistDao: SetlistDao,
    private val catalogDao: CatalogDao,
    private val scope: DataScope,
    private val queue: SetlistMutationQueue,
) : SetlistLineupRepository {
    private val entries = PendingEntries<LineupChange>({ it.id }) { it.state is LineupChange.State.Failed }

    override fun observeLineupChanges(): Flow<List<LineupChange>> = entries.observe()

    override suspend fun setSlotCount(
        jamDate: LocalDate,
        songId: SongId,
        instrument: Instrument,
        count: Int,
    ): SetSlotCountOutcome = queue.enqueue(scope) { id ->
        val entry = start(id, jamDate, songId, instrument, count)
        QueuedWrite.Send { send(entry) }
    }

    private suspend fun start(
        id: Long,
        date: LocalDate,
        songId: SongId,
        instrument: Instrument,
        count: Int,
    ): LineupChange {
        val title = displayedTitle(catalogDao, setlistDao, date, songId)
        val entry = LineupChange(id, date, songId, title, instrument, count, LineupChange.State.Sending)
        entries.add(entry)
        return entry
    }

    private suspend fun send(entry: LineupChange): SetSlotCountOutcome {
        val outcome = if (entry.count !in 0..Lineup.maxCount(entry.instrument)) {
            SetSlotCountOutcome.NotSet(WriteOutcome.Rejected("invalid_count"))
        } else {
            sendValid(entry)
        }
        when (outcome) {
            SetSlotCountOutcome.SlotCountSet -> entries.remove(entry.id)

            is SetSlotCountOutcome.NotSet -> entries.fail(entry.id) {
                it.copy(state = LineupChange.State.Failed(outcome.reason))
            }
        }
        return outcome
    }

    private suspend fun sendValid(entry: LineupChange): SetSlotCountOutcome {
        val fields = mapOf(
            SetlistRequestFields.DATE to JsonPrimitive(entry.jamDate.toString()),
            SetlistRequestFields.SONG_ID to JsonPrimitive(entry.songId.value),
            "instrument" to JsonPrimitive(entry.instrument.name.lowercase(Locale.ROOT)),
            "count" to JsonPrimitive(entry.count),
        )
        return when (val answer = writer.send("setSlotCount", fields)) {
            is AdminAnswer.Refused -> SetSlotCountOutcome.NotSet(answer.outcome)

            is AdminAnswer.Ok -> {
                val slots = confirmedSlots(answer.body, entry)
                    ?: return SetSlotCountOutcome.NotSet(WriteOutcome.Unavailable)
                try {
                    setlistDao.replaceSlots(entry.jamDate.toString(), entry.songId.value, slots)
                } catch (ignored: SQLException) {
                    // The Sheet succeeded; a subsequent refresh repairs the local cache.
                }
                SetSlotCountOutcome.SlotCountSet
            }
        }
    }

    override fun dismissLineupChange(id: Long) = entries.dismiss(id)

    private companion object {
        fun confirmedSlots(body: JsonObject, entry: LineupChange): List<JamSlotEntity>? =
            (body["slots"] as? JsonObject)?.let(SlotKeys::cells)?.takeIf { cells -> cells.all(::validCell) }
                ?.let { cells -> slotEntities(cells, entry.jamDate.toString()) }
    }
}
