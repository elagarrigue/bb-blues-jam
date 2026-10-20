package com.bbbjam.feature.catalog

import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SetlistProblem
import com.bbbjam.core.ui.theme.BluesJamColors
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** The pure rules behind the delete dialogs and the module's colours. */
class CatalogMappingsTest {
    private val july = LocalDate.of(2026, 7, 25)
    private val october = LocalDate.of(2026, 10, 31)
    private val november = LocalDate.of(2026, 11, 28)
    private val december = LocalDate.of(2026, 12, 12)

    @Test
    fun `future uses lists the upcoming and held-back jams that have the song, oldest first, never past ones`() {
        val snapshot = jamsSnapshot(
            upcoming = jamWith(october, setlistOf(CROSSROADS)),
            heldBack = listOf(
                jamWith(december, setlistOf(CROSSROADS, RED_HOUSE)),
                jamWith(november, setlistOf(RED_HOUSE)),
            ),
            past = listOf(jamWith(july, setlistOf(CROSSROADS, RED_HOUSE))),
        )

        assertEquals(listOf(october, december), futureUses(CROSSROADS.id, snapshot))
        assertEquals(listOf(november, december), futureUses(RED_HOUSE.id, snapshot))
        assertEquals(emptyList<LocalDate>(), futureUses(THRILL.id, snapshot))
    }

    @Test
    fun `a future setlist that cannot be read is not counted, the server decides`() {
        val snapshot = jamsSnapshot(
            upcoming = jamWith(october, Setlist.Withheld),
            heldBack = listOf(jamWith(november, Setlist.Unavailable(SetlistProblem.MISSING_TAB))),
        )

        assertEquals(emptyList<LocalDate>(), futureUses(CROSSROADS.id, snapshot))
    }

    @Test
    fun `a song with no future use gets the confirmation, one with some gets the refusal`() {
        val confirm = deleteDialog(CROSSROADS, emptyList(), onConfirm = {}, onClose = {})
        val refuse = deleteDialog(CROSSROADS, listOf(october, november, december), onConfirm = {}, onClose = {})

        assertEquals(true, confirm is DeleteSongUiModel.Confirming)
        val refused = refuse as DeleteSongUiModel.Refused
        assertEquals(
            com.bbbjam.core.ui.text.UiText.res(
                R.string.catalog_refused_many,
                com.bbbjam.core.ui.text.SpanishDateNames.sentence(october),
                2,
            ),
            refused.lines.first(),
        )
    }

    @Test
    fun `the only amber role is the key on the row and the rest is neutral`() {
        val colors = BluesJamColors
        val row = CatalogDefaults.row()
        assertEquals(colors.key, row.key)
        assertEquals(colors.text, row.title)
        assertEquals(colors.textMuted, row.artist)
        assertEquals(colors.text, row.edit)
        assertEquals(colors.error, row.delete)
        assertEquals(colors.surfaceRaised, CatalogDefaults.addButton().fill)
        assertEquals(colors.text, CatalogDefaults.addButton().content)
        assertEquals(colors.textMuted, CatalogDefaults.addButton().disabledContent)
        assertEquals(colors.textMuted, CatalogDefaults.caption())
        assertEquals(colors.text, CatalogDefaults.failure().action)
        assertEquals(colors.error, CatalogDefaults.failure().title)
        assertEquals(colors.text, CatalogDefaults.search().focusedBorder)
        assertEquals(colors.text, CatalogDefaults.formField().cursor)
        val dialog = CatalogDefaults.dialog()
        assertEquals(colors.surfaceRaised, dialog.container)
        assertEquals(colors.text, dialog.submit)
        assertEquals(colors.text, dialog.cancel)
        assertEquals(colors.error, dialog.destructive)
        listOf(row.title, row.artist, row.edit, row.delete, dialog.submit, dialog.cancel, dialog.body)
            .forEach { assertNotEquals(colors.key, it) }
    }
}
