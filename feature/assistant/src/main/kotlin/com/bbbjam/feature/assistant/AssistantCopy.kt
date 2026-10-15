package com.bbbjam.feature.assistant

/** The Asistente tab's copy, Rioplatense Spanish with *vos* (D-12). */
internal object AssistantCopy {
    const val TITLE = "Asistente"
    const val INTRO_TITLE = "Pedí una lista"
    const val INTRO_MESSAGE =
        "Contale al asistente qué jam querés armar: cuántos temas, qué estilo, cómo arrancar y cómo cerrar. " +
            "Solo propone temas del catálogo."
    const val NOT_CONFIGURED_TITLE = "El asistente no está configurado"
    const val NOT_CONFIGURED_MESSAGE =
        "Esta versión de la app no tiene la clave del servicio, así que no se pueden pedir listas."

    const val AUTHOR_YOU = "Vos"
    const val AUTHOR_ASSISTANT = "Asistente"

    const val PENDING_LABEL = "El asistente está armando la propuesta. Puede tardar unos segundos."
    const val CANCEL = "Cancelar"

    const val COMPOSER_LABEL = "Tu pedido"
    const val COMPOSER_PLACEHOLDER = "Ej.: 12 temas de blues nacional, con inicio y final arriba"
    const val SEND = "Enviar"
    const val SEND_WAITING = "Esperando respuesta…"

    const val QUESTIONS_HEADING = "PREGUNTAS"

    const val OFFLINE = "No hay conexión. Para usar el asistente necesitás internet."
    const val UNAUTHORIZED = "El servicio rechazó la clave del asistente."
    const val UNAVAILABLE = "El servicio del asistente no respondió. Probá de nuevo en un rato."
    const val INVALID_RESPONSE = "No se pudo leer la respuesta del asistente. Probá de nuevo."
    const val TOO_MANY_STEPS = "El asistente no llegó a una propuesta. Probá con un pedido más concreto."
    const val CATALOG_EMPTY = "El catálogo todavía no se cargó. Abrí la app con conexión y volvé a probar."
    const val INTERRUPTED = "No se pudo terminar este pedido. Probá de nuevo."
    const val CHECKING = "Comprobando el servicio…"
    const val SERVICE_DOWN =
        "El asistente no está disponible en este momento. El servicio de IA no responde; probá más tarde."
    const val INCOMPLETE_RETRY = "La respuesta se cortó antes de terminar. Probá de nuevo."
    const val INCOMPLETE = "La respuesta se cortó antes de terminar. Esto es lo que llegó; no está completa."
    const val NOT_COMPLETE_SUFFIX = " Lo que llegó no está completo."
    const val CANCELLED = "Cancelaste este pedido."
    const val RETRY = "Reintentar"

    /** "PROPUESTA · 12 temas", "PROPUESTA · 1 tema". */
    fun proposalHeading(count: Int): String = "PROPUESTA · $count " + if (count == 1) "tema" else "temas"

    /** "PROPUESTA INCOMPLETA · 3 temas", "PROPUESTA INCOMPLETA · 1 tema". */
    fun incompleteHeading(count: Int): String = "PROPUESTA INCOMPLETA · $count " + if (count == 1) "tema" else "temas"

    /** The caption for ids the model gave that are not catalog songs; the ids are never shown. */
    fun discarded(count: Int): String = if (count == 1) {
        "Se descartó 1 tema que no está en el catálogo."
    } else {
        "Se descartaron $count temas que no están en el catálogo."
    }
}
