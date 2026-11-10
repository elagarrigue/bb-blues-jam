package com.bbbjam.feature.nextjam.songdetail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.bbbjam.core.data.presence.PresenceAnswer
import com.bbbjam.core.data.presence.SongPresence
import com.bbbjam.core.model.SongId
import java.time.LocalDate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

/** The announce cadence of an open admin detail (`admin-presence-notice`, D-32). */
internal object PresenceHeartbeat {
    /** The server forgets a holder after 120 s, so three heartbeats may be lost before the entry expires. */
    val INTERVAL: Duration = 30.seconds
}

/**
 * Announces, hands the answer to [onAnswer], then waits [interval] counted from the answer, so one
 * device never overlaps itself. Loops until its coroutine is cancelled.
 */
internal suspend fun runPresenceHeartbeat(
    interval: Duration,
    announce: suspend () -> PresenceAnswer,
    onAnswer: (PresenceAnswer) -> Unit,
) {
    while (true) {
        onAnswer(announce())
        delay(interval)
    }
}

/**
 * True while another admin is known to be on the song. While [active] it heartbeats; when [active]
 * turns false or the composition leaves, it releases once and clears the notice. An
 * [PresenceAnswer.Unknown] answer keeps what was known (failures are silent).
 */
@Composable
internal fun rememberOthersHere(presence: SongPresence, active: Boolean, jamDate: LocalDate, songId: SongId): Boolean {
    var othersHere by remember(jamDate, songId) { mutableStateOf(false) }
    LaunchedEffect(active, jamDate, songId) {
        if (!active) return@LaunchedEffect
        try {
            runPresenceHeartbeat(
                interval = PresenceHeartbeat.INTERVAL,
                announce = { presence.announce(jamDate, songId) },
                onAnswer = { answer ->
                    when (answer) {
                        PresenceAnswer.OthersHere -> othersHere = true
                        PresenceAnswer.Alone -> othersHere = false
                        PresenceAnswer.Unknown -> Unit
                    }
                },
            )
        } finally {
            presence.leave(jamDate, songId)
            othersHere = false
        }
    }
    return othersHere && active
}
