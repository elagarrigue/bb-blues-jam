package com.bbbjam.feature.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import com.bbbjam.core.ui.action.IconRowAction
import com.bbbjam.core.ui.action.IconRowActionIcon
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.core.ui.text.resolve
import com.bbbjam.core.ui.theme.BluesJamTheme

/** `Agregar tema`: a full-width neutral button, 48dp, disabled while a catalog write runs. */
@Composable
internal fun AddSongButton(model: ActionUiModel) {
    val style = CatalogDefaults.addButton()
    val shape = BluesJamTheme.shapes.md
    Surface(
        color = style.fill,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalMinimumInteractiveComponentSize.current),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = LocalMinimumInteractiveComponentSize.current)
                .clickable(enabled = model.enabled, role = Role.Button) { model.events(ActionUiModel.Event.Click) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = model.label.resolve(),
                style = BluesJamTheme.typography.body,
                color = if (model.enabled) style.content else style.disabledContent,
            )
        }
    }
}

/** The sending or result line (polite), or the failure card (assertive). */
@Composable
internal fun CatalogNotice(model: CatalogNoticeUiModel) {
    when (model) {
        is CatalogNoticeUiModel.Progress -> NoticeLine(model.message)
        is CatalogNoticeUiModel.Done -> NoticeLine(model.message)
        is CatalogNoticeUiModel.Failure -> FailureCard(model)
    }
}

@Composable
private fun NoticeLine(message: UiText) {
    Text(
        text = message.resolve(),
        style = BluesJamTheme.typography.body,
        color = CatalogDefaults.caption(),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun FailureCard(model: CatalogNoticeUiModel.Failure) {
    val spacing = BluesJamTheme.spacing
    val style = CatalogDefaults.failure()
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
                model.retryLabel?.let { label ->
                    TextAction(label, style.action) { model.events(CatalogNoticeUiModel.Failure.Event.Retry) }
                }
                TextAction(model.dismissLabel, style.action) {
                    model.events(CatalogNoticeUiModel.Failure.Event.Dismiss)
                }
            }
        }
    }
}

/** An underlined text action (never amber): `Reintentar` and `Cerrar`. */
@Composable
private fun TextAction(label: UiText, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
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

@Composable
internal fun SearchField(model: SearchFieldUiModel) {
    OutlinedTextField(
        value = model.query,
        onValueChange = { model.events(SearchFieldUiModel.Event.Changed(it)) },
        modifier = Modifier.fillMaxWidth(),
        textStyle = BluesJamTheme.typography.body,
        label = { Text(text = model.label.resolve()) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = BluesJamTheme.shapes.md,
        colors = fieldColors(CatalogDefaults.search()),
    )
}

@Composable
internal fun fieldColors(style: CatalogDefaults.FieldStyle) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = style.text,
    unfocusedTextColor = style.text,
    errorTextColor = style.text,
    focusedContainerColor = style.container,
    unfocusedContainerColor = style.container,
    errorContainerColor = style.container,
    cursorColor = style.cursor,
    errorCursorColor = style.error,
    focusedBorderColor = style.focusedBorder,
    unfocusedBorderColor = style.unfocusedBorder,
    errorBorderColor = style.error,
    focusedLabelColor = style.label,
    unfocusedLabelColor = style.label,
    errorLabelColor = style.error,
    focusedSupportingTextColor = style.note,
    unfocusedSupportingTextColor = style.note,
    errorSupportingTextColor = style.error,
)

/**
 * One catalog song: title (two lines), artist and the default key (`key`, read as "Tonalidad A") in one merged
 * node, then the edit and delete icons, 48dp each, disabled while a catalog write runs.
 */
@Composable
internal fun SongRow(row: SongRowUiModel) {
    val spacing = BluesJamTheme.spacing
    val style = CatalogDefaults.row()
    val keyDescription = row.keyDescription.resolve()
    Surface(color = style.container, shape = BluesJamTheme.shapes.md, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = spacing.md, top = spacing.xs, bottom = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    Text(
                        text = row.title,
                        style = BluesJamTheme.typography.songTitle,
                        color = style.title,
                        maxLines = TITLE_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = row.key,
                        style = BluesJamTheme.typography.songTitle,
                        color = style.key,
                        modifier = Modifier.semantics { contentDescription = keyDescription },
                    )
                }
                if (row.artist.isNotBlank()) {
                    Text(text = row.artist, style = BluesJamTheme.typography.body, color = style.artist)
                }
            }
            IconRowAction(
                icon = IconRowActionIcon.Edit,
                contentDescription = row.editDescription.resolve(),
                tint = style.edit,
                mutedTint = style.muted,
                enabled = row.actionsEnabled,
            ) { row.events(SongRowUiModel.Event.Edit) }
            IconRowAction(
                icon = IconRowActionIcon.Delete,
                contentDescription = row.deleteDescription.resolve(),
                tint = style.delete,
                mutedTint = style.muted,
                enabled = row.actionsEnabled,
            ) { row.events(SongRowUiModel.Event.Delete) }
        }
    }
}

private const val TITLE_MAX_LINES = 2
