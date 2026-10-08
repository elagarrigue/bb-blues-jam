package com.bbbjam.feature.nextjam

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.SlotPosition
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.nav.BackButton
import com.bbbjam.core.ui.nav.backUiModel
import com.bbbjam.core.ui.state.EmptyStateBlock
import com.bbbjam.core.ui.theme.BluesJamTheme
import java.time.LocalDate
import org.koin.compose.koinInject

/** Full-screen assignment editor for a fixed open slot. */
@Composable
fun AssignMusicianScreen(
    jamDate: LocalDate,
    songId: SongId,
    instrument: Instrument,
    ordinal: SlotPosition,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    presenter: AssignMusicianPresenter = koinInject(),
) {
    val model = presenter.present(AssignMusicianPresenter.Params(jamDate, songId, instrument, ordinal, onBack, onDone))
    AssignMusicianContent(model, modifier, contentPadding)
}

@Composable
private fun AssignMusicianContent(model: AssignMusicianUiModel, modifier: Modifier, contentPadding: PaddingValues) {
    val spacing = BluesJamTheme.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BluesJamTheme.colors.background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.md)
            .padding(bottom = contentPadding.calculateBottomPadding() + spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        BackButton(model.back, modifier = Modifier.padding(top = spacing.sm))
        when (model) {
            is AssignMusicianUiModel.Loading -> Unit
            is AssignMusicianUiModel.Gone -> EmptyStateBlock(model.empty)
            is AssignMusicianUiModel.Content -> AssignmentBody(model)
        }
    }
}

@Composable
private fun AssignmentBody(model: AssignMusicianUiModel.Content) {
    val spacing = BluesJamTheme.spacing
    Text(
        model.title,
        style = BluesJamTheme.typography.h1,
        color = BluesJamTheme.colors.text,
        modifier = Modifier.semantics {
            heading()
        },
    )
    Text(model.songTitle, style = BluesJamTheme.typography.songTitle, color = BluesJamTheme.colors.text)
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(
            AssignMusicianCopy.SLOT.uppercase(),
            style = BluesJamTheme.typography.caption,
            color = BluesJamTheme.colors.textMuted,
        )
        Text(model.slotLabel, style = BluesJamTheme.typography.body, color = BluesJamTheme.colors.text)
    }
    OutlinedTextField(
        value = model.name,
        onValueChange = { model.events(AssignMusicianUiModel.Content.Event.NameChanged(it)) },
        label = { Text(model.nameLabel) },
        singleLine = true,
        isError = model.validationMessage != null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            if (model.submitEnabled) model.events(AssignMusicianUiModel.Content.Event.Submit)
        }),
        supportingText = model.validationMessage?.let { message -> ({ Text(message) }) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = BluesJamTheme.colors.text,
            unfocusedTextColor = BluesJamTheme.colors.text,
            focusedBorderColor = BluesJamTheme.colors.border,
            focusedLabelColor = BluesJamTheme.colors.text,
            cursorColor = BluesJamTheme.colors.text,
            errorBorderColor = BluesJamTheme.colors.error,
            errorLabelColor = BluesJamTheme.colors.error,
            errorSupportingTextColor = BluesJamTheme.colors.error,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    SuggestionSection(model.currentSection)
    SuggestionSection(model.pastSection)
    Button(
        onClick = { model.events(AssignMusicianUiModel.Content.Event.Submit) },
        enabled = model.submitEnabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = BluesJamTheme.colors.surfaceRaised,
            contentColor = BluesJamTheme.colors.text,
            disabledContainerColor = BluesJamTheme.colors.surface,
            disabledContentColor = BluesJamTheme.colors.textMuted,
        ),
        modifier = Modifier.fillMaxWidth().heightIn(min = LocalMinimumInteractiveComponentSize.current),
    ) { Text(model.submitLabel) }
}

@Composable
private fun SuggestionSection(model: SuggestionSectionUiModel) {
    val spacing = BluesJamTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(
            model.label.uppercase(),
            style = BluesJamTheme.typography.caption,
            color = BluesJamTheme.colors.textMuted,
            modifier = Modifier.semantics {
                heading()
            },
        )
        model.rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = LocalMinimumInteractiveComponentSize.current)
                    .clickable(role = Role.Button, onClickLabel = row.clickLabel) {
                        row.events(SuggestionRowUiModel.Event.Select)
                    }
                    .padding(horizontal = spacing.sm),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) { Text(row.name, style = BluesJamTheme.typography.body, color = BluesJamTheme.colors.text) }
        }
        model.emptyMessage?.let {
            Text(it, style = BluesJamTheme.typography.body, color = BluesJamTheme.colors.textMuted)
        }
    }
}

@Preview
@Composable
private fun AssignMusicianPreview() {
    val model = AssignMusicianUiModel.Content(
        back = backUiModel {}, title = AssignMusicianCopy.TITLE, songTitle = "Crossroads",
        slotLabel = "Guitarra 2", instrument = Instrument.GUITAR, ordinal = 2, name = "",
        nameLabel = AssignMusicianCopy.NAME, validationMessage = null,
        currentSection = SuggestionSectionUiModel(AssignMusicianCopy.CURRENT, null, emptyList()),
        pastSection = SuggestionSectionUiModel(AssignMusicianCopy.PAST, AssignMusicianCopy.PAST_EMPTY, emptyList()),
        submitLabel = AssignMusicianCopy.SUBMIT, submitEnabled = false,
        events = com.bbbjam.core.ui.presenter.EventHandler {},
    )
    BluesJamTheme { AssignMusicianContent(model, Modifier, PaddingValues()) }
}
