package com.bbbjam.feature.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.bbbjam.core.ui.theme.BluesJamTheme

/**
 * The "NUEVA JAM" card: a `surfaceRaised` card with the date, the time and venue, the held-back note, the
 * "Crear la jam" button (only opens the confirmation) and the result line. Never amber.
 */
@Composable
internal fun JamCard(card: JamCardUiModel) {
    val style = JamDefaults.card()
    val typography = BluesJamTheme.typography
    Surface(color = style.fill, shape = BluesJamTheme.shapes.md) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(BluesJamTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(BluesJamTheme.spacing.sm),
        ) {
            Text(
                text = card.heading.uppercase(),
                style = typography.caption,
                color = style.heading,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = card.date, style = typography.songTitle, color = style.date)
            Text(text = card.detail, style = typography.body, color = style.detail)
            card.note?.let { Text(text = it, style = typography.body, color = style.note) }
            card.action?.let { action ->
                JamButton(action)
                action.confirmation?.let { ApplyConfirmationDialog(it) }
            }
            card.result?.let { result ->
                Text(
                    text = result.text,
                    style = typography.body,
                    color = ApplyDefaults.summary(result.tone),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

@Composable
private fun JamButton(model: JamActionUiModel) {
    val shape = BluesJamTheme.shapes.md
    val style = JamDefaults.button(model.enabled)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
            .clip(shape)
            .background(style.fill, shape)
            .clickable(enabled = model.enabled, role = Role.Button) {
                model.events(JamActionUiModel.Event.Request)
            }
            .padding(horizontal = BluesJamTheme.spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = model.label, style = BluesJamTheme.typography.body, color = style.content)
    }
}
