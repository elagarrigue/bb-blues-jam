package com.bbbjam.feature.nextjam.setkey

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.bbbjam.core.ui.nav.backUiModel
import com.bbbjam.core.ui.text.SongListText
import com.bbbjam.core.ui.theme.BluesJamTheme

@Preview
@Composable
private fun SetKeyScreenPreview() {
    val model = SetKeyUiModel.Content(
        back = backUiModel {},
        title = SetKeyCopy.TITLE,
        songTitle = "Crossroads",
        currentLabel = SetKeyCopy.CURRENT,
        currentKey = "A",
        currentKeyDescription = SongListText.keyDescription("A"),
        sections = listOf(
            keySection(SetKeyCopy.MAJORS, SetKeyDefaults.MAJORS, "A") {},
            keySection(SetKeyCopy.MINORS, SetKeyDefaults.MINORS, "A") {},
        ),
    )
    BluesJamTheme { Box { SetKeyContent(model = model, modifier = Modifier, contentPadding = PaddingValues()) } }
}
