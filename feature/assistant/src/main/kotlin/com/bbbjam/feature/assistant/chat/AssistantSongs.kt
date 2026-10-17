package com.bbbjam.feature.assistant.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.bbbjam.core.ui.theme.BluesJamTheme
import com.bbbjam.feature.assistant.AssistantDefaults
import com.bbbjam.feature.assistant.ProposedSongUiModel
import com.bbbjam.feature.assistant.apply.ApplyDefaults

/** A heading and the song rows under it; the heading is a screen-reader heading. */
@Composable
internal fun SongList(heading: String, songs: List<ProposedSongUiModel>, captionColor: Color) {
    Caption(heading, captionColor, Modifier.semantics { heading() })
    Column(verticalArrangement = Arrangement.spacedBy(BluesJamTheme.spacing.sm)) {
        songs.forEach { song -> SongRow(song) }
    }
}

/** One proposed song: one merged, non-clickable node read in visual order. */
@Composable
private fun SongRow(song: ProposedSongUiModel) {
    val spacing = BluesJamTheme.spacing
    val typography = BluesJamTheme.typography
    val style = AssistantDefaults.song()
    Column(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(text = song.positionLabel, style = typography.songTitle, color = style.position)
            Text(
                text = song.title,
                style = typography.songTitle,
                color = style.title,
                maxLines = TITLE_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = song.key,
                style = typography.songTitle,
                color = style.key,
                modifier = Modifier.semantics { contentDescription = song.keyDescription },
            )
        }
        Text(text = song.artist, style = typography.body, color = style.artist)
        Text(text = song.reason, style = typography.body, color = style.reason)
        song.applyStatus?.let { status ->
            Text(text = status.text, style = typography.caption, color = ApplyDefaults.status(status.kind))
        }
    }
}

private const val TITLE_MAX_LINES = 2
