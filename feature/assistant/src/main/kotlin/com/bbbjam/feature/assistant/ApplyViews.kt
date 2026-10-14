package com.bbbjam.feature.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.bbbjam.core.ui.theme.BluesJamTheme

/** Under a proposal: the apply action or the blocked note, the confirmation dialog and the run's summary. */
@Composable
internal fun ApplyContent(apply: ApplyUiModel, summary: ApplySummaryUiModel?) {
    when (apply) {
        ApplyUiModel.None -> Unit

        is ApplyUiModel.Blocked -> Text(
            text = apply.note,
            style = BluesJamTheme.typography.body,
            color = ApplyDefaults.applyNote(),
        )

        is ApplyUiModel.Action -> {
            ApplyButton(apply)
            apply.confirmation?.let { ApplyConfirmationDialog(it) }
        }
    }
    summary?.let { ApplySummary(it) }
}

/** The secondary full-width button: opens the confirmation, writes nothing. */
@Composable
private fun ApplyButton(model: ApplyUiModel.Action) {
    val shape = BluesJamTheme.shapes.md
    val style = ApplyDefaults.applyButton(model.enabled)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
            .clip(shape)
            .background(style.fill, shape)
            .clickable(enabled = model.enabled, role = Role.Button) {
                model.events(ApplyUiModel.Action.Event.Request)
            }
            .padding(horizontal = BluesJamTheme.spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = model.label, style = BluesJamTheme.typography.body, color = style.content)
    }
}

@Composable
private fun ApplySummary(summary: ApplySummaryUiModel) {
    val notice = AssistantDefaults.notice()
    Text(
        text = summary.text,
        style = BluesJamTheme.typography.body,
        color = ApplyDefaults.summary(summary.tone),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    summary.stopLabel?.let { label ->
        TextAction(label, notice.action) { summary.events(ApplySummaryUiModel.Event.Stop) }
    }
}
