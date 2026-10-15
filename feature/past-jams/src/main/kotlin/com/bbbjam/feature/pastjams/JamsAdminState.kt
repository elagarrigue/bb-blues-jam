package com.bbbjam.feature.pastjams

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.bbbjam.core.data.setlist.JamCreator
import com.bbbjam.core.data.setlist.JamStatusChange
import com.bbbjam.core.data.setlist.SetlistJamStatusRepository
import com.bbbjam.core.data.setlist.SetlistPublish
import com.bbbjam.core.data.setlist.SetlistPublishRepository
import java.time.LocalDate

/**
 * What the presenter knows only for an admin (`admin-jams-tab`, moved from Próxima jam): the status
 * changes and publications in flight or failed, the confirming keys and the handlers of
 * [JamStatusControls] and [PublishControls], and the "Nueva jam" form ([newJam]) with [today].
 */
internal class JamsAdminState(
    val statusChanges: List<JamStatusChange>,
    val status: JamStatusControls,
    val onDismissStatus: (Long) -> Unit,
    val publishes: List<SetlistPublish>,
    val publish: PublishControls,
    val onDismissPublish: (Long) -> Unit,
    val newJam: NewJamControls,
    val today: LocalDate,
)

/** The admin state, or null for a musician. The controls are remembered either way, so the order is stable. */
@Composable
internal fun rememberJamsAdminState(
    statuses: SetlistJamStatusRepository,
    publishes: SetlistPublishRepository,
    creator: JamCreator,
    today: LocalDate,
    isAdmin: Boolean,
): JamsAdminState? {
    val changes by remember { statuses.observeJamStatusChanges() }.collectAsState(initial = emptyList())
    val entries by remember { publishes.observePublishes() }.collectAsState(initial = emptyList())
    val statusControls = rememberJamStatusControls(statuses)
    val publishControls = rememberPublishControls(publishes)
    val newJamControls = rememberNewJamControls(creator)
    if (!isAdmin) return null
    return JamsAdminState(
        statusChanges = changes,
        status = statusControls,
        onDismissStatus = statuses::dismissJamStatusChange,
        publishes = entries,
        publish = publishControls,
        onDismissPublish = publishes::dismissPublish,
        newJam = newJamControls,
        today = today,
    )
}
