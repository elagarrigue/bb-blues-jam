package com.bbbjam.core.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.bbbjam.core.ui.R

/**
 * The two bundled families. Fonts ship in the APK, not as downloadable fonts, because the app is
 * used offline in a bar; static TTFs because `minSdk` 24 cannot apply variable-font weights. Licences
 * (SIL OFL 1.1) are in `assets/licenses`.
 */
internal object BluesJamFonts {
    val BarlowCondensed = FontFamily(
        Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
        Font(R.font.barlow_condensed_bold, FontWeight.Bold),
        Font(R.font.barlow_condensed_extrabold, FontWeight.ExtraBold),
    )
    val Chivo = FontFamily(
        Font(R.font.chivo_regular, FontWeight.Normal),
    )
}

/**
 * The type scale of `DESIGN.md`, read through `BluesJamTheme.typography`. Line heights come from the
 * Stitch export's Tailwind config; `body` and `caption` weights follow the export (400).
 */
object BluesJamTypography {
    val h1: TextStyle = TextStyle(
        fontFamily = BluesJamFonts.BarlowCondensed,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
    )
    val songTitle: TextStyle = TextStyle(
        fontFamily = BluesJamFonts.BarlowCondensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
    )

    /** The key is read at a glance in near-darkness, so its size and weight are functional. */
    val key: TextStyle = TextStyle(
        fontFamily = BluesJamFonts.BarlowCondensed,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 44.sp,
        lineHeight = 44.sp,
    )

    /**
     * The key as the main element of the song detail (`song-detail-screen`, K1): read from arm's
     * length on stage, so it is the largest style. Used only by the detail; rows keep [key].
     */
    val keyDisplay: TextStyle = TextStyle(
        fontFamily = BluesJamFonts.BarlowCondensed,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 96.sp,
        lineHeight = 96.sp,
    )
    val body: TextStyle = TextStyle(
        fontFamily = BluesJamFonts.Chivo,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    )
    val caption: TextStyle = TextStyle(
        fontFamily = BluesJamFonts.Chivo,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
}
