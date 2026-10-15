package com.bbbjam.feature.pastjams

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.bbbjam.core.data.setlist.SetlistJamStatusRepository
import com.bbbjam.core.model.JamStatus
import java.time.LocalDate
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * The status change's decisions (`admin-set-jam-status`): leaving PUBLICADA only asks for a
 * confirmation (the saveable [confirming] key, "date|STATUS"); every other request calls
 * `setJamStatus` at once. [inFlight] ignores a second tap until the call returns, so a double tap
 * writes once.
 */
internal data class JamStatusControls(
    val confirming: String?,
    val onRequest: (date: LocalDate, current: JamStatus, target: JamStatus) -> Unit,
    val onCancel: (LocalDate) -> Unit,
    val onConfirm: (LocalDate) -> Unit,
    val onRetry: (date: LocalDate, id: Long, target: JamStatus) -> Unit,
)

/** The confirming key for [date] and the status it would move to. */
internal fun statusConfirmKey(date: LocalDate, target: JamStatus): String = "$date|${target.name}"

/** The target of [key] when it is for [date], else null. */
internal fun statusConfirmTarget(key: String?, date: LocalDate): JamStatus? {
    val parts = key?.split('|').orEmpty()
    val matches = parts.size == 2 && parts[0] == date.toString()
    return if (matches) JamStatus.entries.firstOrNull { it.name == parts[1] } else null
}

@Composable
internal fun rememberJamStatusControls(statuses: SetlistJamStatusRepository): JamStatusControls {
    var confirming by rememberSaveable { mutableStateOf<String?>(null) }
    var inFlight by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val send: (LocalDate, JamStatus) -> Unit = { date, target ->
        inFlight = true
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                statuses.setJamStatus(date, target)
            } finally {
                inFlight = false
            }
        }
    }
    return JamStatusControls(
        confirming = confirming,
        onRequest = { date, current, target ->
            if (!inFlight) {
                if (current == JamStatus.PUBLISHED) {
                    confirming = statusConfirmKey(date, target)
                } else {
                    send(date, target)
                }
            }
        },
        onCancel = { date -> if (statusConfirmTarget(confirming, date) != null) confirming = null },
        onConfirm = { date ->
            val target = statusConfirmTarget(confirming, date)
            if (target != null && !inFlight) {
                confirming = null
                send(date, target)
            }
        },
        onRetry = { date, id, target ->
            if (!inFlight) {
                statuses.dismissJamStatusChange(id)
                send(date, target)
            }
        },
    )
}
