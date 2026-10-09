package com.bbbjam.core.data.setlist

import android.database.SQLException
import com.bbbjam.core.data.DataScope
import com.bbbjam.core.data.admin.AdminAnswer
import com.bbbjam.core.data.admin.AdminWriter
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.cache.CatalogDao
import com.bbbjam.core.data.cache.JamExtraEntity
import com.bbbjam.core.data.cache.SetlistDao
import com.bbbjam.core.model.ExtraParticipant
import com.bbbjam.core.model.SongId
import java.time.LocalDate
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal class SetlistExtraParticipants(
    private val writer: AdminWriter,
    private val setlistDao: SetlistDao,
    private val catalogDao: CatalogDao,
    private val scope: DataScope,
    private val queue: SetlistMutationQueue,
) : SetlistExtraParticipantRepository {
    private val entries = MutableStateFlow<List<ExtraParticipantChange>>(emptyList())
    override fun observeExtraParticipantChanges() = entries.asStateFlow()

    override suspend fun addExtraParticipant(jamDate: LocalDate, songId: SongId, name: String, instrument: String) =
        mutate(jamDate, songId, name, instrument, null)

    override suspend fun removeExtraParticipant(
        jamDate: LocalDate,
        songId: SongId,
        ordinal: Int,
        expectedName: String,
        expectedInstrument: String,
    ) = mutate(jamDate, songId, expectedName, expectedInstrument, ordinal)

    suspend fun mutate(
        date: LocalDate,
        songId: SongId,
        name: String?,
        instrument: String?,
        ordinal: Int?,
    ): ExtraParticipantOutcome = scope.async(start = CoroutineStart.UNDISPATCHED) {
        queue.order.withLock {
            val current = cachedSong(date, songId)
            val cachedExtras = current?.let { song ->
                setlistDao.extrasFrom(date.toString(), song.position)
                    .filter { it.position == song.position }
                    .sortedBy { it.entryOrder }
                    .map { ExtraParticipant(it.name, it.instrument) }
            }.orEmpty()
            val normalizedName = name?.let(ExtraParticipantContract::normalize)
            val normalizedInstrument = instrument?.let(ExtraParticipantContract::normalize)
            val localError =
                validationError(
                    cachedExtras,
                    current != null,
                    name,
                    instrument,
                    normalizedName,
                    normalizedInstrument,
                    ordinal,
                )
            val shown =
                optimisticExtraParticipants(cachedExtras, normalizedName, normalizedInstrument, ordinal, localError)
            val title = current?.let { catalogDao.song(songId.value)?.title ?: it.titleCopy } ?: songId.value
            val entry =
                ExtraParticipantChange(
                    queue.ids.incrementAndGet(),
                    date,
                    songId,
                    title,
                    shown,
                    ExtraParticipantChange.State.Sending,
                )
            entries.update { it + entry }
            scope.async(start = CoroutineStart.UNDISPATCHED) {
                queue.writes.withLock { send(entry, ordinal, name, instrument, localError) }
            }
        }.await()
    }.await()

    private suspend fun cachedSong(date: LocalDate, songId: SongId) =
        if (setlistDao.setlistState(date.toString()) == AVAILABLE) {
            setlistDao.songsOf(date.toString()).singleOrNull { it.songId == songId.value }
        } else {
            null
        }

    private fun validationError(
        extras: List<ExtraParticipant>,
        exists: Boolean,
        name: String?,
        instrument: String?,
        normalizedName: String?,
        normalizedInstrument: String?,
        ordinal: Int?,
    ): String? = when {
        normalizedName != null && !ExtraParticipantContract.validName(normalizedName) -> INVALID_EXTRA

        normalizedInstrument != null && !ExtraParticipantContract.validInstrument(normalizedInstrument) -> INVALID_EXTRA

        ordinal != null && ordinal !in 1..MAX_EXTRAS -> INVALID_EXTRA

        !exists -> SONG_NOT_IN_SETLIST

        ordinal != null &&
            extras.getOrNull(ordinal - 1) !=
            ExtraParticipant(
                ExtraParticipantContract.normalize(name.orEmpty()),
                ExtraParticipantContract.normalize(instrument.orEmpty()),
            ) -> EXTRA_CHANGED

        ordinal == null && extras.size >= MAX_EXTRAS -> EXTRA_LIMIT

        else -> null
    }

    private suspend fun send(
        entry: ExtraParticipantChange,
        ordinal: Int?,
        name: String?,
        instrument: String?,
        localError: String?,
    ): ExtraParticipantOutcome {
        val result = localError?.let { ExtraParticipantOutcome.NotChanged(WriteOutcome.Rejected(it)) }
            ?: sendRequest(entry, ordinal, name, instrument)
        entries.update { list ->
            when (result) {
                ExtraParticipantOutcome.Changed -> list.filterNot { it.id == entry.id }

                is ExtraParticipantOutcome.NotChanged -> list.map {
                    if (it.id ==
                        entry.id
                    ) {
                        it.copy(state = ExtraParticipantChange.State.Failed(result.reason))
                    } else {
                        it
                    }
                }
            }
        }
        return result
    }

    private suspend fun sendRequest(
        entry: ExtraParticipantChange,
        ordinal: Int?,
        name: String?,
        instrument: String?,
    ): ExtraParticipantOutcome {
        val fields = mutationFields(entry, ordinal, name, instrument)
        val action = if (ordinal == null) ADD_ACTION else REMOVE_ACTION
        return when (val answer = writer.send(action, fields)) {
            is AdminAnswer.Refused -> ExtraParticipantOutcome.NotChanged(answer.outcome)
            is AdminAnswer.Ok -> confirmResponse(entry, answer.body)
        }
    }

    private fun mutationFields(
        entry: ExtraParticipantChange,
        ordinal: Int?,
        name: String?,
        instrument: String?,
    ): Map<String, JsonPrimitive> = buildMap {
        put("date", JsonPrimitive(entry.jamDate.toString()))
        put("songId", JsonPrimitive(entry.songId.value))
        if (ordinal == null) {
            put("name", JsonPrimitive(ExtraParticipantContract.normalize(name.orEmpty())))
            put("instrument", JsonPrimitive(ExtraParticipantContract.normalize(instrument.orEmpty())))
        } else {
            put("ordinal", JsonPrimitive(ordinal))
            put("expectedName", JsonPrimitive(ExtraParticipantContract.normalize(name.orEmpty())))
            put("expectedInstrument", JsonPrimitive(ExtraParticipantContract.normalize(instrument.orEmpty())))
        }
    }

    private suspend fun confirmResponse(entry: ExtraParticipantChange, body: JsonObject): ExtraParticipantOutcome {
        val extras =
            ExtraParticipantContract.parse(body) ?: return ExtraParticipantOutcome.NotChanged(WriteOutcome.Unavailable)
        try {
            setlistDao.replaceExtras(
                entry.jamDate.toString(),
                entry.songId.value,
                extras.mapIndexed { index, extra ->
                    JamExtraEntity(entry.jamDate.toString(), 0, index + 1, extra.name, extra.instrument)
                },
            )
        } catch (_: SQLException) { }
        return ExtraParticipantOutcome.Changed
    }

    fun dismiss(id: Long) {
        entries.update {
            it.filterNot { e ->
                e.id == id &&
                    e.state is ExtraParticipantChange.State.Failed
            }
        }
    }

    private companion object {
        const val AVAILABLE = "AVAILABLE"
        const val MAX_EXTRAS = 20
        const val SONG_NOT_IN_SETLIST = "song_not_in_setlist"
        const val INVALID_EXTRA = "invalid_extra"
        const val EXTRA_CHANGED = "extra_changed"
        const val EXTRA_LIMIT = "extra_limit"
        const val ADD_ACTION = "addExtraParticipant"
        const val REMOVE_ACTION = "removeExtraParticipant"
    }
}

private object ExtraParticipantContract {
    private const val MAX_LENGTH = 40
    private const val MAX_ENTRIES = 20
    private const val SCHEMA_VERSION = 1
    private val forbidden = Regex("[\\u0000-\\u001f\\u007f-\\u009f;()]")

    fun normalize(value: String): String = value.trim().replace(Regex("\\s+"), " ")

    fun validName(value: String): Boolean = value.isNotEmpty() && value.length <= MAX_LENGTH &&
        !forbidden.containsMatchIn(value) && !Regex("^[=+@-]").containsMatchIn(value) &&
        value.any(Char::isLetterOrDigit)

    fun validInstrument(value: String): Boolean = value.isNotEmpty() && value.length <= MAX_LENGTH &&
        !forbidden.containsMatchIn(value) && value.any(Char::isLetterOrDigit)

    fun parse(body: JsonObject): List<ExtraParticipant>? = (body["extras"] as? JsonArray)
        ?.takeIf { body["schemaVersion"] == JsonPrimitive(SCHEMA_VERSION) && it.size <= MAX_ENTRIES }
        ?.let { array ->
            val parsed = array.mapNotNull { item ->
                val value = item as? JsonObject
                val name = (value?.get("name") as? JsonPrimitive)?.takeIf { it.isString }?.content
                val instrument = (value?.get("instrument") as? JsonPrimitive)?.takeIf { it.isString }?.content
                if (name == null || instrument == null) {
                    null
                } else {
                    ExtraParticipant(name, instrument).takeIf { validName(name) && validInstrument(instrument) }
                }
            }
            parsed.takeIf { it.size == array.size }
        }
}

private fun optimisticExtraParticipants(
    extras: List<ExtraParticipant>,
    name: String?,
    instrument: String?,
    ordinal: Int?,
    error: String?,
): List<ExtraParticipant> = when {
    error != null -> extras
    ordinal == null -> extras + ExtraParticipant(requireNotNull(name), requireNotNull(instrument))
    else -> extras.filterIndexed { index, _ -> index != ordinal - 1 }
}
