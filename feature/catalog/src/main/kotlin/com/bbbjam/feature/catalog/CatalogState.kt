package com.bbbjam.feature.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.bbbjam.core.data.catalog.edit.SongFields
import com.bbbjam.core.model.Song
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.text.UiText

/** One catalog write the admin asked for; kept so `Reintentar` sends exactly the same request. */
internal sealed interface CatalogAction {
    data class Create(val fields: SongFields) : CatalogAction

    data class Update(val id: SongId, val fields: SongFields) : CatalogAction

    data class Delete(val id: SongId) : CatalogAction
}

/** What the last write left: a result line or a failure that changed nothing. */
internal sealed interface CatalogResult {
    data class Done(val message: UiText) : CatalogResult

    data class Failed(
        val action: CatalogAction,
        val title: UiText,
        val message: UiText,
        val retryable: Boolean,
        /** The server refused because a jam lists the song, so the jams the admin sees are out of date. */
        val staleJams: Boolean = false,
    ) : CatalogResult
}

/**
 * The add and edit dialog's saveable fields: [mode] (null closed, empty to add, a song id to edit), the three
 * texts and the mask of the fields the admin typed in since the dialog opened.
 */
internal class SongFormState(
    private val mode: MutableState<String?>,
    private val title: MutableState<String>,
    private val artist: MutableState<String>,
    private val key: MutableState<String>,
    private val touched: MutableState<Int>,
) {
    val isOpen: Boolean get() = mode.value != null
    val editingId: String? get() = mode.value?.takeIf { it.isNotEmpty() }
    val titleText: String get() = title.value
    val artistText: String get() = artist.value
    val keyText: String get() = key.value

    fun isTouched(field: SongFormField): Boolean = touched.value and (1 shl field.ordinal) != 0

    fun openAdd() = open("", "", "", "")

    fun openEdit(song: Song) = open(song.id.value, song.title, song.artist, song.defaultKey.value)

    private fun open(id: String, titleText: String, artistText: String, keyText: String) {
        title.value = titleText
        artist.value = artistText
        key.value = keyText
        touched.value = 0
        mode.value = id
    }

    fun type(field: SongFormField, text: String) {
        when (field) {
            SongFormField.TITLE -> title.value = text
            SongFormField.ARTIST -> artist.value = text
            SongFormField.KEY -> key.value = text
        }
        touched.value = touched.value or (1 shl field.ordinal)
    }

    fun close() {
        mode.value = null
    }
}

@Composable
internal fun rememberSongFormState(): SongFormState {
    val mode = rememberSaveable { mutableStateOf<String?>(null) }
    val title = rememberSaveable { mutableStateOf("") }
    val artist = rememberSaveable { mutableStateOf("") }
    val key = rememberSaveable { mutableStateOf("") }
    val touched = rememberSaveable { mutableStateOf(0) }
    return SongFormState(mode, title, artist, key, touched)
}
