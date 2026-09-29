package com.bbbjam.core.model

/**
 * Identity of a catalog [Song] (`Catalogo.id`): lowercase `a`–`z` and `0`–`9` words joined by single
 * hyphens, e.g. `sweet-little-angel`. Jam tabs reference it, so a malformed id fails here.
 */
@JvmInline
value class SongId(val value: String) {
    init {
        require(FORMAT.matches(value)) {
            "Invalid song id \"$value\": expected a lowercase slug such as sweet-little-angel"
        }
    }

    override fun toString(): String = value

    companion object {
        private val FORMAT = Regex("[a-z0-9]+(-[a-z0-9]+)*")

        /** The id for [text], or null when [text] is not a lowercase slug. */
        fun parseOrNull(text: String): SongId? = if (FORMAT.matches(text)) SongId(text) else null
    }
}
