package com.bbbjam.feature.nextjam

internal object MoveCopy {
    fun failed(title: String): String = "No se pudo mover «$title»"
    fun position(position: Int, total: Int): String = "Posición $position de $total"
}
