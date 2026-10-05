package com.bbbjam.feature.songdetail

/**
 * The song detail copy, approved by the user on 4 October 2026 (`docs/specs/song-detail-screen.md`,
 * C1). Rioplatense Spanish with vos (D-12). Instrument names, "LIBRE", "Otros", the hint, the
 * no-open-slots note and "Volver" are the `:core:ui` components' own approved copy.
 */
internal object SongDetailCopy {
    /** The label above the key, drawn in uppercase. */
    const val KEY_LABEL = "Tonalidad"

    /** For screen readers only: the detail draws no skeleton while the local read runs. */
    const val LOADING = "Cargando el tema"

    const val NOT_FOUND_TITLE = "Este tema ya no está en la lista"
    const val NOT_FOUND_MESSAGE =
        "Puede que la organización haya cambiado la lista. Volvé a la próxima jam para ver la actual."

    /** What a screen reader says for the key block: "Tonalidad Bm", as in the song row. */
    fun keyDescription(key: String): String = "$KEY_LABEL $key"
}
