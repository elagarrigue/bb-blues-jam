package com.bbbjam.core.data.jams

/*
 * The jam fields' rules, the same as Apps Script's `createJam` validates (`assistant-create-jam`, D-25),
 * shared by the assistant's proposal validator and the Jams tab's form (`admin-jams-tab`). Pure.
 */

/** The longest venue the Sheet accepts, after normalizing. */
const val MAX_VENUE_LENGTH = 80

private val TIME = Regex("""([01]\d|2[0-3]):[0-5]\d""")
private val CONTROL = Regex("""[\u0000-\u001f\u007f-\u009f]""")
private val SPACES = Regex("""\s+""")
private const val FORMULA_PREFIXES = "=+-@"

/** Why a venue text is not usable. */
enum class VenueProblem {
    /** Nothing but spaces. */
    EMPTY,

    /** More than [MAX_VENUE_LENGTH] characters once normalized. */
    TOO_LONG,

    /** Starts with `=`, `+`, `-` or `@` (the Sheet would read it as a formula). */
    FORMULA,

    /** Has a control character. */
    CONTROL,
}

/** True when [text] is exactly `HH:MM`, 00:00 to 23:59. */
fun isValidTime(text: String): Boolean = TIME.matches(text)

/** The venue as the Sheet stores it: trimmed, inner spaces collapsed. Not validated. */
private fun collapsedVenue(raw: String): String = raw.trim().replace(SPACES, " ")

/** What is wrong with [raw] as a venue, or null when it is usable. */
fun venueProblem(raw: String): VenueProblem? {
    val text = collapsedVenue(raw)
    return when {
        CONTROL.containsMatchIn(raw) -> VenueProblem.CONTROL
        text.isEmpty() -> VenueProblem.EMPTY
        text.length > MAX_VENUE_LENGTH -> VenueProblem.TOO_LONG
        text.first() in FORMULA_PREFIXES -> VenueProblem.FORMULA
        else -> null
    }
}

/** The normalized venue (trimmed, inner spaces collapsed, 1..[MAX_VENUE_LENGTH], no formula prefix), or null. */
fun normalizeVenue(raw: String): String? = if (venueProblem(raw) == null) collapsedVenue(raw) else null
