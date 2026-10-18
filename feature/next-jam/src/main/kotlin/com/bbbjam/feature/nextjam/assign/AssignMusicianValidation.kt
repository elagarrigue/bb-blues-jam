package com.bbbjam.feature.nextjam.assign

import com.bbbjam.core.model.MusicianName
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.nextjam.R

internal fun validationMessage(name: String): UiText? {
    if (name.isBlank()) return null
    val normalizedName = name.trim().replace(Regex("\\s+"), " ")
    return when {
        name.any(Character::isISOControl) || name.any { it in ";()" } ||
            normalizedName.firstOrNull() in listOf(
                '=',
                '+',
                '-',
                '@',
            ) -> UiText.res(R.string.next_jam_failure_invalid_name)

        normalizedName.length > MAX_MUSICIAN_NAME_LENGTH -> UiText.res(R.string.assign_musician_too_long)

        MusicianName.parseOrNull(name) == null -> UiText.res(R.string.assign_musician_empty_name)

        else -> null
    }
}

private const val MAX_MUSICIAN_NAME_LENGTH = 40
