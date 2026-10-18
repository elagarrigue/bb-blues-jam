package com.bbbjam.feature.nextjam.assign

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.ui.nav.backUiModel
import com.bbbjam.core.ui.theme.BluesJamTheme

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
