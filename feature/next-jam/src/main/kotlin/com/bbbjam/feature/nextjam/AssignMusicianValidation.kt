package com.bbbjam.feature.nextjam

import com.bbbjam.core.model.MusicianName

internal fun validationMessage(name: String): String? {
    if (name.isBlank()) return null
    val normalizedName = name.trim().replace(Regex("\\s+"), " ")
    return when {
        name.any(Character::isISOControl) || name.any { it in ";()" } ||
            normalizedName.firstOrNull() in listOf('=', '+', '-', '@') -> AssignMusicianCopy.INVALID_NAME

        normalizedName.length > MAX_MUSICIAN_NAME_LENGTH -> AssignMusicianCopy.TOO_LONG

        MusicianName.parseOrNull(name) == null -> AssignMusicianCopy.EMPTY_NAME

        else -> null
    }
}

private const val MAX_MUSICIAN_NAME_LENGTH = 40
