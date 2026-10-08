package com.bbbjam.feature.nextjam

internal fun publishDetails(songCount: Int): String = if (songCount == 1) {
    "Los músicos van a ver el tema cuando abran o actualicen la app."
} else {
    "Los músicos van a ver los $songCount temas cuando abran o actualicen la app."
}
