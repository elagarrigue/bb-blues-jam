package com.bbbjam.feature.info

/**
 * The Info copy, approved by the user on 30 September 2026 (`docs/specs/info-screen.md`). Facts come
 * only from `docs/info-content.md`: no venue (D-19), no station. Rioplatense Spanish with vos
 * (D-12). Kept in Kotlin, not string resources: the app is Spanish-only, the copy belongs in the
 * UiModel for the assistant, and the presenter stays Android-free.
 */
internal object InfoCopy {
    const val TITLE = "Bahía Blanca Blues"
    const val TAGLINE = "Comunidad de amantes del blues"

    const val ORGANIZER_TITLE = "Quién organiza"
    const val ORGANIZER_BODY =
        "La jam la organiza Bahía Blanca Blues, una comunidad de amantes del blues que también arma " +
            "festivales, conciertos y programas de radio."

    const val JAM_TITLE = "La jam"
    const val JAM_BODY =
        "Una jam de blues en Bahía Blanca, una vez por mes. El lugar puede cambiar de una jam a otra: " +
            "lo ves junto a la fecha de cada una."

    const val HIDEAWAY_TITLE = "Hideaway"
    const val HIDEAWAY_BODY =
        "Hideaway es el programa de radio de Bahía Blanca Blues. Sale los martes de 20 a 22."

    const val JOIN_TITLE = "Cómo sumarte"
    const val JOIN_BODY =
        "Si tocás, venite a la jam y anotate ahí: la organización te suma a un tema. " +
            "No necesitás cuenta ni registrarte en la app."

    const val LINKS_TITLE = "Redes"

    const val INSTAGRAM_LABEL = "Instagram"
    const val INSTAGRAM_DESTINATION = "@bahiablancablues"
    const val INSTAGRAM_OPEN = "Abrir Instagram"

    const val YOUTUBE_LABEL = "YouTube"
    const val YOUTUBE_DESTINATION = "youtube.com"
    const val YOUTUBE_OPEN = "Abrir YouTube"

    const val LINKTREE_LABEL = "Linktree"
    const val LINKTREE_DESTINATION = "linktr.ee/bahiablancablues"
    const val LINKTREE_OPEN = "Abrir Linktree"

    const val LINK_ERROR = "No se pudo abrir el enlace."

    const val ADMIN_ENTRY = "Entrar como admin"

    // admin-passphrase-login (A5, approved 5 October 2026).
    const val ADMIN_ACTIVE = "Modo admin activo"
    const val ADMIN_LOG_OUT = "Salir del modo admin"
}
