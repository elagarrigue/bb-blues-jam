package com.bbbjam.feature.nextjam.addsong

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.bbbjam.core.ui.nav.backUiModel
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.text.SongListText
import com.bbbjam.core.ui.theme.BluesJamTheme

@Preview
@Composable
private fun AddSongScreenPreview() {
    fun row(id: String, title: String, artist: String, key: String, listed: Boolean = false) = CatalogRowUiModel(
        id = id,
        title = title,
        artist = artist,
        key = key,
        keyDescription = SongListText.keyDescription(key),
        isListed = listed,
        listedLabel = if (listed) AddSongCopy.ALREADY_LISTED else null,
        pickLabel = AddSongCopy.PICK_LABEL,
        events = EventHandler {},
    )
    val model = AddSongUiModel(
        back = backUiModel {},
        title = AddSongCopy.TITLE,
        search = SearchFieldUiModel("", AddSongCopy.SEARCH_LABEL, EventHandler {}),
        content = AddSongContentUiModel.Songs(
            listOf(
                row("crossroads", "Crossroads", "Eric Clapton", "A"),
                row("red-house", "Red House", "Jimi Hendrix", "Bb", listed = true),
                row("the-thrill-is-gone", "The Thrill Is Gone", "B.B. King", "Bm"),
            ),
        ),
    )
    BluesJamTheme { Box { AddSongContent(model = model, modifier = Modifier, contentPadding = PaddingValues()) } }
}
