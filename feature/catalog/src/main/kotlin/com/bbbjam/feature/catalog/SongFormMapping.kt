package com.bbbjam.feature.catalog

import com.bbbjam.core.data.catalog.edit.SongFields
import com.bbbjam.core.data.catalog.edit.SongFormCheck
import com.bbbjam.core.data.catalog.edit.SongTextProblem
import com.bbbjam.core.data.catalog.edit.checkSongForm
import com.bbbjam.core.model.Song
import com.bbbjam.core.ui.R as CoreR
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.text.UiText

/**
 * The add and edit dialog for the form [state] over [catalog]. `Agregar` and `Guardar` are enabled only when
 * every field is usable, no other song has the same title and artist, no write runs ([busy]) and, when
 * editing, something differs from [editing]. Errors show once a field was typed in, or is not blank.
 */
internal fun songFormModel(
    state: SongFormState,
    catalog: List<Song>,
    editing: Song?,
    busy: Boolean,
    onType: (SongFormField, String) -> Unit,
    onSubmit: (SongFields) -> Unit,
    onCancel: () -> Unit,
): SongFormUiModel {
    val check = checkSongForm(state.titleText, state.artistText, state.keyText, catalog, editing?.id)
    val fields = check.fields?.takeIf { editing == null || it.differsFrom(editing) }
    val canSubmit = fields != null && !busy
    return SongFormUiModel(
        title = UiText.res(if (editing == null) R.string.catalog_form_add_title else R.string.catalog_form_edit_title),
        titleField = FormFieldUiModel(
            label = UiText.res(R.string.catalog_form_title_label),
            value = state.titleText,
            hint = null,
            error = textError(check.titleProblem, R.string.catalog_error_title_empty, state, SongFormField.TITLE),
        ),
        artistField = FormFieldUiModel(
            label = UiText.res(R.string.catalog_form_artist_label),
            value = state.artistText,
            hint = null,
            error = textError(check.artistProblem, R.string.catalog_error_artist_empty, state, SongFormField.ARTIST)
                ?: UiText.res(R.string.catalog_error_duplicate).takeIf { check.duplicate },
        ),
        keyField = FormFieldUiModel(
            label = UiText.res(R.string.catalog_form_key_label),
            value = state.keyText,
            hint = UiText.res(R.string.catalog_form_key_hint),
            error = UiText.res(R.string.catalog_error_key).takeIf { check.keyShown(state) },
        ),
        notes = listOf(
            UiText.res(
                if (editing ==
                    null
                ) {
                    R.string.catalog_form_add_note
                } else {
                    R.string.catalog_form_edit_note
                },
            ),
        ),
        submitLabel = UiText.res(if (editing == null) R.string.catalog_form_add else R.string.catalog_form_save),
        cancelLabel = UiText.res(CoreR.string.jam_form_cancel),
        canSubmit = canSubmit,
        events = EventHandler(key = "${state.editingId}|$fields|$busy") { event ->
            when (event) {
                is SongFormUiModel.Event.Edit -> onType(event.field, event.text)
                SongFormUiModel.Event.Submit -> if (canSubmit) fields?.let(onSubmit)
                SongFormUiModel.Event.Cancel -> onCancel()
            }
        },
    )
}

private fun SongFields.differsFrom(song: Song): Boolean =
    title != song.title || artist != song.artist || defaultKey != song.defaultKey

private fun SongFormCheck.keyShown(state: SongFormState): Boolean =
    keyInvalid && (state.keyText.isNotBlank() || state.isTouched(SongFormField.KEY))

/** The error under a text field, shown once it was typed in or is not blank; an empty untouched field says nothing. */
private fun textError(problem: SongTextProblem?, emptyRes: Int, state: SongFormState, field: SongFormField): UiText? {
    val text = if (field == SongFormField.TITLE) state.titleText else state.artistText
    val shown = text.isNotBlank() || state.isTouched(field)
    return if (problem == null || !shown) {
        null
    } else {
        UiText.res(
            when (problem) {
                SongTextProblem.EMPTY -> emptyRes
                SongTextProblem.TOO_LONG -> R.string.catalog_error_too_long
                SongTextProblem.CONTROL -> R.string.catalog_error_control
                SongTextProblem.FORMULA_START -> R.string.catalog_error_formula
                SongTextProblem.NO_LETTERS -> R.string.catalog_error_no_letters
            },
        )
    }
}
