package com.bbbjam.core.ui.text

/**
 * The generic failure lines of an admin write that several features show word for word
 * (`typed-write-rejections`). Plain strings: `:core:ui` cannot see `WriteOutcome`, so each feature
 * keeps its own `when` over the outcome and reads these into its UiModels. Rioplatense Spanish (D-12).
 */
object WriteFailureCopy {
    const val ACCESS_REFUSED =
        "La frase de acceso cambió o no es válida. Salí del modo admin en Info y volvé a entrar."
    const val OFFLINE = "No hay conexión. Probá de nuevo cuando tengas internet."
    const val UNAVAILABLE = "El servidor no respondió. Probá de nuevo en un rato."
    const val JAM_CHANGED = "La jam cambió en la planilla. Actualizá y probá de nuevo."
    const val SHEET_REFUSED = "La planilla rechazó el cambio. Revisala y probá de nuevo."
    const val NOT_IN_CATALOG = "Ese tema ya no está en el catálogo."
}
