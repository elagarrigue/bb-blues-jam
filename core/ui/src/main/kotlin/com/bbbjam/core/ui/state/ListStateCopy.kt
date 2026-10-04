package com.bbbjam.core.ui.state

/**
 * The copy every list's states share, approved by the user on 4 October 2026
 * (`docs/specs/list-states.md`, C1). Rioplatense Spanish with vos (D-12). Feature-specific titles
 * and the skeleton's description stay in the feature's own copy.
 */
internal object ListStateCopy {
    const val ERROR_OFFLINE =
        "No hay conexión y todavía no hay nada guardado. Revisá los datos o el wifi y probá de nuevo."
    const val ERROR_OTHER =
        "Algo falló al leer los datos. Probá de nuevo en un rato; si sigue fallando, avisale a la organización."
    const val RETRY = "Reintentar"
    const val NOTICE_OFFLINE = "Sin conexión"
    const val NOTICE_OTHER = "No se pudo actualizar"
    const val REFRESHING = "Actualizando…"

    /** "Mostrando lo guardado hace 3 horas." */
    fun showingSaved(age: String): String = "Mostrando lo guardado $age."

    /**
     * "hace menos de un minuto", "hace 1 minuto", "hace n minutos", "hace 1 hora", "hace n horas",
     * "hace 1 día", "hace n días": whole units, rounded down.
     */
    fun age(minutes: Long): String = when {
        minutes < 1 -> "hace menos de un minuto"
        minutes < MINUTES_PER_HOUR -> plural(minutes, "minuto", "minutos")
        minutes < MINUTES_PER_DAY -> plural(minutes / MINUTES_PER_HOUR, "hora", "horas")
        else -> plural(minutes / MINUTES_PER_DAY, "día", "días")
    }

    private fun plural(count: Long, one: String, many: String): String =
        if (count == 1L) "hace 1 $one" else "hace $count $many"

    private const val MINUTES_PER_HOUR = 60L
    private const val MINUTES_PER_DAY = 24L * MINUTES_PER_HOUR
}
