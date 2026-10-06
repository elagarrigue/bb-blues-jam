package com.bbbjam.feature.nextjam

/**
 * The catalog picker's copy, approved on 5–6 October 2026 (`docs/specs/admin-add-song-to-setlist.md`,
 * C1). Rioplatense Spanish with vos (D-12).
 */
internal object AddSongCopy {
    const val TITLE = "Agregar tema"
    const val SEARCH_LABEL = "Buscar por título o artista"
    const val ALREADY_LISTED = "Ya está en la lista"
    const val PICK_LABEL = "agregar a la lista"
    const val LOADING = "Cargando el catálogo"
    const val LOAD_FAILED = "No pudimos cargar el catálogo"
    const val EMPTY_TITLE = "El catálogo está vacío"
    const val EMPTY = "Cargá temas en la pestaña Catalogo de la planilla."

    /** The search's no-results line: `Ningún tema coincide con «blues».` */
    fun noResults(query: String): String = "Ningún tema coincide con «$query»."
}
