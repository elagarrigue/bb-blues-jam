package com.bbbjam.feature.pastjams

import com.bbbjam.core.ui.jamform.JamFormText
import com.bbbjam.core.ui.text.SpanishDateNames
import java.time.LocalDate
import java.time.LocalTime

/**
 * The Anteriores copy, approved by the user on 4 October 2026 (`docs/specs/past-jams-list.md`, C1
 * and P1). Rioplatense Spanish with vos (D-12). Kept in Kotlin, not string resources, as in
 * `NextJamCopy`. The shared state messages, labels and ages live in `:core:ui` (`ListStateCopy`).
 */
internal object PastJamsCopy {
    const val TITLE = "Jams anteriores"
    const val LOADING = "Cargando las jams anteriores"
    const val LOAD_FAILED = "No pudimos cargar las jams anteriores"
    const val EMPTY_TITLE = "Todavía no hay jams anteriores"
    const val EMPTY_MESSAGE = "Después de cada jam, su lista queda guardada acá para que veas qué se tocó."

    /** A past draft (P1): the jam happened, its list was never published. */
    const val SETLIST_NOT_PUBLISHED = "La lista de esta jam no se publicó."
    const val SETLIST_UNAVAILABLE = "No se pudo leer la lista de esta jam."
    const val EMPTY_SETLIST = "Esta jam no tiene temas cargados."

    /** "1 tema", "13 temas"; [count] is at least 1 (no songs is [EMPTY_SETLIST], never "0 temas"). */
    fun songCount(count: Int): String = if (count == 1) "1 tema" else "$count temas"

    /** The hook's tail after the first titles: "y 10 más". */
    fun andMore(count: Int): String = "y $count más"

    /** The click label of a row that opens its jam (`past-jam-detail`, C1). */
    const val OPEN_JAM = "ver la lista de temas"

    /** The admin's view (`admin-jams-tab`, D-27): the tab is called Jams and lists the jams to come. */
    const val TITLE_ADMIN = "Jams"
    const val UPCOMING_HEADING = "PRÓXIMAS"
    const val PAST_HEADING = "ANTERIORES"
    const val NO_FUTURE = "No hay ninguna jam programada."
    const val NO_SONGS_YET = "Sin temas todavía"
    const val FUTURE_UNAVAILABLE = "No se pudo leer la lista"

    /** Notes per status, moved from Próxima jam (same text). */
    const val DRAFT_NOTE = "Los músicos todavía no ven esta jam."
    const val CREATED_NOTE = "Los músicos ven la fecha, pero todavía no la lista."
    const val PUBLISHED_NOTE = "Los músicos ya ven esta lista."

    /** Publishing and changing the status, moved from Próxima jam (same text). */
    const val PUBLISH = "Publicar lista"
    const val PUBLISH_PROMPT = "¿Publicar la lista?"
    const val PUBLISH_REVERSIBLE = "Después podés volver a cambiar el estado desde acá."
    const val PUBLISH_CONFIRM = "Publicar"
    const val PUBLISHING = "Publicando…"
    const val PUBLISH_FAILED = "No se pudo publicar la lista"
    const val PUBLISH_CONSEQUENCE = "Los músicos siguen sin ver la lista."
    const val PUBLISH_RETRY = "Reintentar"
    const val PUBLISH_OFFLINE =
        "No pudimos confirmar la publicación: no hay conexión o el servidor tardó. Reintentá; si ya estaba publicada, no pasa nada."
    const val EMPTY_PUBLISH = "La lista no tiene temas. Agregá al menos uno antes de publicar."
    const val TO_CREATED = "Pasar a creada"
    const val TO_DRAFT = "Pasar a borrador"
    const val STATUS_SAVING = "Cambiando el estado…"
    const val UNPUBLISH_PROMPT = "¿Dejar de publicar la lista?"
    const val UNPUBLISH_TO_CREATED_BODY =
        "Los músicos van a dejar de ver la lista cuando actualicen la app. Van a seguir viendo la fecha."
    const val UNPUBLISH_TO_DRAFT_BODY =
        "Los músicos van a dejar de ver esta jam, con su fecha y su lista, cuando actualicen la app."
    const val STATUS_FAILED = "No se pudo cambiar el estado"
    const val STATUS_CONSEQUENCE = "La jam sigue como estaba."
    const val STATUS_SCRIPT_OUTDATED =
        "El script de la planilla todavía no sabe cambiar el estado. Actualizalo con la guía y probá de nuevo."
    const val CLOSE = "Cerrar"
    const val CANCEL = "Cancelar"
    const val ACCESS_REFUSED =
        "La frase de acceso cambió o no es válida. Salí del modo admin en Info y volvé a entrar."
    const val OFFLINE = "No hay conexión. Probá de nuevo cuando tengas internet."
    const val UNAVAILABLE = "El servidor no respondió. Probá de nuevo en un rato."
    const val JAM_CHANGED = "La jam cambió en la planilla. Actualizá y probá de nuevo."
    const val SHEET_REFUSED = "La planilla rechazó el cambio. Revisala y probá de nuevo."

    /** The "Nueva jam" form (`admin-jams-tab`, J7/J9). */
    const val NEW_JAM = "Nueva jam"
    const val NEW_JAM_CREATE = "Crear la jam"
    const val NEW_JAM_DRAFT_NOTE =
        "Se crea como borrador: los músicos no la ven hasta que la pases a creada o publiques la lista."
    const val CREATING = "Creando la jam…"
    const val CREATED = "Jam creada como borrador."
    const val CREATE_FAILED = "No se pudo crear la jam"
    const val CREATE_CONSEQUENCE = "La jam no se creó."
    const val CREATE_EXISTS = "Ya hay una jam o una pestaña con esa fecha en la planilla."
    const val CREATE_INVALID = "La planilla rechazó los datos. Revisalos y probá de nuevo."
    const val CREATE_SCRIPT_OUTDATED =
        "El script de la planilla todavía no sabe crear jams. Actualizalo con la guía y probá de nuevo."

    /** "sábado 31 de octubre": the date inside a sentence, day name in lower case. */
    fun sentenceDate(date: LocalDate): String = JamFormText.sentenceDate(date)

    /** The line of a held-back card: which jam is the next one (`admin-edit-delete-jam` C3). */
    fun heldBack(upcoming: LocalDate): String = "En espera: la próxima jam es la del ${sentenceDate(upcoming)}."

    /** A visible held-back jam behind a BORRADOR upcoming jam is the musicians next jam (D-26). */
    fun visibleBehindDraft(upcoming: LocalDate): String =
        "Los músicos la ven como la próxima jam, porque la del ${sentenceDate(upcoming)} está en borrador."

    fun waitsNote(upcoming: LocalDate): String =
        "Queda en espera: la próxima jam sigue siendo la del ${sentenceDate(upcoming)}."

    /** What a screen reader says while the detail has nothing to show yet. */
    const val DETAIL_LOADING = "Cargando la jam"
    const val NOT_FOUND_TITLE = "Esta jam ya no está en el archivo"
    const val NOT_FOUND_MESSAGE =
        "Puede que la organización la haya cambiado. Volvé a Jams para ver las jams guardadas."
}

/** "Sábado 25 de julio de 2026": day, date and year, no time. */
internal fun pastJamDateLabel(date: LocalDate): String {
    val day = SpanishDateNames.day(date.dayOfWeek)
    val month = SpanishDateNames.month(date.month)
    return "$day ${date.dayOfMonth} de $month de ${date.year}"
}

/** "Sábado 31 de octubre · 21:00": day, date and start time, no year (a jam to come). */
internal fun jamDateLabel(date: LocalDate, startTime: LocalTime): String {
    val day = SpanishDateNames.day(date.dayOfWeek)
    val month = SpanishDateNames.month(date.month)
    val time = "${startTime.hour.twoDigits()}:${startTime.minute.twoDigits()}"
    return "$day ${date.dayOfMonth} de $month · $time"
}

private const val TIME_DIGITS = 2

/** Not `String.format`, which is locale-sensitive. */
private fun Int.twoDigits(): String = toString().padStart(TIME_DIGITS, '0')
