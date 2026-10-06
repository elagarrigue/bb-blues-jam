package com.bbbjam.feature.nextjam

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.nav.BackButton
import com.bbbjam.core.ui.nav.backUiModel
import com.bbbjam.core.ui.state.EmptyStateBlock
import com.bbbjam.core.ui.theme.BluesJamTheme
import java.time.LocalDate
import org.koin.compose.koinInject

/**
 * The key picker (`admin-set-key`, V1): full screen over the tabs, a back button, the title, the
 * song's title, `TONALIDAD ACTUAL` over the current key (amber `key` role), then `MAYORES` and
 * `MENORES`, each a 4-column grid of 3 rows. Selectable cells are never amber; the current key's
 * cell is outlined, amber and marked `actual`, and does nothing. Tapping a key changes it and calls
 * [onDone] at once (`:app` closes the screen). It scrolls, so large font scales still reach every
 * key. It draws [SetKeyUiModel] and forwards events; the presenter decides.
 */
@Composable
fun SetKeyScreen(
    jamDate: LocalDate,
    songId: SongId,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    presenter: SetKeyPresenter = koinInject(),
) {
    val model = presenter.present(SetKeyPresenter.Params(jamDate, songId, onBack, onDone))
    SetKeyContent(model = model, modifier = modifier, contentPadding = contentPadding)
}

@Composable
private fun SetKeyContent(model: SetKeyUiModel, modifier: Modifier, contentPadding: PaddingValues) {
    val spacing = BluesJamTheme.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BluesJamTheme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(
                start = spacing.md,
                end = spacing.md,
                bottom = contentPadding.calculateBottomPadding() + spacing.lg,
            ),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        BackButton(model.back, modifier = Modifier.padding(top = spacing.sm))
        when (model) {
            is SetKeyUiModel.Loading -> Unit
            is SetKeyUiModel.Gone -> EmptyStateBlock(model.empty)
            is SetKeyUiModel.Content -> PickerBody(model)
        }
    }
}

@Composable
private fun PickerBody(model: SetKeyUiModel.Content) {
    val spacing = BluesJamTheme.spacing
    val style = SetKeyDefaults.header()
    Text(
        text = model.title,
        style = BluesJamTheme.typography.h1,
        color = BluesJamTheme.colors.text,
        modifier = Modifier.semantics { heading() },
    )
    Text(
        text = model.songTitle,
        style = BluesJamTheme.typography.songTitle,
        color = style.songTitle,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    Column(modifier = Modifier.padding(top = spacing.sm)) {
        Text(text = model.currentLabel.uppercase(), style = BluesJamTheme.typography.caption, color = style.label)
        Text(
            text = model.currentKey,
            style = BluesJamTheme.typography.key,
            color = style.currentKey,
            modifier = Modifier.semantics { contentDescription = model.currentKeyDescription },
        )
    }
    model.sections.forEach { section -> KeySection(section) }
}

@Composable
private fun KeySection(section: KeySectionUiModel) {
    val spacing = BluesJamTheme.spacing
    Column(
        modifier = Modifier.padding(top = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Text(
            text = section.label.uppercase(),
            style = BluesJamTheme.typography.caption,
            color = SetKeyDefaults.header().label,
            modifier = Modifier.semantics { heading() },
        )
        section.rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                row.forEach { cell -> KeyCell(cell, Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * One key, one node: a selectable cell is a button read "Tonalidad Bb" with the click label "elegir
 * esta tonalidad"; the current one is read "Tonalidad A, actual" and is not clickable.
 */
@Composable
private fun KeyCell(cell: KeyCellUiModel, modifier: Modifier) {
    val style = SetKeyDefaults.cell(cell.isCurrent)
    val node = if (cell.isCurrent) {
        Modifier.semantics(mergeDescendants = true) {
            contentDescription = cell.description
            cell.currentLabel?.let { stateDescription = it }
        }
    } else {
        Modifier
            .clickable(onClickLabel = cell.pickLabel, role = Role.Button) { cell.events(KeyCellUiModel.Event.Pick) }
            .semantics { contentDescription = cell.description }
    }
    Surface(
        color = style.container,
        shape = BluesJamTheme.shapes.md,
        border = style.outline?.let { BorderStroke(Dp.Hairline, it) },
        modifier = modifier,
    ) {
        Column(
            modifier = node
                .fillMaxWidth()
                .heightIn(min = LocalMinimumInteractiveComponentSize.current)
                .padding(vertical = BluesJamTheme.spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = cell.key, style = BluesJamTheme.typography.songTitle, color = style.content)
            cell.currentLabel?.let { Text(text = it, style = BluesJamTheme.typography.caption, color = style.caption) }
        }
    }
}

@Preview
@Composable
private fun SetKeyScreenPreview() {
    val model = SetKeyUiModel.Content(
        back = backUiModel {},
        title = SetKeyCopy.TITLE,
        songTitle = "Crossroads",
        currentLabel = SetKeyCopy.CURRENT,
        currentKey = "A",
        currentKeyDescription = NextJamCopy.keyDescription("A"),
        sections = listOf(
            keySection(SetKeyCopy.MAJORS, SetKeyDefaults.MAJORS, "A") {},
            keySection(SetKeyCopy.MINORS, SetKeyDefaults.MINORS, "A") {},
        ),
    )
    BluesJamTheme { Box { SetKeyContent(model = model, modifier = Modifier, contentPadding = PaddingValues()) } }
}
