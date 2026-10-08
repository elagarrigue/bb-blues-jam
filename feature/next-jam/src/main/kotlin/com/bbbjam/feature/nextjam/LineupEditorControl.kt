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

/** Adjusting a lineup keeps the counts with their instrument and exposes described +/- controls. */
@Composable
internal fun LineupEditor(model: LineupEditorUiModel) {
    when (model) {
        is LineupEditorUiModel.Idle -> RowTextAction(model.label, AdminControlsDefaults.lineupEditor().action) {
            model.events(LineupEditorUiModel.Event.Open)
        }

        is LineupEditorUiModel.Editing -> {
            val spacing = BluesJamTheme.spacing
            val style = AdminControlsDefaults.lineupEditor()
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    text = model.heading,
                    style = BluesJamTheme.typography.caption,
                    color = style.heading,
                    modifier = Modifier
                        .padding(start = spacing.md, top = spacing.xs)
                        .semantics { heading() },
                )
                model.lines.forEach { line -> LineupEditorLine(line) }
                RowTextAction(model.doneLabel, style.action) {
                    model.events(LineupEditorUiModel.Event.Done)
                }
            }
        }
    }
}

@Composable
private fun LineupEditorLine(model: LineupEditorLineUiModel) {
    val spacing = BluesJamTheme.spacing
    val style = AdminControlsDefaults.lineupEditor()
    Surface(
        color = style.line,
        contentColor = style.instrument,
        shape = BluesJamTheme.shapes.md,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.instrument.fullName(),
                        style = BluesJamTheme.typography.body,
                        color = style.instrument,
                    )
                    Text(text = model.countLabel, style = BluesJamTheme.typography.caption, color = style.count)
                }
                EditorCountButton("−", model.removeDescription, model.canRemove, style) {
                    model.events(LineupEditorLineUiModel.Event.Remove)
                }
                EditorCountButton("+", model.addDescription, model.canAdd, style) {
                    model.events(LineupEditorLineUiModel.Event.Add)
                }
            }
            model.blockedNote?.let { note ->
                Text(
                    text = note,
                    style = BluesJamTheme.typography.caption,
                    color = style.blockedNote,
                    modifier = Modifier.padding(top = spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun EditorCountButton(
    glyph: String,
    description: String,
    enabled: Boolean,
    style: AdminControlsDefaults.LineupEditorStyle,
    onClick: () -> Unit,
) {
    val size = LocalMinimumInteractiveComponentSize.current
    Box(
        modifier = Modifier
            .size(size)
            .clip(BluesJamTheme.shapes.md)
            .background(style.buttonFill, BluesJamTheme.shapes.md)
            .then(
                if (enabled) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier.semantics { disabled() }
                },
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = BluesJamTheme.typography.songTitle,
            color = if (enabled) style.buttonGlyph else style.disabledGlyph,
        )
    }
}
