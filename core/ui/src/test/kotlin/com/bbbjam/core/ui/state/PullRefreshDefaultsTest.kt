package com.bbbjam.core.ui.state

import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.theme.BluesJamColors
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pull indicator's look and the shared model (`live-refresh-during-jam`, Decision 5 and 6). */
class PullRefreshDefaultsTest {

    private val colors = BluesJamColors

    @Test
    fun `the indicator is a raised surface disc with a text arc`() {
        assertEquals(
            PullRefreshDefaults.IndicatorColors(container = colors.surfaceRaised, content = colors.text),
            PullRefreshDefaults.indicatorColors(),
        )
    }

    /**
     * Every amber role has the same value, so the roles the file reads are checked in the source (the
     * module directory is the working directory, as in `ListStateDefaultsTest`).
     */
    @Test
    fun `the pull to refresh component reads no amber role`() {
        val source = File("src/main/kotlin/com/bbbjam/core/ui/state/PullRefresh.kt").readText()
        assertTrue(AMBER_ROLE_READ.findAll(source).none())
        assertFalse(source.contains("MaterialTheme"))
    }

    @Test
    fun `the accessibility action is Actualizar`() {
        assertEquals("Actualizar", PullRefreshCopy.ACTION)
    }

    @Test
    fun `the idle model is not refreshing and equals any keyless idle model`() {
        assertFalse(PullRefreshUiModel.IDLE.isRefreshing)
        assertEquals(PullRefreshUiModel(isRefreshing = false, events = EventHandler { }), PullRefreshUiModel.IDLE)
    }

    private companion object {
        val AMBER_ROLE_READ = Regex(
            """\bcolors\s*\.\s*""" +
                """(primaryAction|onPrimaryAction|slotOpen|key|published|onPublished|activeFilter|onActiveFilter)\b""",
        )
    }
}
