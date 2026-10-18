package com.bbbjam.feature.pastjams.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.core.ui.text.resolve
import com.bbbjam.core.ui.theme.BluesJamTheme
import com.bbbjam.feature.pastjams.create.NewJamBlock
import com.bbbjam.feature.pastjams.edit.DeleteJamDialog
import com.bbbjam.feature.pastjams.edit.EditJamDialog
import com.bbbjam.feature.pastjams.edit.JamManageActions
import com.bbbjam.feature.pastjams.edit.JamManageIcons
import com.bbbjam.feature.pastjams.status.JamStatusActions
import com.bbbjam.feature.pastjams.status.JamStatusDefaults
import com.bbbjam.feature.pastjams.status.PublishUiModel
import com.bbbjam.feature.pastjams.status.UnpublishDialog

/** The admin's items above the past jams: the `PRÓXIMAS` heading and cards, then the `ANTERIORES` heading. */
internal fun LazyListScope.adminUpcomingItems(admin: JamsAdminUiModel) {
    item(key = UPCOMING_HEADING_KEY) { SectionHeading(admin.upcomingHeading) }
    item(key = NEW_JAM_KEY) { NewJamBlock(admin.newJam) }
    admin.noFuture?.let { line -> item(key = NO_FUTURE_KEY) { NoFutureLine(line) } }
    items(admin.future, key = { "future-${it.date}" }) { jam -> FutureJamCard(jam, admin) }
}

/** The `ANTERIORES` heading, drawn for the admin only (a musician's list has no headings). */
internal fun LazyListScope.adminPastHeading(admin: JamsAdminUiModel) {
    item(key = PAST_HEADING_KEY) { SectionHeading(admin.pastHeading) }
}

private const val UPCOMING_HEADING_KEY = "upcoming-heading"
private const val NO_FUTURE_KEY = "no-future"
private const val NEW_JAM_KEY = "new-jam"
private const val PAST_HEADING_KEY = "past-heading"

@Composable
private fun SectionHeading(text: UiText) {
    Text(
        text = text.resolve(),
        style = BluesJamTheme.typography.caption,
        color = JamsAdminDefaults.heading(),
        modifier = Modifier.fillMaxWidth().semantics { heading() },
    )
}

@Composable
private fun NoFutureLine(text: UiText) {
    Text(text = text.resolve(), style = BluesJamTheme.typography.body, color = JamsAdminDefaults.card().note)
}

/**
 * One future jam: one card with the date and the edit and delete icons, the venue and count, the status chips
 * and their note; the upcoming card also opens the publish confirmation from the Publicada chip, a held-back
 * card carries its line. The edit and delete dialogs of [admin] are drawn by the card they are for.
 * Colours only from [JamsAdminDefaults].
 */
@Composable
private fun FutureJamCard(jam: FutureJamUiModel, admin: JamsAdminUiModel) {
    val spacing = BluesJamTheme.spacing
    val typography = BluesJamTheme.typography
    val style = JamsAdminDefaults.card()
    Surface(color = style.fill, shape = BluesJamTheme.shapes.md, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = jam.dateLabel.resolve(),
                    style = typography.songTitle,
                    color = style.date,
                    modifier = Modifier.weight(1f),
                )
                JamManageIcons(jam.manage)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text(
                    text = jam.venue,
                    style = typography.body,
                    color = style.venue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                jam.songCount?.let { Text(text = it.resolve(), style = typography.body, color = style.count) }
            }
            Column(
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text(text = jam.note.resolve(), style = typography.caption, color = style.note)
                jam.heldBack?.let { Text(text = it.resolve(), style = typography.caption, color = style.note) }
                jam.musicianNote?.let { Text(text = it.resolve(), style = typography.caption, color = style.note) }
                JamControls(jam.controls)
                JamManageActions(jam.manage)
            }
        }
    }
    admin.editForm?.takeIf { it.jamDate == jam.date }?.let { EditJamDialog(it) }
    admin.deleteConfirm?.takeIf { it.jamDate == jam.date }?.let { DeleteJamDialog(it) }
}

@Composable
private fun JamControls(controls: JamControlsUiModel) {
    JamStatusActions(controls.status)
    controls.publish?.let { model ->
        when (model) {
            is PublishUiModel.Confirming -> PublishConfirmDialog(model)
            is PublishUiModel.Failed -> PublishFailureCard(model)
        }
    }
    controls.status.confirming?.let { UnpublishDialog(it, controls.status.events) }
}

@Composable
private fun PublishConfirmDialog(model: PublishUiModel.Confirming) {
    val style = JamStatusDefaults.publishDialog()
    AlertDialog(
        onDismissRequest = { model.events(PublishUiModel.Event.Cancel) },
        containerColor = style.container,
        title = { Text(text = model.prompt.resolve(), style = BluesJamTheme.typography.body, color = style.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(BluesJamTheme.spacing.xs)) {
                Text(text = model.details.resolve(), style = BluesJamTheme.typography.caption, color = style.body)
                Text(
                    text = model.reversibleNote.resolve(),
                    style = BluesJamTheme.typography.caption,
                    color = style.body,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { model.events(PublishUiModel.Event.Confirm) }) {
                Text(text = model.confirmLabel.resolve(), color = style.confirm)
            }
        },
        dismissButton = {
            TextButton(onClick = { model.events(PublishUiModel.Event.Cancel) }) {
                Text(text = model.cancelLabel.resolve(), color = style.cancel)
            }
        },
    )
}

@Composable
private fun PublishFailureCard(model: PublishUiModel.Failed) {
    val spacing = BluesJamTheme.spacing
    val style = JamsAdminDefaults.failure()
    Surface(
        color = style.container,
        shape = BluesJamTheme.shapes.md,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Assertive
        },
    ) {
        Column(
            modifier = Modifier.padding(start = spacing.md, end = spacing.md, top = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(text = model.title.resolve(), style = BluesJamTheme.typography.body, color = style.title)
            Text(text = model.message.resolve(), style = BluesJamTheme.typography.body, color = style.message)
            Text(text = model.consequence.resolve(), style = BluesJamTheme.typography.caption, color = style.message)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                PublishTextAction(model.retryLabel, Modifier.weight(1f)) { model.events(PublishUiModel.Event.Retry) }
                PublishTextAction(model.dismissLabel, Modifier.weight(1f)) {
                    model.events(PublishUiModel.Event.Dismiss)
                }
            }
        }
    }
}

/** An underlined text action (never amber): `Cancelar`, `Reintentar` and `Cerrar`. */
@Composable
internal fun PublishTextAction(
    label: UiText,
    modifier: Modifier = Modifier,
    color: Color = BluesJamTheme.colors.text,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.resolve(),
            style = BluesJamTheme.typography.body,
            color = color,
            textDecoration = TextDecoration.Underline,
        )
    }
}
