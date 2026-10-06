package com.bbbjam.core.data.setlist

import android.database.SQLException
import com.bbbjam.core.data.DataScope
import com.bbbjam.core.data.admin.AdminAnswer
import com.bbbjam.core.data.admin.AdminWriter
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.cache.CatalogDao
import com.bbbjam.core.data.cache.JamSlotEntity
import com.bbbjam.core.data.cache.JamSongEntity
import com.bbbjam.core.data.cache.SetlistDao
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.SongId
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * [SetlistRepository] over the admin POST path. The pending and failed entries live in memory; the
 * cache only ever receives a song the server confirmed, built from its answer, so a failed write
 * leaves no row anywhere.
 *
 * Ordering: [order] is held while a call resolves its song, publishes its entry and queues its write
 * on [writes] (both mutexes are fair), so writes are sent one at a time in call order. The write
 * runs in [scope], and the caller only awaits it.
 */
internal class DefaultSetlistRepository(
    private val writer: AdminWriter,
    private val setlistDao: SetlistDao,
    private val catalogDao: CatalogDao,
    private val scope: DataScope,
) : SetlistRepository {

    private val entries = MutableStateFlow<List<SetlistAdd>>(emptyList())
    private val ids = AtomicLong(0)
    private val order = Mutex()
    private val writes = Mutex()

    /**
     * Runs in [scope] from the first instruction (undispatched), so a caller cancelled at any point,
     * even before the song is resolved, never cancels an add it started: a presenter may launch it
     * and close its screen at once.
     */
    override suspend fun addSong(jamDate: LocalDate, songId: SongId, key: Key): AddSongOutcome =
        scope.async(start = CoroutineStart.UNDISPATCHED) { enqueue(jamDate, songId, key) }.await()

    private suspend fun enqueue(jamDate: LocalDate, songId: SongId, key: Key): AddSongOutcome {
        val write = order.withLock {
            val id = ids.incrementAndGet()
            val song = catalogDao.song(songId.value)
            if (song == null) {
                val reason = WriteOutcome.Rejected(UNKNOWN_SONG)
                publish(SetlistAdd(id, jamDate, songId, songId.value, "", key, SetlistAdd.State.Failed(reason)))
                return AddSongOutcome.NotAdded(reason)
            }
            val entry = SetlistAdd(id, jamDate, songId, song.title, song.artist, key, SetlistAdd.State.Sending)
            publish(entry)
            scope.async(start = CoroutineStart.UNDISPATCHED) { writes.withLock { send(entry) } }
        }
        return write.await()
    }

    override fun observeAdds(): Flow<List<SetlistAdd>> = entries.asStateFlow()

    override fun dismiss(id: Long) {
        entries.update { list -> list.filterNot { it.id == id && it.state is SetlistAdd.State.Failed } }
    }

    private suspend fun send(entry: SetlistAdd): AddSongOutcome {
        val fields = mapOf(
            DATE to JsonPrimitive(entry.jamDate.toString()),
            SONG_ID to JsonPrimitive(entry.songId.value),
            KEY to JsonPrimitive(entry.key.value),
        )
        val outcome = when (val answer = writer.send(ADD_SONG, fields)) {
            is AdminAnswer.Ok -> confirmed(answer.body)
                ?.let { added -> store(entry, added) }
                ?: AddSongOutcome.NotAdded(WriteOutcome.Unavailable)

            is AdminAnswer.Refused -> AddSongOutcome.NotAdded(answer.outcome)
        }
        when (outcome) {
            is AddSongOutcome.Added -> entries.update { list -> list.filterNot { it.id == entry.id } }

            is AddSongOutcome.NotAdded -> entries.update { list ->
                list.map { if (it.id == entry.id) it.copy(state = SetlistAdd.State.Failed(outcome.reason)) else it }
            }
        }
        return outcome
    }

    /**
     * Caches the confirmed song with seven open slots. A cache that refuses the row changes nothing
     * about the outcome: the Sheet has it, and the next refresh brings it.
     */
    private suspend fun store(entry: SetlistAdd, added: Confirmed): AddSongOutcome.Added {
        val date = entry.jamDate.toString()
        val song = JamSongEntity(date, added.position, entry.songId.value, added.title, added.artist, entry.key.value)
        val slots = Lineup.DEFAULT_INSTRUMENTS.mapIndexed { column, instrument ->
            JamSlotEntity(date, added.position, column, instrument.name, musicianName = null)
        }
        try {
            setlistDao.insertSetlistSong(song, slots)
        } catch (ignored: SQLException) {
            // The write succeeded; only the local copy waits for the next refresh.
        }
        return AddSongOutcome.Added(added.position)
    }

    private fun publish(entry: SetlistAdd) {
        entries.update { it + entry }
    }

    private class Confirmed(val position: Int, val title: String, val artist: String)

    private companion object {
        const val ADD_SONG = "addSong"
        const val DATE = "date"
        const val SONG_ID = "songId"
        const val KEY = "key"
        const val UNKNOWN_SONG = "unknown_song"

        /** The answer's `position` (a JSON integer above 0), `title` and `artist`, or null. */
        fun confirmed(body: JsonObject): Confirmed? {
            val position = (body["position"] as? JsonPrimitive)
                ?.takeUnless { it.isString }
                ?.intOrNull
                ?.takeIf { it > 0 }
            val title = (body["title"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val artist = (body["artist"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            return if (position == null || title.isNullOrBlank() || artist.isNullOrBlank()) {
                null
            } else {
                Confirmed(position, title, artist)
            }
        }
    }
}
