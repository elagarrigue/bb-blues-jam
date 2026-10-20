package com.bbbjam.feature.assistant.apply

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.bbbjam.core.ui.jamform.JamFormDialog
import com.bbbjam.core.ui.state.LoadingDots
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.core.ui.text.resolve
import com.bbbjam.core.ui.theme.BluesJamTheme
import com.bbbjam.feature.assistant.AssistantDefaults
import com.bbbjam.feature.assistant.chat.TextAction

/**
 * Under a proposal: the target buttons and their notes, the confirmation dialog, the run's summary, the
 * removed songs' lines and the order line.
 */
@Composable
internal fun ApplyContent(
    apply: ApplyUiModel,
    summary: ApplySummaryUiModel?,
    removals: List<ApplyStatusLineUiModel> = emptyList(),
    order: UiText? = null,
) {
    when (apply) {
        ApplyUiModel.None -> Unit

        is ApplyUiModel.Targets -> {
            Column(verticalArrangement = Arrangement.spacedBy(BluesJamTheme.spacing.xs)) {
                apply.actions.forEach { TargetButton(it) }
            }
            apply.notes.forEach { note ->
                Text(text = note.resolve(), style = BluesJamTheme.typography.body, color = ApplyDefaults.applyNote())
            }
            apply.confirmation?.let { ApplyConfirmationDialog(it) }
            apply.createForm?.let { JamFormDialog(it) }
        }
    }
    summary?.let { ApplySummary(it) }
    removals.forEach { RemovalLine(it) }
    order?.let {
        Text(text = it.resolve(), style = BluesJamTheme.typography.caption, color = ApplyDefaults.applyNote())
    }
}

/** The secondary full-width button: opens the confirmation, writes nothing. */
@Composable
private fun TargetButton(model: TargetActionUiModel) {
    val shape = BluesJamTheme.shapes.md
    val style = ApplyDefaults.applyButton(model.enabled)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
            .clip(shape)
            .background(style.fill, shape)
            .clickable(enabled = model.enabled, role = Role.Button) {
                model.events(TargetActionUiModel.Event.Request)
            }
            .padding(horizontal = BluesJamTheme.spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = model.label.resolve(), style = BluesJamTheme.typography.body, color = style.content)
    }
}

@Composable
private fun RemovalLine(line: ApplyStatusLineUiModel) {
    val typography = BluesJamTheme.typography
    Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(text = line.title, style = typography.body, color = ApplyDefaults.applyNote())
        Text(text = line.text.resolve(), style = typography.caption, color = ApplyDefaults.status(line.kind))
    }
}

@Composable
private fun ApplySummary(summary: ApplySummaryUiModel) {
    val notice = AssistantDefaults.notice()
    val text = summary.text.resolve()
    val color = ApplyDefaults.summary(summary.tone)
    val stopLabel = summary.stopLabel
    if (stopLabel == null) {
        Text(
            text = text,
            style = BluesJamTheme.typography.body,
            color = color,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    } else {
        // While the run goes, the dots carry the summary as their one polite node; the text is hidden from a
        // screen reader so it is not read twice.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BluesJamTheme.spacing.sm),
        ) {
            LoadingDots(description = text)
            Text(
                text = text,
                style = BluesJamTheme.typography.body,
                color = color,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        TextAction(stopLabel.resolve(), notice.action) { summary.events(ApplySummaryUiModel.Event.Stop) }
    }
}
