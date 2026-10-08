package com.bbbjam.feature.nextjam

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import com.bbbjam.core.ui.strip.fullName
import com.bbbjam.core.ui.theme.BluesJamTheme

/*
 * The admin's controls on Próxima jam (`admin-add-song-to-setlist`). They are only appended: the
 * draft badge under the header, the rest after the rows, and the row actions at the end of an
 * expanded row's panel (`admin-remove-song-from-setlist`), so nothing a musician sees moves. Colours
 * come only from [AdminControlsDefaults]; no amber.
 */

/** The muted `BORRADOR` badge and the note that musicians do not see the list yet. */
@Composable
internal fun AdminDraftBanner(badge: String, note: String?) {
    val spacing = BluesJamTheme.spacing
    val style = AdminControlsDefaults.draft()
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Surface(color = style.badgeFill, contentColor = style.badgeText, shape = BluesJamTheme.shapes.sm) {
            Text(
                text = badge.uppercase(),
                style = BluesJamTheme.typography.caption,
                color = style.badgeText,
                modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            )
        }
        note?.let { Text(text = it, style = BluesJamTheme.typography.caption, color = style.note) }
    }
}

/** An add in flight: a muted card with the title and "Agregando…", read politely when it appears. */
@Composable
internal fun PendingRow(model: PendingRowUiModel) {
    val spacing = BluesJamTheme.spacing
    val style = AdminControlsDefaults.pendingRow()
    Surface(
        color = style.container,
        contentColor = style.content,
        shape = BluesJamTheme.shapes.md,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = LocalMinimumInteractiveComponentSize.current)
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(
                text = model.title,
                style = BluesJamTheme.typography.songTitle,
                color = style.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(text = model.status, style = BluesJamTheme.typography.body, color = style.content)
        }
    }
}

/** A failed add: the title in `error`, the reason, and a 48dp "Cerrar". It stays until closed. */
@Composable
internal fun AddFailureCard(model: AddFailureUiModel) {
    val spacing = BluesJamTheme.spacing
    val style = AdminControlsDefaults.failureCard()
    Surface(
        color = style.container,
        contentColor = style.message,
        shape = BluesJamTheme.shapes.md,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(start = spacing.md, end = spacing.md, top = spacing.sm)) {
            Column(
                modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text(text = model.title, style = BluesJamTheme.typography.body, color = style.title)
                Text(text = model.message, style = BluesJamTheme.typography.body, color = style.message)
            }
            Box(
                modifier = Modifier
                    .heightIn(min = LocalMinimumInteractiveComponentSize.current)
                    .clickable(role = Role.Button) { model.events(AddFailureUiModel.Event.Dismiss) }
                    .padding(end = spacing.sm),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = model.dismissLabel,
                    style = BluesJamTheme.typography.body,
                    color = style.action,
                    textDecoration = TextDecoration.Underline,
                )
            }
        }
    }
}

/** "Agregar tema": a full-width, 48dp, non-amber button after the last row. */
@Composable
internal fun AddSongButton(model: AddSongActionUiModel) {
    val shape = BluesJamTheme.shapes.md
    val style = AdminControlsDefaults.addButton()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
            .clip(shape)
            .background(style.container, shape)
            .clickable(role = Role.Button) { model.events(AddSongActionUiModel.Event.Open) }
            .padding(horizontal = BluesJamTheme.spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = model.label, style = BluesJamTheme.typography.body, color = style.content)
    }
}

/**
 * The admin's part of an expanded row, after "Ver detalle del tema". Each action is a full-width,
 * 48dp, underlined text action, as the detail action: "Cambiar tonalidad" (`admin-set-key`), then
 * the removal, which stays last. A later row action goes before the removal.
 */
@Composable
internal fun AdminRowActions(
    model: SongRowAdminUiModel,
    rowKey: String,
    onMoveAction: (String, MoveActionUiModel) -> Unit,
) {
    RowTextAction(model.setKey.label, AdminControlsDefaults.keyChange().action) {
        model.setKey.events(SetKeyActionUiModel.Event.Open)
    }
    LineupEditor(model.lineup)
    model.move?.let { move -> MoveControl(move, rowKey, onMoveAction) }
    RemovalControl(model.removal)
}

/**
 * "Guardando…" under the row's title line while its key is a pending change (O1): `caption`,
 * `textMuted`, read politely when it appears. Drawn inside the row header, in both states.
 */
@Composable
internal fun SaveStatusLine(status: String) {
    Text(
        text = status,
        style = BluesJamTheme.typography.caption,
        color = AdminControlsDefaults.keyChange().status,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * Removing the song (U1): the action, the inline confirmation (prompt, details, "Quitar" and
 * "Cancelar"), or "Quitando…", read politely. Colours from [AdminControlsDefaults.removal]; no amber.
 */
@Composable
private fun RemovalControl(model: RemovalUiModel) {
    val spacing = BluesJamTheme.spacing
    val style = AdminControlsDefaults.removal()
    when (model) {
        is RemovalUiModel.Idle -> RowTextAction(model.label, style.action) {
            model.events(RemovalUiModel.Event.RequestRemove)
        }

        is RemovalUiModel.Confirming -> Column(modifier = Modifier.padding(top = spacing.xs)) {
            Column(
                modifier = Modifier
                    .padding(horizontal = spacing.md)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text(text = model.prompt, style = BluesJamTheme.typography.body, color = style.prompt)
                model.details.forEach { line ->
                    Text(text = line, style = BluesJamTheme.typography.caption, color = style.details)
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                RowTextAction(model.confirmLabel, style.confirm, Modifier.weight(1f)) {
                    model.events(RemovalUiModel.Event.Confirm)
                }
                RowTextAction(model.cancelLabel, style.cancel, Modifier.weight(1f)) {
                    model.events(RemovalUiModel.Event.Cancel)
                }
            }
        }

        is RemovalUiModel.Removing -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = LocalMinimumInteractiveComponentSize.current)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(start = spacing.md, end = spacing.md, bottom = spacing.xs),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(text = model.status, style = BluesJamTheme.typography.body, color = style.status)
        }
    }
}

/** A 48dp underlined text action in [color], as the row's "Ver detalle del tema". */
@Composable
internal fun RowTextAction(label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val spacing = BluesJamTheme.spacing
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = spacing.md, end = spacing.md, bottom = spacing.xs),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            style = BluesJamTheme.typography.body,
            color = color,
            textDecoration = TextDecoration.Underline,
        )
    }
}

/** The admin's line on Próxima jam with no upcoming jam (J1): how to create one in the Sheet. */
@Composable
internal fun AdminHint(text: String) {
    Text(text = text, style = BluesJamTheme.typography.caption, color = AdminControlsDefaults.draft().note)
}

/** The admin's items after the list: pending adds, failure cards, then "Agregar tema". */
internal fun LazyListScope.adminItems(admin: NextJamAdminUiModel) {
    items(admin.pending, key = { "pending-${it.id}" }) { pending -> PendingRow(pending) }
    items(admin.failures, key = { "failure-${it.id}" }) { failure -> AddFailureCard(failure) }
    admin.addSong?.let { action -> item(key = ADD_SONG_KEY) { AddSongButton(action) } }
}

private const val ADD_SONG_KEY = "add-song"
