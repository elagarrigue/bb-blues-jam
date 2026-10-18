package com.bbbjam.core.ui.text

import com.bbbjam.core.ui.R

/**
 * The song-row text every setlist screen writes the same way (Próxima jam, Anteriores, the song
 * detail): the two-digit position, what a screen reader says for the key, and the note under a
 * setlist that lost invalid rows. Rioplatense Spanish (D-12).
 */
object SongListText {
    private const val POSITION_DIGITS = 2

    /** "01" … "13": padded with `padStart`, never `String.format` (locale-sensitive). */
    fun positionLabel(position: Int): String = position.toString().padStart(POSITION_DIGITS, '0')

    /** What a screen reader says for the key: "Tonalidad Bm". */
    fun keyDescription(key: String): UiText = UiText.res(R.string.song_list_key_description, key)

    /** The note under a setlist that lost [count] invalid rows (next-jam P4); [count] is at least 1. */
    fun droppedRows(count: Int): UiText = UiText.plural(R.plurals.song_list_dropped_rows, count)
}
