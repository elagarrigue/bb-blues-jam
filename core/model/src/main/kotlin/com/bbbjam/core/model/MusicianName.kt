package com.bbbjam.core.model

/** A normalized name accepted for assigning a musician to a slot. */
@JvmInline
value class MusicianName private constructor(val value: String) {
    override fun toString(): String = value

    companion object {
        private const val MAX_LENGTH = 40

        /** Normalizes whitespace, returning null when the resulting name is not safe to write. */
        fun parseOrNull(text: String): MusicianName? = normalize(text)
            .takeIf { normalized -> isValid(text, normalized) }
            ?.let(::MusicianName)

        private fun normalize(text: String): String = buildString(text.length) {
            var pendingSpace = false
            text.forEach { character ->
                if (character.isWhitespace()) {
                    pendingSpace = isNotEmpty()
                } else {
                    if (pendingSpace) append(' ')
                    append(character)
                    pendingSpace = false
                }
            }
        }

        private fun isValid(raw: String, normalized: String): Boolean = raw.none(Character::isISOControl) &&
            normalized.isNotEmpty() &&
            normalized.length <= MAX_LENGTH &&
            normalized.none { it in ";()" } &&
            normalized.first() !in "=+-@" &&
            normalized.any(Char::isLetterOrDigit)
    }
}
