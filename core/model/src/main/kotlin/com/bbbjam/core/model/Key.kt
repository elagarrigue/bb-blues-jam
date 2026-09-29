package com.bbbjam.core.model

/**
 * A musical key in the format of `docs/sheet-schema.md` (**Keys**): a note letter `A`–`G`, an
 * optional `#` or `b`, and an optional `m` for minor, e.g. `B`, `Bm`, `F#`, `Bbm`. The value is not
 * trimmed; a mapper trims and calls [parseOrNull]. The admin sets it (D-08).
 */
@JvmInline
value class Key(val value: String) {
    init {
        require(FORMAT.matches(value)) { "Invalid key \"$value\": expected A-G, optional # or b, optional m" }
    }

    /** True when the key is minor (`Bm`, `Bbm`). */
    val isMinor: Boolean
        get() = value.endsWith(MINOR_SUFFIX)

    override fun toString(): String = value

    companion object {
        private val FORMAT = Regex("[A-G][#b]?m?")
        private const val MINOR_SUFFIX = 'm'

        /** The key for [text], or null when [text] does not match the format exactly. */
        fun parseOrNull(text: String): Key? = if (FORMAT.matches(text)) Key(text) else null
    }
}
