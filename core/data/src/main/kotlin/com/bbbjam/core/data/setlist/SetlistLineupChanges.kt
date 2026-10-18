package com.bbbjam.core.data.setlist

import android.database.SQLException
import com.bbbjam.core.data.DataScope
import com.bbbjam.core.data.admin.AdminAnswer
import com.bbbjam.core.data.admin.AdminWriter
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.cache.CatalogDao
import com.bbbjam.core.data.cache.JamSlotEntity
import com.bbbjam.core.data.cache.SetlistDao
import com.bbbjam.core.data.remote.SlotKeys
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.SongId
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Entry/request/cache half; the repository owns shared ordering, ids and lifetime. */
internal class SetlistLineupChanges(
    private val writer: AdminWriter,
    private val setlistDao: SetlistDao,
    private val catalogDao: CatalogDao,
    private val scope: DataScope,
    private val queue: SetlistMutationQueue,
) : SetlistLineupRepository {
    private val entries = MutableStateFlow<List<LineupChange>>(emptyList())

    override fun observeLineupChanges() = entries.asStateFlow()

    override suspend fun setSlotCount(
        jamDate: LocalDate,
        songId: SongId,
        instrument: Instrument,
        count: Int,
    ): SetSlotCountOutcome = scope.async(start = CoroutineStart.UNDISPATCHED) {
        queue.order.withLock {
            val entry = start(queue.ids.incrementAndGet(), jamDate, songId, instrument, count)
            scope.async(start = CoroutineStart.UNDISPATCHED) { queue.writes.withLock { send(entry) } }
        }.await()
    }.await()

    suspend fun start(id: Long, date: LocalDate, songId: SongId, instrument: Instrument, count: Int): LineupChange {
        val title = displayedTitle(catalogDao, setlistDao, date, songId)
        val entry = LineupChange(id, date, songId, title, instrument, count, LineupChange.State.Sending)
        entries.update { it + entry }
        return entry
    }

    suspend fun send(entry: LineupChange): SetSlotCountOutcome {
        val outcome = if (entry.count !in 0..Lineup.maxCount(entry.instrument)) {
            SetSlotCountOutcome.NotSet(WriteOutcome.Rejected("invalid_count"))
        } else {
            sendValid(entry)
        }
        entries.update { list ->
            when (outcome) {
                SetSlotCountOutcome.SlotCountSet -> list.filterNot { it.id == entry.id }

                is SetSlotCountOutcome.NotSet -> list.map {
                    if (it.id == entry.id) it.copy(state = LineupChange.State.Failed(outcome.reason)) else it
                }
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

    override fun dismissLineupChange(id: Long) {
        entries.update { list -> list.filterNot { it.id == id && it.state is LineupChange.State.Failed } }
    }

    private companion object {
        fun confirmedSlots(body: JsonObject, entry: LineupChange): List<JamSlotEntity>? =
            (body["slots"] as? JsonObject)?.let(SlotKeys::cells)?.takeIf { cells -> cells.all(::validCell) }
                ?.let { cells -> slotEntities(cells, entry.jamDate.toString()) }
    }
}
