package com.bbbjam.feature.pastjams

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
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.bbbjam.core.ui.status.JamStatusBadge
import com.bbbjam.core.ui.theme.BluesJamTheme

/** The admin's items above the past jams: the `PRÓXIMAS` heading and cards, then the `ANTERIORES` heading. */
internal fun LazyListScope.adminUpcomingItems(admin: JamsAdminUiModel) {
    item(key = UPCOMING_HEADING_KEY) { SectionHeading(admin.upcomingHeading) }
    item(key = NEW_JAM_KEY) { NewJamBlock(admin.newJam) }
    admin.noFuture?.let { line -> item(key = NO_FUTURE_KEY) { NoFutureLine(line) } }
    items(admin.future, key = { "future-${it.date}" }) { jam -> FutureJamCard(jam) }
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
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = BluesJamTheme.typography.caption,
        color = JamsAdminDefaults.heading(),
        modifier = Modifier.fillMaxWidth().semantics { heading() },
    )
}

@Composable
private fun NoFutureLine(text: String) {
    Text(text = text, style = BluesJamTheme.typography.body, color = JamsAdminDefaults.card().note)
}

/**
 * One future jam: one card with the date, the venue and count, the badge and its note. The upcoming
 * jam's card also carries the status actions and `Publicar lista`; a held-back card carries its line
 * and no action. Colours only from [JamsAdminDefaults].
 */
@Composable
private fun FutureJamCard(jam: FutureJamUiModel) {
    val spacing = BluesJamTheme.spacing
    val typography = BluesJamTheme.typography
    val style = JamsAdminDefaults.card()
    Surface(color = style.fill, shape = BluesJamTheme.shapes.md, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(text = jam.dateLabel, style = typography.songTitle, color = style.date)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text(
                    text = jam.venue,
                    style = typography.body,
                    color = style.venue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                jam.songCount?.let { Text(text = it, style = typography.body, color = style.count) }
            }
            Column(
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                JamStatusBadge(jam.badge)
                Text(text = jam.note, style = typography.caption, color = style.note)
                jam.heldBack?.let { Text(text = it, style = typography.caption, color = style.note) }
                jam.controls?.let { JamControls(it) }
            }
        }
    }
}

@Composable
private fun JamControls(controls: JamControlsUiModel) {
    JamStatusActions(controls.status)
    controls.publish?.let { PublishControl(it) }
    controls.status.confirming?.let { UnpublishDialog(it, controls.status.events) }
}

@Composable
private fun PublishControl(model: PublishUiModel) {
    val spacing = BluesJamTheme.spacing
    val note = JamsAdminDefaults.status()
    when (model) {
        is PublishUiModel.Idle -> PrimaryPublishButton(model.label, Modifier.fillMaxWidth()) {
            model.events(PublishUiModel.Event.Request)
        }

        is PublishUiModel.Confirming -> Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(text = model.prompt, style = BluesJamTheme.typography.body, color = BluesJamTheme.colors.text)
            Text(text = model.details, style = BluesJamTheme.typography.caption, color = note)
            Text(text = model.reversibleNote, style = BluesJamTheme.typography.caption, color = note)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                PrimaryPublishButton(model.confirmLabel, Modifier.weight(1f)) {
                    model.events(PublishUiModel.Event.Confirm)
                }
                PublishTextAction(model.cancelLabel, Modifier.weight(1f)) {
                    model.events(PublishUiModel.Event.Cancel)
                }
            }
        }

        is PublishUiModel.Publishing -> Text(
            text = model.status,
            style = BluesJamTheme.typography.body,
            color = note,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = LocalMinimumInteractiveComponentSize.current)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )

        is PublishUiModel.Failed -> PublishFailureCard(model)
    }
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
            Text(text = model.title, style = BluesJamTheme.typography.body, color = style.title)
            Text(text = model.message, style = BluesJamTheme.typography.body, color = style.message)
            Text(text = model.consequence, style = BluesJamTheme.typography.caption, color = style.message)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                PublishTextAction(model.retryLabel, Modifier.weight(1f)) { model.events(PublishUiModel.Event.Retry) }
                PublishTextAction(model.dismissLabel, Modifier.weight(1f)) {
                    model.events(PublishUiModel.Event.Dismiss)
                }
            }
        }
    }
}

@Composable
private fun PrimaryPublishButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val style = JamsAdminDefaults.publish()
    Box(
        modifier = modifier
            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { },
        contentAlignment = Alignment.Center,
    ) {
        Surface(color = style.fill, contentColor = style.text, shape = BluesJamTheme.shapes.md) {
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = LocalMinimumInteractiveComponentSize.current),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = BluesJamTheme.typography.body,
                    color = style.text,
                    modifier = Modifier.padding(horizontal = BluesJamTheme.spacing.md),
                )
            }
        }
    }
}

/** An underlined text action (never amber): the status actions, `Cancelar`, `Reintentar` and `Cerrar`. */
@Composable
internal fun PublishTextAction(
    label: String,
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
            text = label,
            style = BluesJamTheme.typography.body,
            color = color,
            textDecoration = TextDecoration.Underline,
        )
    }
}
