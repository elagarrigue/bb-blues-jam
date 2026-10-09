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
    const val SETLIST_UNAVAILABLE =
        "No se pudo leer la lista de temas de esta jam. Avisale a la organización."

    /**
     * The draft card, approved on 5 October 2026 (`docs/specs/unpublished-setlist-state.md`, C1).
     * [DRAFT_LABEL] is drawn uppercase ("EN PREPARACIÓN").
     */
    const val DRAFT_LABEL = "En preparación"
    const val DRAFT_TITLE = "La lista se está armando"
    const val DRAFT_MESSAGE =
        "Cuando la organización la publique, vas a ver acá los temas, las tonalidades y los cupos libres."

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

    /**
     * The admin's controls on Próxima jam, approved on 5–6 October 2026
     * (`docs/specs/admin-add-song-to-setlist.md`, C1 and J1). [DRAFT_BADGE] is drawn uppercase.
     */
    const val ADD_SONG = "Agregar tema"
    const val ADDING = "Agregando…"
    const val DRAFT_BADGE = "Borrador"
    const val DRAFT_NOTE = "Los músicos todavía no ven esta lista."
    const val PUBLISHED_BADGE = "Publicada"
    const val PUBLISHED_NOTE = "Los músicos ya ven esta lista."
    const val PUBLISH = "Publicar lista"
    const val PUBLISH_PROMPT = "¿Publicar la lista?"
    const val PUBLISH_IRREVERSIBLE = "Desde la app no se puede volver a borrador."
    const val PUBLISH_CONFIRM = "Publicar"
    const val PUBLISHING = "Publicando…"
    const val PUBLISH_FAILED = "No se pudo publicar la lista"
    const val PUBLISH_CONSEQUENCE = "Los músicos siguen sin ver la lista."
    const val PUBLISH_RETRY = "Reintentar"
    const val PUBLISH_OFFLINE =
        "No pudimos confirmar la publicación: no hay conexión o el servidor tardó. Reintentá; si ya estaba publicada, no pasa nada."
    const val EMPTY_PUBLISH = "La lista no tiene temas. Agregá al menos uno antes de publicar."
    const val ADMIN_EMPTY_TITLE = "Todavía no hay temas"
    const val ADMIN_EMPTY = "Agregá el primero desde el catálogo."
    const val ADMIN_NO_UPCOMING = "Para armar la lista, cargá la fecha en la pestaña Jams de la planilla."
    const val CLOSE = "Cerrar"
    const val ACCESS_REFUSED =
        "La frase de acceso cambió o no es válida. Salí del modo admin en Info y volvé a entrar."
    const val OFFLINE = "No hay conexión. Probá de nuevo cuando tengas internet."
    const val UNAVAILABLE = "El servidor no respondió. Probá de nuevo en un rato."
    const val ALREADY_LISTED = "Ese tema ya está en la lista."
    const val NOT_IN_CATALOG = "Ese tema ya no está en el catálogo."
    const val JAM_CHANGED = "La jam cambió en la planilla. Actualizá y probá de nuevo."
    const val SHEET_REFUSED = "La planilla rechazó el cambio. Revisala y probá de nuevo."

    /**
     * Removing a song, approved on 6 October 2026 (`docs/specs/admin-remove-song-from-setlist.md`,
     * U1 and the copy table).
     */
    const val REMOVE = "Quitar de la lista"
    const val CONFIRM_REMOVE = "Quitar"
    const val CANCEL = "Cancelar"
    const val REMOVING = "Quitando…"
    const val PUBLISHED_REMOVE_NOTE = "La lista está publicada: los músicos van a dejar de verlo."
    const val NOT_IN_SETLIST = "Ese tema ya no estaba en la lista."
    const val DUPLICATE_SONG = "Ese tema está repetido en la planilla. Corregilo ahí y probá de nuevo."

    /**
     * Setting a key, approved on 6 October 2026 (`docs/specs/admin-set-key.md`, V1, O1 and C2). The
     * row shows the new key at once with [SAVING] under the title until the server answers.
     */
    const val CHANGE_LINEUP = "Cambiar formación"
    const val LINEUP_HEADING = "FORMACIÓN"
    const val LINEUP_DONE = "Listo"
    const val LINEUP_BLOCKED = "Tiene músico anotado. Liberá el cupo antes de sacarlo."
    const val SLOT_FILLED = "Ese cupo tiene un músico anotado. Liberalo antes de sacarlo."
    fun lineupCount(count: Int): String = when (count) {
        0 -> "No va en este tema"
        1 -> "1 cupo"
        else -> "$count cupos"
    }
    fun lineupFailed(title: String): String = "No se pudo cambiar la formación de «$title»"
    fun assignmentFailed(name: String, title: String): String = "No se pudo anotar a «$name» en «$title»"
    const val CLEAR_FAILED = "No se pudo liberar el cupo"
    const val CLEARING = "Quitando…"
    const val CLEAR_STALE = "Ese cupo ya no está ocupado. Volvé a la próxima jam para ver la lista actual."
    const val SLOT_TAKEN = "Ese cupo ya está ocupado. Actualizá la lista e intentá de nuevo."
    const val INVALID_ASSIGNMENT_NAME = "La planilla no aceptó ese nombre."

    const val SET_KEY = "Cambiar tonalidad"
    const val ADD_EXTRA = "Agregar a Otros"
    const val REMOVE_EXTRA = "Quitar"
    const val EXTRA_NAME = "Nombre"
    const val EXTRA_INSTRUMENT = "Instrumento"
    const val EXTRA_FAILED = "No se pudo cambiar Otros"
    const val SAVING = "Guardando…"
    const val MOVE_UP = "Subir"
    const val MOVE_DOWN = "Bajar"
    const val UNORDERED_SETLIST = "Las posiciones de la planilla están desordenadas. Corregilas ahí y probá de nuevo."

    /** A failed key change's card title: `No se pudo cambiar la tonalidad de «Crossroads»`. */
    fun keyFailed(title: String): String = "No se pudo cambiar la tonalidad de «$title»"

    /** The confirmation's question: `¿Quitar «Crossroads» de la lista?`. */
    fun removePrompt(title: String): String = "¿Quitar «$title» de la lista?"

    /** The confirmation's line for [count] assigned musicians (filled slots plus extras), at least 1. */
    fun assignedMusicians(count: Int): String =
        if (count == 1) "Se borra también el músico anotado." else "Se borran también los $count músicos anotados."

    /** A failed removal's card title: `No se pudo quitar «Crossroads»`. */
    fun removeFailed(title: String): String = "No se pudo quitar «$title»"

    /** The failure card's title: `No se pudo agregar «Crossroads»`. */
    fun addFailed(title: String): String = "No se pudo agregar «$title»"

    /** "En 29 días": always days, no weeks (exact and short). Only for two days or more. */
    fun inDays(days: Long): String = "En $days días"

    /** The note under a setlist that lost [count] invalid rows (P4); [count] is at least 1. */
    fun droppedRows(count: Int): String =
        if (count == 1) "Falta 1 tema: no se pudo leer." else "Faltan $count temas: no se pudieron leer."

    /** What a screen reader says for the key: "Tonalidad Bm". */
    fun keyDescription(key: String): String = "Tonalidad $key"
}
