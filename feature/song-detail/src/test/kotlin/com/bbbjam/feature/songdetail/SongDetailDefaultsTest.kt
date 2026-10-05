package com.bbbjam.feature.songdetail

import com.bbbjam.core.ui.theme.BluesJamColors
import com.bbbjam.core.ui.theme.BluesJamTypography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** K1: the key is the largest text on the detail, in the amber `key` role. */
class SongDetailDefaultsTest {

    @Test
    fun `the key style is keyDisplay at 96sp`() {
        assertEquals(BluesJamTypography.keyDisplay, SongDetailDefaults.keyStyle)
        assertTrue(SongDetailDefaults.keyStyle.fontSize.isSp)
        assertEquals(96f, SongDetailDefaults.keyStyle.fontSize.value, 0f)
    }

    @Test
    fun `the key is larger than every other text the screen draws or embeds`() {
        val key = SongDetailDefaults.keyStyle.fontSize.value
        // Its own styles, then the :core:ui components it embeds: the empty block (songTitle, body),
        // the instrument groups (body, caption), and the row key style for comparison.
        val others = SongDetailDefaults.otherStyles + listOf(
            BluesJamTypography.songTitle,
            BluesJamTypography.body,
            BluesJamTypography.caption,
            BluesJamTypography.h1,
            BluesJamTypography.key,
        )
        others.forEach { style ->
            assertTrue("key $key sp must exceed ${style.fontSize}", key > style.fontSize.value)
        }
    }

    @Test
    fun `the key colour is the key role`() {
        assertEquals(BluesJamColors.key, SongDetailDefaults.keyColor())
    }
}
