package com.bbbjam.core.ui.state

import com.bbbjam.core.ui.theme.BluesJamColors
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The assistant's loading dots (`assistant-streaming`, Part A): the pure alpha curve and reduced motion. */
class LoadingDotsTest {

    @Test
    fun `the alpha stays between the minimum and one for any time and dot`() {
        for (index in 0..2) {
            for (t in 0L..5_000L step 37L) {
                val alpha = loadingDotAlpha(t, index)
                assertTrue("t=$t i=$index alpha=$alpha", alpha >= LoadingDotsDefaults.MIN_ALPHA - 1e-4f)
                assertTrue("t=$t i=$index alpha=$alpha", alpha <= 1f + 1e-4f)
            }
        }
    }

    @Test
    fun `the curve repeats every period`() {
        for (index in 0..2) {
            for (t in 0L..1_200L step 50L) {
                assertEquals(
                    loadingDotAlpha(t, index),
                    loadingDotAlpha(t + LoadingDotsDefaults.PERIOD_MILLIS, index),
                    1e-4f,
                )
            }
        }
    }

    @Test
    fun `each dot lags the previous one by the stagger`() {
        for (t in 0L..1_200L step 50L) {
            assertEquals(
                loadingDotAlpha(t, 0),
                loadingDotAlpha(t + LoadingDotsDefaults.STAGGER_MILLIS, 1),
                1e-4f,
            )
            assertEquals(
                loadingDotAlpha(t, 1),
                loadingDotAlpha(t + LoadingDotsDefaults.STAGGER_MILLIS, 2),
                1e-4f,
            )
        }
    }

    @Test
    fun `the dots really move over a period`() {
        val alphas = (0L until LoadingDotsDefaults.PERIOD_MILLIS step 20L).map { loadingDotAlpha(it, 0) }
        assertEquals(1f, alphas.max(), 0.01f)
        assertEquals(LoadingDotsDefaults.MIN_ALPHA, alphas.min(), 0.01f)
    }

    @Test
    fun `only an animator scale of zero is reduced motion`() {
        assertTrue(isReducedMotion(0f))
        assertFalse(isReducedMotion(1f))
        assertFalse(isReducedMotion(0.5f))
        assertFalse(isReducedMotion(10f))
    }

    @Test
    fun `the dots are muted text, never amber, and the static alpha sits between the bounds`() {
        assertEquals(BluesJamColors.textMuted, LoadingDotsDefaults.color())
        assertTrue(LoadingDotsDefaults.STATIC_ALPHA in LoadingDotsDefaults.MIN_ALPHA..1f)
        val source = File("src/main/kotlin/com/bbbjam/core/ui/state/LoadingDots.kt").readText()
        assertFalse(source.contains("primaryAction"))
        assertFalse(source.contains("slotOpen"))
        assertFalse(source.contains("activeFilter"))
        assertFalse(Regex("""\d\.dp""").containsMatchIn(source))
    }
}
