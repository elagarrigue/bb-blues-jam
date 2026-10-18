package com.bbbjam.core.data.setlist.add

import android.database.SQLException
import com.bbbjam.core.data.DataScope
import com.bbbjam.core.data.admin.AdminAnswer
import com.bbbjam.core.data.admin.AdminWriter
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.cache.CatalogDao
import com.bbbjam.core.data.cache.JamSlotEntity
import com.bbbjam.core.data.cache.JamSongEntity
import com.bbbjam.core.data.cache.SetlistDao
import com.bbbjam.core.data.setlist.engine.PendingEntries
import com.bbbjam.core.data.setlist.engine.QueuedWrite
import com.bbbjam.core.data.setlist.engine.SetlistMutationQueue
import com.bbbjam.core.data.setlist.engine.SetlistRequestFields
import com.bbbjam.core.data.setlist.engine.enqueue
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.SongId
import java.time.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Owns the add-song entry, request and confirmed cache insert; ordering is shared with other writes. */
internal class SetlistSongAdds(
    private val writer: AdminWriter,
    private val setlistDao: SetlistDao,
    private val catalogDao: CatalogDao,
    private val scope: DataScope,
    private val queue: SetlistMutationQueue,
) : SetlistSongAddRepository {
    private val entries = PendingEntries<SetlistAdd>({ it.id }) { it.state is SetlistAdd.State.Failed }

    /**
     * Runs in the data scope from the first instruction (undispatched), so a caller cancelled at any
     * point, even before the song is resolved, never cancels an add it started: a presenter may launch
     * it and close its screen at once.
     */
    override suspend fun addSong(jamDate: LocalDate, songId: SongId, key: Key): AddSongOutcome =
        queue.enqueue(scope) { id ->
            val song = catalogDao.song(songId.value)
            if (song == null) {
                val reason = WriteOutcome.Rejected(UNKNOWN_SONG)
                entries.add(SetlistAdd(id, jamDate, songId, songId.value, "", key, SetlistAdd.State.Failed(reason)))
                QueuedWrite.Immediate(AddSongOutcome.NotAdded(reason))
            } else {
                val entry = SetlistAdd(id, jamDate, songId, song.title, song.artist, key, SetlistAdd.State.Sending)
                entries.add(entry)
                QueuedWrite.Send { send(entry) }
            }
        }

    override fun observeAdds() = entries.observe()

    override fun dismissAdd(id: Long) = entries.dismiss(id)

    private suspend fun send(entry: SetlistAdd): AddSongOutcome {
        val fields = mapOf(
            SetlistRequestFields.DATE to JsonPrimitive(entry.jamDate.toString()),
            SetlistRequestFields.SONG_ID to JsonPrimitive(entry.songId.value),
            SetlistRequestFields.KEY to JsonPrimitive(entry.key.value),
        )
        val outcome = when (val answer = writer.send(ADD_SONG, fields)) {
            is AdminAnswer.Ok -> confirmed(answer.body)
                ?.let { added -> storeConfirmedSong(setlistDao, entry, added) }
                ?: AddSongOutcome.NotAdded(WriteOutcome.Unavailable)

            is AdminAnswer.Refused -> AddSongOutcome.NotAdded(answer.outcome)
        }
        when (outcome) {
            is AddSongOutcome.Added -> entries.remove(entry.id)

            is AddSongOutcome.NotAdded -> entries.fail(entry.id) {
                it.copy(state = SetlistAdd.State.Failed(outcome.reason))
            }
        }
        return outcome
    }

    private companion object {
        const val ADD_SONG = "addSong"
        const val UNKNOWN_SONG = "unknown_song"
    }
}

private class Confirmed(val position: Int, val title: String, val artist: String)

/** The answer's `position` (a JSON integer above 0), `title` and `artist`, or null. */
private fun confirmed(body: JsonObject): Confirmed? {
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

/** The Sheet is authoritative; a refused local insert waits for the next refresh. */
private suspend fun storeConfirmedSong(
    setlistDao: SetlistDao,
    entry: SetlistAdd,
    added: Confirmed,
): AddSongOutcome.Added {
    val date = entry.jamDate.toString()
    val song = JamSongEntity(date, added.position, entry.songId.value, added.title, added.artist, entry.key.value)
    // A new song starts with the seven default slots, each in its column of the eighteen-column layout.
    val slots = Lineup.DEFAULT_INSTRUMENTS.zip(Lineup.DEFAULT_COLUMNS) { instrument, column ->
        JamSlotEntity(date, added.position, column, instrument.name, musicianName = null)
    }
    try {
        setlistDao.insertSetlistSong(song, slots)
    } catch (ignored: SQLException) {
        // The server write succeeded; only the local copy waits for the next refresh.
    }
    return AddSongOutcome.Added(added.position)
}
