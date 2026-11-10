package com.bbbjam.core.data.presence

import com.bbbjam.core.data.DataScope
import com.bbbjam.core.data.admin.AdminAnswer
import com.bbbjam.core.data.admin.AdminWriter
import com.bbbjam.core.model.SongId
import java.time.LocalDate
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * [SongPresence] over the admin POST path. It is not on the mutation queue, so presence never waits
 * behind a write. Every failure reads as [PresenceAnswer.Unknown]; nothing is logged.
 */
internal class DefaultSongPresence(
    private val writer: AdminWriter,
    private val holders: PresenceHolderStore,
    private val scope: DataScope,
) : SongPresence {

    override suspend fun announce(jamDate: LocalDate, songId: SongId): PresenceAnswer {
        val holder = holders.holderId() ?: return PresenceAnswer.Unknown
        return when (val answer = writer.send(ACTION, fields(jamDate, songId, holder))) {
            is AdminAnswer.Refused -> PresenceAnswer.Unknown

            is AdminAnswer.Ok -> {
                val others = (answer.body["others"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
                when {
                    others == null || others < 0 -> PresenceAnswer.Unknown
                    others > 0 -> PresenceAnswer.OthersHere
                    else -> PresenceAnswer.Alone
                }
            }
        }
    }

    override fun leave(jamDate: LocalDate, songId: SongId) {
        scope.launch {
            val holder = holders.holderId() ?: return@launch
            writer.send(ACTION, fields(jamDate, songId, holder) + (LEAVING to JsonPrimitive(true)))
        }
    }

    private fun fields(jamDate: LocalDate, songId: SongId, holder: String): Map<String, JsonElement> = mapOf(
        "date" to JsonPrimitive(jamDate.toString()),
        "songId" to JsonPrimitive(songId.value),
        "holder" to JsonPrimitive(holder),
    )

    private companion object {
        const val ACTION = "announcePresence"
        const val LEAVING = "leaving"
    }
}
