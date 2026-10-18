package com.bbbjam.feature.nextjam.addsong

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.bbbjam.core.ui.nav.backUiModel
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.text.SongListText
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.core.ui.theme.BluesJamTheme
import com.bbbjam.feature.nextjam.R

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
        listedLabel = if (listed) UiText.res(R.string.add_song_already_listed) else null,
        pickLabel = UiText.res(R.string.add_song_pick_label),
        events = EventHandler {},
    )
    val model = AddSongUiModel(
        back = backUiModel {},
        title = UiText.res(R.string.add_song_title),
        search = SearchFieldUiModel("", UiText.res(R.string.add_song_search_label), EventHandler {}),
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
