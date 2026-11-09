package com.bbbjam.core.data.jams

import java.text.Normalizer
import java.util.Locale

private val COMBINING_MARKS = Regex("\\p{M}+")

/**
 * The one rule for the `tocado` cell (D-31, `docs/sheet-schema.md`), the same as `isPlayedText_` in
 * the script: trimmed, lowercased and without its accent, the text is `si`. Null (an empty cell or a
 * missing column) and any other text are not played.
 */
internal fun isPlayedCell(text: String?): Boolean {
    if (text == null) return false
    val plain = Normalizer.normalize(text.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
    return plain == "si"
}
