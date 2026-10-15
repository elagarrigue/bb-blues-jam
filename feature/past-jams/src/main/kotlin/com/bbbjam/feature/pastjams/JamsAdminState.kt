package com.bbbjam.feature.pastjams

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.bbbjam.core.data.setlist.JamEdit
import com.bbbjam.core.data.setlist.JamStatusChange
import com.bbbjam.core.data.setlist.SetlistPublish
import java.time.LocalDate

/**
 * What the presenter knows only for an admin (`admin-jams-tab`, moved from Próxima jam): the status
 * changes and publications in flight or failed, the confirming keys and the handlers of
 * [JamStatusControls] and [PublishControls], the "Nueva jam" form ([newJam]), the edits and deletions in
 * flight or failed ([edits]) with the dialogs of [editing], and [today].
 */
internal class JamsAdminState(
    val statusChanges: List<JamStatusChange>,
    val status: JamStatusControls,
    val onDismissStatus: (Long) -> Unit,
    val publishes: List<SetlistPublish>,
    val publish: PublishControls,
    val onDismissPublish: (Long) -> Unit,
    val newJam: NewJamControls,
    val edits: List<JamEdit>,
    val editing: JamEditControls,
    val onDismissEdit: (Long) -> Unit,
    val today: LocalDate,
)

/** The admin state, or null for a musician. The controls are remembered either way, so the order is stable. */
@Composable
internal fun rememberJamsAdminState(
    writes: JamsAdminWrites,
    today: LocalDate,
    isAdmin: Boolean,
    futureDates: Set<LocalDate>?,
): JamsAdminState? {
    val changes by remember { writes.statuses.observeJamStatusChanges() }.collectAsState(initial = emptyList())
    val entries by remember { writes.publishes.observePublishes() }.collectAsState(initial = emptyList())
    val editEntries by remember { writes.edits.observeJamEdits() }.collectAsState(initial = emptyList())
    val statusControls = rememberJamStatusControls(writes.statuses)
    val publishControls = rememberPublishControls(writes.publishes)
    val newJamControls = rememberNewJamControls(writes.creator)
    val editControls = rememberJamEditControls(writes.edits)
    // A dialog for a jam that left the snapshot (edited elsewhere, deleted) closes by itself.
    LaunchedEffect(futureDates) { futureDates?.let(editControls.retain) }
    if (!isAdmin) return null
    return JamsAdminState(
        statusChanges = changes,
        status = statusControls,
        onDismissStatus = writes.statuses::dismissJamStatusChange,
        publishes = entries,
        publish = publishControls,
        onDismissPublish = writes.publishes::dismissPublish,
        newJam = newJamControls,
        edits = editEntries,
        editing = editControls,
        onDismissEdit = writes.edits::dismissJamEdit,
        today = today,
    )
}
