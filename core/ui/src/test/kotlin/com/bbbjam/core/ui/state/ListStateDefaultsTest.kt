package com.bbbjam.core.ui.state

import com.bbbjam.core.ui.theme.BluesJamColors
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The list states' look: amber only on the error block's retry button, static greys elsewhere. */
class ListStateDefaultsTest {

    private val colors = BluesJamColors

    @Test
    fun `the retry button is the primary action`() {
        assertEquals(
            ListStateDefaults.ButtonStyle(fill = colors.primaryAction, content = colors.onPrimaryAction),
            ListStateDefaults.retryButtonStyle(),
        )
    }

    @Test
    fun `skeleton bars are raised surface on surface rows, and the notice is raised surface`() {
        assertEquals(
            ListStateDefaults.SkeletonFill(row = colors.surface, bar = colors.surfaceRaised),
            ListStateDefaults.skeletonFill(),
        )
        assertEquals(colors.surfaceRaised, ListStateDefaults.noticeFill())
        assertEquals(5, ListStateDefaults.SKELETON_ROWS)
        assertEquals(3, ListStateDefaults.HEADER_BAR_FRACTIONS.size)
        val fractions = ListStateDefaults.HEADER_BAR_FRACTIONS +
            ListStateDefaults.TITLE_BAR_FRACTION + ListStateDefaults.STRIP_BAR_FRACTION
        assertTrue(fractions.all { it > 0f && it <= 1f })
    }

    /**
     * Every amber role has the same value, so comparing colours cannot tell `slotOpen` from
     * `primaryAction`. The roles a style reads are checked in the source instead (the module
     * directory is the working directory, as in `WindowBackgroundTest`; an edit to the file
     * recompiles it, so this test reruns).
     */
    @Test
    fun `no state style uses the open slot or the active filter`() {
        val source = File("src/main/kotlin/com/bbbjam/core/ui/state/ListStateDefaults.kt").readText()
        val amberReads = AMBER_ROLE_READ.findAll(source).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("primaryAction", "onPrimaryAction"), amberReads)
        val button = ListStateDefaults.retryButtonStyle()
        val skeleton = ListStateDefaults.skeletonFill()
        listOf(skeleton.row, skeleton.bar, ListStateDefaults.noticeFill()).forEach { fill ->
            assertNotEquals(colors.slotOpen, fill)
            assertNotEquals(button.fill, fill)
        }
    }

    private companion object {
        val AMBER_ROLE_READ = Regex(
            """\bcolors\s*\.\s*""" +
                """(primaryAction|onPrimaryAction|slotOpen|key|published|onPublished|activeFilter|onActiveFilter)\b""",
        )
    }
}
