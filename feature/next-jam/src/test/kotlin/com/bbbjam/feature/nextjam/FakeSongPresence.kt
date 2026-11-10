package com.bbbjam.feature.nextjam

import com.bbbjam.core.data.presence.PresenceAnswer
import com.bbbjam.core.data.presence.SongPresence
import com.bbbjam.core.model.SongId
import java.time.LocalDate

/** A [SongPresence] that answers [answer] and records every announce and leave. */
class FakeSongPresence(var answer: PresenceAnswer = PresenceAnswer.Alone) : SongPresence {
    val announces = mutableListOf<Pair<LocalDate, SongId>>()
    val leaves = mutableListOf<Pair<LocalDate, SongId>>()

    override suspend fun announce(jamDate: LocalDate, songId: SongId): PresenceAnswer {
        announces += jamDate to songId
        return answer
    }

    override fun leave(jamDate: LocalDate, songId: SongId) {
        leaves += jamDate to songId
    }
}
