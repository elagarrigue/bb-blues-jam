package com.bbbjam.feature.songdetail

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.bbbjam.core.ui.theme.BluesJamColors
import com.bbbjam.core.ui.theme.BluesJamTheme

/**
 * Every text style the detail draws, kept apart from the composable so a JVM test can prove the key
 * is the largest text on the screen (`song-detail-screen`, K1). The key is the only amber here, in
 * its `key` role.
 */
internal object SongDetailDefaults {
    val keyStyle: TextStyle get() = BluesJamTheme.typography.keyDisplay
    val titleStyle: TextStyle get() = BluesJamTheme.typography.h1
    val bodyStyle: TextStyle get() = BluesJamTheme.typography.body
    val captionStyle: TextStyle get() = BluesJamTheme.typography.caption

    /** Every style the screen draws itself, except [keyStyle]. */
    val otherStyles: List<TextStyle> get() = listOf(titleStyle, bodyStyle, captionStyle)

    /** The key's colour: the amber `key` role, never another amber role. */
    fun keyColor(colors: BluesJamColors = BluesJamColors): Color = colors.key
}
