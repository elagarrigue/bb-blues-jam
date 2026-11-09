package com.bbbjam.core.data.setlist.played

import android.database.SQLException
import com.bbbjam.core.data.DataFailure
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
import com.bbbjam.core.model.SongId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * The played mark mutation (`song-played-state`): its entries, its request and its cache mirror, queued on the
 * shared [SetlistMutationQueue]. Room changes only from the server's confirmation, never optimistically.
 */
internal class SetlistPlayedChanges(
    private val writer: AdminWriter,
    private val setlistDao: SetlistDao,
    private val catalogDao: CatalogDao,
    private val scope: DataScope,
    private val queue: SetlistMutationQueue,
) : SetlistPlayedRepository {
    private val entries = PendingEntries<PlayedChange>({ it.id }) { it.state !is PlayedChange.State.Sending }

    /** Runs in [scope] from the first instruction, so the caller's cancellation never cancels it. */
    override suspend fun setSongPlayed(jamDate: LocalDate, songId: SongId, played: Boolean): SetSongPlayedOutcome =
        queue.enqueue(scope) { id ->
            val title = displayedTitle(catalogDao, setlistDao, jamDate, songId)
            val entry = PlayedChange(id, jamDate, songId, title, played, PlayedChange.State.Sending)
            entries.add(entry)
            QueuedWrite.Send { send(entry) }
        }

    override fun observePlayedChanges(): Flow<List<PlayedChange>> = entries.observe()

    override fun dismissPlayedChange(id: Long) = entries.dismiss(id)

    private suspend fun send(entry: PlayedChange): SetSongPlayedOutcome {
        val answer = writer.send(
            SET_SONG_PLAYED,
            mapOf(
                SetlistRequestFields.DATE to JsonPrimitive(entry.jamDate.toString()),
                SetlistRequestFields.SONG_ID to JsonPrimitive(entry.songId.value),
                PLAYED to JsonPrimitive(entry.played),
            ),
        )
        val outcome = when (answer) {
            is AdminAnswer.Refused ->
                if (answer.failure == DataFailure.Service(ServiceCodes.UNKNOWN_ACTION)) {
                    SetSongPlayedOutcome.ScriptOutdated
                } else {
                    SetSongPlayedOutcome.NotSet(answer.outcome)
                }

            is AdminAnswer.Ok -> confirmed(answer, entry)
        }
        if (outcome is SetSongPlayedOutcome.PlayedSet) {
            mirror(entry)
            entries.remove(entry.id)
        } else {
            val state = when (outcome) {
                is SetSongPlayedOutcome.NotSet -> PlayedChange.State.Failed(outcome.reason)
                else -> PlayedChange.State.Outdated
            }
            entries.fail(entry.id) { it.copy(state = state) }
        }
        return outcome
    }

    /** The answer must echo the value that was asked for; anything else is not a confirmation. */
    private fun confirmed(answer: AdminAnswer.Ok, entry: PlayedChange): SetSongPlayedOutcome {
        val echoed = (answer.body[PLAYED] as? JsonPrimitive)?.booleanOrNull
        return if (echoed == entry.played) {
            SetSongPlayedOutcome.PlayedSet(entry.played)
        } else {
            SetSongPlayedOutcome.NotSet(WriteOutcome.Unavailable)
        }
    }

    private suspend fun mirror(entry: PlayedChange) {
        try {
            setlistDao.updatePlayed(entry.jamDate.toString(), entry.songId.value, entry.played)
        } catch (ignored: SQLException) {
            // The Sheet changed; only the local copy waits for the next refresh.
        }
    }

    private companion object {
        const val SET_SONG_PLAYED = "setSongPlayed"
        const val PLAYED = "played"
    }
}
