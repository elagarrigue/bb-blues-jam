package com.bbbjam.feature.nextjam

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.bbbjam.core.data.setlist.SetlistRepository
import java.time.LocalDate
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

internal data class PublishControls(
    val confirming: String?,
    val onRequest: (LocalDate) -> Unit,
    val onCancel: (LocalDate) -> Unit,
    val onConfirm: (LocalDate) -> Unit,
    val onRetry: (LocalDate, Long) -> Unit,
)

@Composable
internal fun rememberPublishControls(setlist: SetlistRepository): PublishControls {
    var confirming by rememberSaveable { mutableStateOf<String?>(null) }
    var consumed by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    return PublishControls(
        confirming = confirming,
        onRequest = { date ->
            confirming = date.toString()
            consumed = false
        },
        onCancel = { date ->
            if (confirming == date.toString()) {
                confirming = null
                consumed = false
            }
        },
        onConfirm = { date ->
            if (confirming == date.toString() && !consumed) {
                consumed = true
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    setlist.publishSetlist(date)
                    if (confirming == date.toString()) confirming = null
                    consumed = false
                }
            }
        },
        onRetry = { date, id ->
            setlist.dismiss(id)
            scope.launch(start = CoroutineStart.UNDISPATCHED) { setlist.publishSetlist(date) }
        },
    )
}
