package com.bbbjam.feature.catalog

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.state.PullRefreshUiModel
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.core.ui.theme.BluesJamTheme

private val SAMPLE_ROWS = listOf(
    sampleRow("crossroads", "Crossroads", "Eric Clapton", "A"),
    sampleRow("the-thrill-is-gone", "The Thrill Is Gone", "B.B. King", "Bm"),
    sampleRow("sweet-home-chicago", "Sweet Home Chicago, la versión larga de la banda", "Robert Johnson", "E"),
)

private fun sampleRow(id: String, title: String, artist: String, key: String) = SongRowUiModel(
    id = id,
    title = title,
    artist = artist,
    key = key,
    keyDescription = UiText.raw("Tonalidad $key"),
    editDescription = UiText.raw("Editar $title"),
    deleteDescription = UiText.raw("Borrar $title"),
    actionsEnabled = true,
    events = EventHandler {},
)

private fun sampleContent(
    notice: CatalogNoticeUiModel? = null,
    form: SongFormUiModel? = null,
    delete: DeleteSongUiModel? = null,
) = CatalogUiModel.Content(
    title = UiText.raw("Temas"),
    count = UiText.raw("3 temas"),
    add = ActionUiModel(UiText.raw("Agregar tema"), enabled = true, events = EventHandler {}),
    notice = notice,
    search = SearchFieldUiModel("", UiText.raw("Buscar por título o artista"), EventHandler {}),
    list = CatalogListUiModel.Songs(SAMPLE_ROWS),
    staleness = null,
    pullRefresh = PullRefreshUiModel.IDLE,
    form = form,
    delete = delete,
)

@Composable
private fun Show(model: CatalogUiModel) {
    BluesJamTheme { CatalogContent(model, Modifier, PaddingValues()) }
}

@Preview
@Composable
private fun CatalogListPreview() = Show(sampleContent())

@Preview
@Composable
private fun CatalogFailurePreview() = Show(
    sampleContent(
        notice = CatalogNoticeUiModel.Failure(
            title = UiText.raw("No se pudo borrar el tema"),
            message = UiText.raw("Ese tema está en la lista de una jam que viene."),
            consequence = UiText.raw("El catálogo sigue como estaba."),
            retryLabel = null,
            dismissLabel = UiText.raw("Cerrar"),
            events = EventHandler {},
        ),
    ),
)

@Preview
@Composable
private fun CatalogFormPreview() = Show(
    sampleContent(
        form = SongFormUiModel(
            title = UiText.raw("Agregar tema"),
            titleField = FormFieldUiModel(UiText.raw("Título"), "Crossroads", null, null),
            artistField = FormFieldUiModel(
                UiText.raw("Artista"),
                "",
                null,
                UiText.raw("Escribí el artista."),
            ),
            keyField = FormFieldUiModel(UiText.raw("Tonalidad"), "a", UiText.raw("Ej.: A, Bm, F#"), null),
            notes = listOf(UiText.raw("El tempo, las etiquetas y la dificultad se cargan en la planilla.")),
            submitLabel = UiText.raw("Agregar"),
            cancelLabel = UiText.raw("Cancelar"),
            canSubmit = false,
            events = EventHandler {},
        ),
    ),
)

@Preview
@Composable
private fun CatalogRefusalPreview() = Show(
    sampleContent(
        delete = DeleteSongUiModel.Refused(
            songId = "crossroads",
            title = UiText.raw("No se puede borrar «Crossroads»"),
            lines = listOf(
                UiText.raw("Está en la lista de la jam del sábado 25 de julio."),
                UiText.raw("Sacalo de esa lista antes de borrarlo."),
            ),
            okLabel = UiText.raw("Entendido"),
            events = EventHandler {},
        ),
    ),
)
