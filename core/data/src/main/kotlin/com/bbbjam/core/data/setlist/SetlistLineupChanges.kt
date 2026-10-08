package com.bbbjam.core.data.setlist

import android.database.SQLException
import com.bbbjam.core.data.admin.AdminAnswer
import com.bbbjam.core.data.admin.AdminWriter
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.cache.CatalogDao
import com.bbbjam.core.data.cache.JamSlotEntity
import com.bbbjam.core.data.cache.SetlistDao
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.SongId
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Entry/request/cache half; the repository owns shared ordering, ids and lifetime. */
internal class SetlistLineupChanges(
    private val writer: AdminWriter,
    private val setlistDao: SetlistDao,
    private val catalogDao: CatalogDao,
) {
    private val entries = MutableStateFlow<List<LineupChange>>(emptyList())

    fun observe() = entries.asStateFlow()

    suspend fun start(id: Long, date: LocalDate, songId: SongId, instrument: Instrument, count: Int): LineupChange {
        val title = displayedTitle(catalogDao, setlistDao, date, songId)
        val entry = LineupChange(id, date, songId, title, instrument, count, LineupChange.State.Sending)
        entries.update { it + entry }
        return entry
    }

    suspend fun send(entry: LineupChange): SetSlotCountOutcome {
        val outcome = if (entry.count !in 0..Lineup.defaultCount(entry.instrument)) {
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
            "date" to JsonPrimitive(entry.jamDate.toString()),
            "songId" to JsonPrimitive(entry.songId.value),
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

    fun dismiss(id: Long) {
        entries.update { list -> list.filterNot { it.id == id && it.state is LineupChange.State.Failed } }
    }

    private companion object {
        val FIELDS = listOf("guitar1", "guitar2", "bass", "drums", "vocals", "harmonica", "keyboards")

        fun confirmedSlots(body: JsonObject, entry: LineupChange): List<JamSlotEntity>? {
            val slots = (body["slots"] as? JsonObject)?.takeIf { it.keys == FIELDS.toSet() } ?: return null
            val valid = slots.values.all {
                it == JsonNull || (it is JsonPrimitive && it.isString && it.content.isNotBlank())
            }
            return if (valid) {
                FIELDS.mapIndexedNotNull { column, field ->
                    val value = slots.getValue(field)
                    val name = (value as? JsonPrimitive)?.takeUnless { it == JsonNull }?.content
                    if (name == "-") {
                        null
                    } else {
                        JamSlotEntity(
                            entry.jamDate.toString(),
                            0,
                            column,
                            Lineup.DEFAULT_INSTRUMENTS[column].name,
                            name,
                        )
                    }
                }
            } else {
                null
            }
        }
    }
}
