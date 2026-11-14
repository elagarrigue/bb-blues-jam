package com.bbbjam.core.ui.filter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.RosterEntry
import com.bbbjam.core.ui.R
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.core.ui.text.resolve
import com.bbbjam.core.ui.theme.BluesJamTheme

/**
 * The musician filter block (DESIGN.md "Filter chip"): a heading, the pick button or the chosen
 * name's chip with "Quitar filtro", then the count line or the no-results block. Whether the picker
 * is open is presentation state of this composable; every change goes to [MusicianFilterUiModel.events]
 * or an option's handler, and the presenter decides.
 */
@Composable
fun MusicianFilterBar(model: MusicianFilterUiModel, modifier: Modifier = Modifier, showHeading: Boolean = true) {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val spacing = BluesJamTheme.spacing
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        if (showHeading) {
            Text(
                text = model.heading.resolve(),
                style = BluesJamTheme.typography.caption,
                color = BluesJamTheme.colors.textMuted,
                modifier = Modifier.semantics { heading() },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            NameButton(model) { pickerOpen = true }
            if (model.selectedName != null) {
                TextAction(model.clearLabel) { model.events(MusicianFilterUiModel.Event.Clear) }
            }
        }
        model.summary?.let { summary ->
            Text(
                text = summary.resolve(),
                style = BluesJamTheme.typography.body,
                color = BluesJamTheme.colors.text,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        model.noResults?.let { message ->
            Text(
                text = message.resolve(),
                style = BluesJamTheme.typography.body,
                color = BluesJamTheme.colors.textMuted,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            TextAction(model.noResultsClearLabel) { model.events(MusicianFilterUiModel.Event.ClearAll) }
        }
    }
    if (pickerOpen) MusicianPicker(model) { pickerOpen = false }
}

@Composable
private fun NameButton(model: MusicianFilterUiModel, onClick: () -> Unit) {
    val shape = BluesJamTheme.shapes.lg
    val style = MusicianFilterDefaults.chipStyle(selected = model.selectedName != null)
    Box(
        modifier = Modifier
            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
            .clip(shape)
            .background(style.fill, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = BluesJamTheme.spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = model.selectedName ?: model.pickLabel.resolve(),
            style = BluesJamTheme.typography.body,
            color = style.content,
            maxLines = 1,
        )
    }
}

@Composable
private fun TextAction(label: UiText, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label.resolve(),
            style = BluesJamTheme.typography.body,
            color = BluesJamTheme.colors.text,
            textDecoration = TextDecoration.Underline,
        )
    }
}

/** `Todos los músicos` and one radio row per name, each at least 48dp; a choice closes the dialog. */
@Composable
private fun MusicianPicker(model: MusicianFilterUiModel, onClose: () -> Unit) {
    val style = MusicianFilterDefaults.dialogStyle()
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = style.container,
        title = {
            Text(
                text = model.pickerTitle.resolve(),
                style = BluesJamTheme.typography.body,
                color = style.title,
                modifier = Modifier.semantics { heading() },
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                PickerRow(model.allLabel.resolve(), model.selectedName == null, style) {
                    model.events(MusicianFilterUiModel.Event.Clear)
                    onClose()
                }
                model.options.forEach { option ->
                    PickerRow(option.label.resolve(), option.isSelected, style) {
                        option.events(MusicianOptionUiModel.Event.Select)
                        onClose()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text(
                    text = stringResource(R.string.musician_filter_close),
                    style = BluesJamTheme.typography.body,
                    color = style.action,
                )
            }
        },
    )
}

@Composable
private fun PickerRow(
    label: String,
    selected: Boolean,
    style: MusicianFilterDefaults.DialogStyle,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BluesJamTheme.spacing.xs),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = style.radio,
                unselectedColor = BluesJamTheme.colors.textMuted,
            ),
        )
        Text(text = label, style = BluesJamTheme.typography.body, color = style.option)
    }
}

private val previewRoster = listOf(RosterEntry("Ana", "ana", 2), RosterEntry("Juan", "juan", 3))

@Composable
private fun BarPreview(selected: String?, shown: Int, instruments: Set<Instrument> = emptySet()) {
    BluesJamTheme {
        Surface(color = BluesJamTheme.colors.background) {
            MusicianFilterBar(
                model = musicianFilter(
                    roster = previewRoster,
                    selected = selected,
                    counts = MusicianFilterCounts(total = 13, withMusician = 3, shown = shown),
                    instruments = instruments,
                    onChange = {},
                ),
                modifier = Modifier.padding(BluesJamTheme.spacing.md),
            )
        }
    }
}

@Preview
@Composable
private fun NoneChosenPreview() = BarPreview(selected = null, shown = 13)

@Preview
@Composable
private fun JuanChosenPreview() = BarPreview(selected = "Juan", shown = 3)

@Preview
@Composable
private fun JuanAndBassPreview() = BarPreview(selected = "Juan", shown = 1, instruments = setOf(Instrument.BASS))

@Preview
@Composable
private fun AbsentNamePreview() = BarPreview(selected = "Pedro", shown = 0)
