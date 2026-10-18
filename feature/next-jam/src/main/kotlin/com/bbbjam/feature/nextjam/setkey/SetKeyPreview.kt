package com.bbbjam.feature.nextjam.setkey

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.bbbjam.core.ui.nav.backUiModel
import com.bbbjam.core.ui.text.SongListText
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.core.ui.theme.BluesJamTheme
import com.bbbjam.feature.nextjam.R

@Preview
@Composable
private fun SetKeyScreenPreview() {
    val model = SetKeyUiModel.Content(
        back = backUiModel {},
        title = UiText.res(R.string.set_key_title),
        songTitle = "Crossroads",
        currentLabel = UiText.res(R.string.set_key_current),
        currentKey = "A",
        currentKeyDescription = SongListText.keyDescription("A"),
        sections = listOf(
            keySection(UiText.res(R.string.set_key_majors), SetKeyDefaults.MAJORS, "A") {},
            keySection(UiText.res(R.string.set_key_minors), SetKeyDefaults.MINORS, "A") {},
        ),
    )
    BluesJamTheme { Box { SetKeyContent(model = model, modifier = Modifier, contentPadding = PaddingValues()) } }
}
