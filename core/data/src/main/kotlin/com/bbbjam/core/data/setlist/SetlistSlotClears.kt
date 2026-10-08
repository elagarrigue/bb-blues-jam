package com.bbbjam.core.data.setlist

import android.database.SQLException
import com.bbbjam.core.data.DataScope
import com.bbbjam.core.data.admin.AdminAnswer
import com.bbbjam.core.data.admin.AdminWriter
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.cache.CatalogDao
import com.bbbjam.core.data.cache.JamSlotEntity
import com.bbbjam.core.data.cache.SetlistDao
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.SlotPosition
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
import kotlinx.serialization.json.intOrNull

/** Compare-and-clear entry, request and confirmed cache mirror. */
internal class SetlistSlotClears(
    private val writer: AdminWriter,
    private val setlistDao: SetlistDao,
    private val catalogDao: CatalogDao,
    private val scope: DataScope,
    private val queue: SetlistMutationQueue,
) : SetlistSlotClearRepository {
    private val entries = MutableStateFlow<List<SlotClear>>(emptyList())

    fun observe() = entries.asStateFlow()

    override suspend fun clearSlot(
        jamDate: LocalDate,
        songId: SongId,
        instrument: Instrument,
        ordinal: SlotPosition,
        expectedName: String,
    ): ClearSlotOutcome = scope.async(start = CoroutineStart.UNDISPATCHED) {
        queue.order.withLock {
            val (entry, target, localError) = start(
                queue.ids.incrementAndGet(),
                jamDate,
                songId,
                instrument,
                ordinal,
                expectedName,
            )
            scope.async(start = CoroutineStart.UNDISPATCHED) {
                queue.writes.withLock { send(entry, target, localError) }
            }
        }.await()
    }.await()

    override fun observeSlotClears() = observe()

    suspend fun start(
        id: Long,
        date: LocalDate,
        songId: SongId,
        instrument: Instrument,
        ordinal: SlotPosition,
        expectedName: String,
    ): Triple<SlotClear, Target?, String?> {
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
        val target = if (song != null && slot?.musicianName == expectedName) {
            slot?.let { Target(song.position, it.columnIndex) }
        } else {
            null
        }
        val localError = when {
            song == null || slot == null -> UNAVAILABLE_SLOT
            slot.musicianName == null -> SLOT_EMPTY
            slot.musicianName != expectedName -> SLOT_CHANGED
            else -> null
        }
        val entry = SlotClear(id, date, songId, title, instrument, ordinal, expectedName, SlotClear.State.Sending)
        entries.update { it + entry }
        return Triple(entry, target, localError)
    }

    suspend fun send(entry: SlotClear, target: Target?, localError: String?): ClearSlotOutcome {
        val outcome = when {
            target == null -> ClearSlotOutcome.NotCleared(WriteOutcome.Rejected(localError ?: UNAVAILABLE_SLOT))
            else -> sendValid(entry, target)
        }
        entries.update { list ->
            when (outcome) {
                ClearSlotOutcome.Cleared -> list.filterNot { it.id == entry.id }

                is ClearSlotOutcome.NotCleared -> list.map {
                    if (it.id == entry.id) it.copy(state = SlotClear.State.Failed(outcome.reason)) else it
                }
            }
        }
        return outcome
    }

    private suspend fun sendValid(entry: SlotClear, target: Target): ClearSlotOutcome {
        val fields = mapOf(
            "date" to JsonPrimitive(entry.jamDate.toString()),
            "songId" to JsonPrimitive(entry.songId.value),
            "instrument" to JsonPrimitive(entry.instrument.name.lowercase(Locale.ROOT)),
            "ordinal" to JsonPrimitive(entry.ordinal.value),
            "expectedName" to JsonPrimitive(entry.musicianName),
        )
        return when (val answer = writer.send("clearSlot", fields)) {
            is AdminAnswer.Refused -> ClearSlotOutcome.NotCleared(answer.outcome)

            is AdminAnswer.Ok -> {
                val column = (answer.body["column"] as? JsonPrimitive)
                    ?.takeUnless { it.isString }
                    ?.intOrNull
                val slots = confirmedSlots(answer.body, entry)
                if (column != target.columnIndex || slots == null) {
                    return ClearSlotOutcome.NotCleared(WriteOutcome.Unavailable)
                }
                try {
                    setlistDao.replaceSlots(entry.jamDate.toString(), entry.songId.value, slots)
                } catch (ignored: SQLException) {
                    // Sheet is authoritative; refresh repairs Room.
                }
                ClearSlotOutcome.Cleared
            }
        }
    }

    fun dismiss(id: Long) {
        entries.update { list -> list.filterNot { it.id == id && it.state is SlotClear.State.Failed } }
    }

    data class Target(val position: Int, val columnIndex: Int)

    private companion object {
        const val UNAVAILABLE_SLOT = "slot_not_in_lineup"
        const val SLOT_EMPTY = "slot_empty"
        const val SLOT_CHANGED = "slot_changed"
        val FIELDS = listOf("guitar1", "guitar2", "bass", "drums", "vocals", "harmonica", "keyboards")

        fun confirmedSlots(body: JsonObject, entry: SlotClear): List<JamSlotEntity>? =
            responseSlots(body)?.let { slots ->
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
                            com.bbbjam.core.model.Lineup.DEFAULT_INSTRUMENTS[column].name,
                            name,
                        )
                    }
                }
            }

        private fun responseSlots(body: JsonObject): JsonObject? = (body["slots"] as? JsonObject)
            ?.takeIf { body["schemaVersion"] == JsonPrimitive(1) }
            ?.takeIf { it.keys == FIELDS.toSet() }
            ?.takeIf { slots -> slots.values.all(::validSlotValue) }

        private fun validSlotValue(value: kotlinx.serialization.json.JsonElement): Boolean =
            value == JsonNull || (value is JsonPrimitive && value.isString && value.content.isNotBlank())
    }
}
