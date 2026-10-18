package com.bbbjam.feature.nextjam.assign

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.ui.nav.backUiModel
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.core.ui.theme.BluesJamTheme
import com.bbbjam.feature.nextjam.R

@Preview
@Composable
private fun AssignMusicianPreview() {
    val model = AssignMusicianUiModel.Content(
        back = backUiModel {}, title = UiText.res(R.string.assign_musician_title), songTitle = "Crossroads",
        slotLabel = AssignMusicianDefaults.slotName(Instrument.GUITAR, 2),
        instrument = Instrument.GUITAR, ordinal = 2, name = "",
        nameLabel = UiText.res(R.string.assign_musician_name), validationMessage = null,
        currentSection = SuggestionSectionUiModel(UiText.res(R.string.assign_musician_current), null, emptyList()),
        pastSection = SuggestionSectionUiModel(
            UiText.res(R.string.assign_musician_past),
            UiText.res(R.string.assign_musician_past_empty),
            emptyList(),
        ),
        submitLabel = UiText.res(R.string.assign_musician_submit), submitEnabled = false,
        events = com.bbbjam.core.ui.presenter.EventHandler {},
    )
    BluesJamTheme { AssignMusicianContent(model, Modifier, PaddingValues()) }
}
