package com.bbbjam.core.data.catalog.edit

import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Song
import com.bbbjam.core.model.SongId
import java.text.Normalizer

/*
 * The song form's rules (`admin-catalog-tab`, C3-C5), pure so a JVM test can check them. They are the
 * same rules Apps Script's `createSong` and `updateSong` enforce; the server stays the authority.
 */

/** The longest title or artist the Sheet accepts, after normalizing. */
const val MAX_SONG_TEXT_LENGTH = 100

private val CONTROL = Regex("""[\u0000-\u001f\u007f-\u009f]""")
private val SPACES = Regex("""[\s\u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000\ufeff]+""")
private val ACCENTS = Regex("""[\u0300-\u036f]""")
private val NOT_SLUG = Regex("""[^a-z0-9]+""")
private const val FORMULA_PREFIXES = "=+-@"

/** Why a title or an artist is not usable. */
enum class SongTextProblem {
    /** Nothing but spaces. */
    EMPTY,

    /** More than [MAX_SONG_TEXT_LENGTH] characters once normalized. */
    TOO_LONG,

    /** Has a control character. */
    CONTROL,

    /** Starts with `=`, `+`, `-` or `@` (the Sheet would read it as a formula). */
    FORMULA_START,

    /** No letter or digit, so no id can be made from it. */
    NO_LETTERS,
}

/** [raw] trimmed with every whitespace run collapsed to one space. Not validated. */
fun normalizeSongText(raw: String): String = raw.replace(SPACES, " ").trim(' ')

/** What is wrong with [raw] as a title or an artist, or null when it is usable. */
fun songTextProblem(raw: String): SongTextProblem? {
    val text = normalizeSongText(raw)
    return when {
        CONTROL.containsMatchIn(raw) -> SongTextProblem.CONTROL
        text.isEmpty() -> SongTextProblem.EMPTY
        text.length > MAX_SONG_TEXT_LENGTH -> SongTextProblem.TOO_LONG
        text.first() in FORMULA_PREFIXES -> SongTextProblem.FORMULA_START
        songSlug(text).isEmpty() -> SongTextProblem.NO_LETTERS
        else -> null
    }
}

/** The key typed by the admin: trimmed, a leading `a`-`g` upper-cased, then [Key.parseOrNull]. */
fun parseKeyInput(raw: String): Key? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    val first = text.first()
    return Key.parseOrNull(if (first in 'a'..'g') first.uppercaseChar() + text.substring(1) else text)
}

/** The slug of [text]: accents dropped, lowercase, every run outside `a-z0-9` one `-`, trimmed of `-`. */
fun songSlug(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
    .replace(ACCENTS, "")
    .lowercase()
    .replace(NOT_SLUG, "-")
    .trim('-')

/**
 * The verdict on the typed form. [fields] is set only when every field is usable and no other song has
 * the same title and artist, so `Agregar` or `Guardar` is enabled exactly then.
 */
data class SongFormCheck(
    val titleProblem: SongTextProblem?,
    val artistProblem: SongTextProblem?,
    val keyInvalid: Boolean,
    val duplicate: Boolean,
    val fields: SongFields?,
) {
    val canSubmit: Boolean
        get() = fields != null
}

/**
 * Checks the typed [title], [artist] and [key] against [catalog]. [editing] is the song being edited,
 * which does not count as a duplicate of itself (C4: same title slug and same artist slug).
 */
fun checkSongForm(title: String, artist: String, key: String, catalog: List<Song>, editing: SongId?): SongFormCheck {
    val titleProblem = songTextProblem(title)
    val artistProblem = songTextProblem(artist)
    val parsedKey = parseKeyInput(key)
    val duplicate = titleProblem == null && artistProblem == null && catalog.any {
        it.id != editing && songSlug(it.title) == songSlug(title) && songSlug(it.artist) == songSlug(artist)
    }
    val usable = titleProblem == null && artistProblem == null && !duplicate
    val fields = if (usable && parsedKey != null) {
        SongFields(normalizeSongText(title), normalizeSongText(artist), parsedKey)
    } else {
        null
    }
    return SongFormCheck(titleProblem, artistProblem, parsedKey == null, duplicate, fields)
}
