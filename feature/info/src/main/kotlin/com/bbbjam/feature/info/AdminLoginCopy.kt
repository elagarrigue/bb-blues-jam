package com.bbbjam.feature.info

/**
 * The admin login copy (`docs/specs/admin-passphrase-login.md`, A5, approved 5 October 2026).
 * Rioplatense Spanish with vos (D-12). The back label is the shared `Volver` (`NavCopy`).
 */
internal object AdminLoginCopy {
    const val TITLE = "Entrar como admin"
    const val FIELD_LABEL = "Frase de acceso"
    const val SHOW = "Mostrar"
    const val HIDE = "Ocultar"
    const val SUBMIT = "Entrar"
    const val VERIFYING = "Verificando…"

    const val WRONG = "La frase de acceso no es correcta."
    const val OFFLINE = "No hay conexión. Para entrar como admin necesitás internet."
    const val UNAVAILABLE = "No se pudo verificar la frase de acceso. Probá de nuevo en un rato."
}
