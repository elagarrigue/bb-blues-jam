package com.bbbjam.feature.nextjam

/**
 * The Próxima jam copy, approved by the user on 2 October 2026 (`docs/specs/next-jam-read-only-list.md`,
 * C1). Rioplatense Spanish with vos (D-12). Kept in Kotlin, not string resources, as in `InfoCopy`.
 *
 * Day and month names come from `SpanishDateNames` in `:core:ui` (shared with Anteriores since
 * `past-jams-list`), hand-written rather than from `DateTimeFormatter` and a `Locale` (Decision 5).
 */
internal object NextJamCopy {
    const val TONIGHT = "Esta noche"
    const val TODAY = "Hoy"
    const val TOMORROW = "Mañana"

    const val NO_UPCOMING_JAM =
        "La próxima jam todavía no tiene fecha. Cuando se confirme, la vas a ver acá."

    /**
     * The screen's own state copy, approved on 4 October 2026 (`docs/specs/list-states.md`, C1).
     * The shared messages, labels and ages live in `:core:ui` (`ListStateCopy`).
     */
    const val LOADING = "Cargando la próxima jam"
    const val LOAD_FAILED = "No pudimos cargar la próxima jam"
    const val NO_UPCOMING_TITLE = "Todavía no hay fecha"
    const val EMPTY_SETLIST_TITLE = "Todavía no hay temas"
    const val EMPTY_SETLIST =
        "La lista está publicada pero todavía no tiene temas. ¿Tenés uno en mente? Contáselo a la organización."
    const val SETLIST_WITHHELD =
        "La lista de temas se está armando. Cuando se publique, la vas a ver acá."
    const val SETLIST_UNAVAILABLE =
        "No se pudo leer la lista de temas de esta jam. Avisale a la organización."

    /**
     * A row's state and its action for screen readers, approved on 3 October 2026
     * (`docs/specs/song-row-expansion.md`, C1). TalkBack reads the action as "Presioná dos veces
     * para ver los cupos".
     */
    const val ROW_EXPANDED = "expandido"
    const val ROW_COLLAPSED = "contraído"
    const val SHOW_SLOTS = "ver los cupos"
    const val HIDE_SLOTS = "ocultar los cupos"

    /**
     * The expanded row's entry to the song detail, approved on 4 October 2026
     * (`docs/specs/song-detail-screen.md`, C1).
     */
    const val OPEN_DETAIL = "Ver detalle del tema"

    /** "En 29 días": always days, no weeks (exact and short). Only for two days or more. */
    fun inDays(days: Long): String = "En $days días"

    /** The note under a setlist that lost [count] invalid rows (P4); [count] is at least 1. */
    fun droppedRows(count: Int): String =
        if (count == 1) "Falta 1 tema: no se pudo leer." else "Faltan $count temas: no se pudieron leer."

    /** What a screen reader says for the key: "Tonalidad Bm". */
    fun keyDescription(key: String): String = "Tonalidad $key"
}
