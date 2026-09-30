package com.bbbjam.feature.info

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.bbbjam.core.ui.theme.BluesJamTheme
import org.koin.compose.koinInject

/**
 * The Info screen: who runs the jam, the Hideaway program, how to join, the social links and the
 * discreet admin entry. It renders [InfoUiModel] and forwards events; the presenter decides.
 * [contentPadding] goes inside the scroll, so the background runs edge to edge.
 */
@Composable
fun InfoScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    presenter: InfoPresenter = koinInject(),
) {
    InfoContent(model = presenter.present(Unit), modifier = modifier, contentPadding = contentPadding)
}

@Composable
private fun InfoContent(model: InfoUiModel, modifier: Modifier, contentPadding: PaddingValues) {
    val spacing = BluesJamTheme.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BluesJamTheme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = spacing.md, vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Column {
            Text(text = model.title, style = BluesJamTheme.typography.h1)
            Text(text = model.tagline, style = BluesJamTheme.typography.body, color = BluesJamTheme.colors.textMuted)
        }
        model.sections.forEach { section ->
            InfoCard(title = section.title) {
                Text(text = section.body, style = BluesJamTheme.typography.body)
            }
        }
        InfoCard(title = model.linksTitle) {
            model.links.forEach { link ->
                LinkRow(link = link, onClick = { model.events(InfoUiModel.Event.OpenLink(link.link)) })
            }
            model.linkError?.let { error ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = LocalMinimumInteractiveComponentSize.current)
                        .clickable(role = Role.Button) { model.events(InfoUiModel.Event.DismissLinkError) },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(text = error, style = BluesJamTheme.typography.caption, color = BluesJamTheme.colors.error)
                }
            }
        }
        AdminEntry(model = model.adminEntry, onClick = { model.events(InfoUiModel.Event.AdminEntryTapped) })
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    Surface(
        color = BluesJamTheme.colors.surface,
        contentColor = BluesJamTheme.colors.text,
        shape = BluesJamTheme.shapes.md,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(BluesJamTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(BluesJamTheme.spacing.sm),
        ) {
            // songTitle is the only 24sp heading style in DESIGN.md; no new token for section headings.
            Text(text = title, style = BluesJamTheme.typography.songTitle)
            content()
        }
    }
}

@Composable
private fun LinkRow(link: InfoLinkUiModel, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
            .clickable(onClickLabel = link.openLabel, role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = link.label, style = BluesJamTheme.typography.body)
        Text(text = link.destination, style = BluesJamTheme.typography.caption, color = BluesJamTheme.colors.textMuted)
    }
}

@Composable
private fun AdminEntry(model: AdminEntryUiModel, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .heightIn(min = LocalMinimumInteractiveComponentSize.current)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = BluesJamTheme.spacing.md),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = model.label, style = BluesJamTheme.typography.caption, color = BluesJamTheme.colors.textMuted)
        }
        model.notice?.let { notice ->
            Text(
                text = notice,
                style = BluesJamTheme.typography.caption,
                color = BluesJamTheme.colors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview
@Composable
private fun InfoScreenPreview() {
    BluesJamTheme { InfoScreen(presenter = InfoPresenter { true }) }
}
