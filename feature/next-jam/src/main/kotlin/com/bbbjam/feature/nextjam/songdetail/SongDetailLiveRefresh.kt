package com.bbbjam.feature.nextjam.songdetail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.model.SongId
import java.time.LocalDate
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

/** Refreshes immediately on shared editing, then one second after each answer, without overlapping requests. */
@Composable
internal fun rememberSongDetailLiveRefresh(jams: JamsRepository, active: Boolean, jamDate: LocalDate, songId: SongId) {
    LaunchedEffect(jams, active, jamDate, songId) {
        if (!active) return@LaunchedEffect
        while (true) {
            // Failure keeps the confirmed cache; the repository also coalesces other callers' refreshes.
            jams.refresh()
            delay(1.seconds)
        }
    }
}
