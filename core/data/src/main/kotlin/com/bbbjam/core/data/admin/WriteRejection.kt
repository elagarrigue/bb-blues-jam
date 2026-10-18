package com.bbbjam.core.data.admin

/**
 * The server error codes the client maps to copy, as a typed view of [WriteOutcome.Rejected.code]
 * (`typed-write-rejections`). [fromCode] is the one place the wire string becomes a value; a code
 * that is not listed (including every `invalid_*` the server may add) is [Unknown] and the raw code
 * stays on the outcome for logs. The codes that map to other outcomes (`invalid_passphrase`,
 * `rate_limited`, ...) are not entries.
 */
enum class WriteRejection(val code: String) {
    SONG_ALREADY_IN_SETLIST("song_already_in_setlist"),
    SONG_NOT_IN_SETLIST("song_not_in_setlist"),
    DUPLICATE_SONG("duplicate_song"),
    UNKNOWN_SONG("unknown_song"),
    UNKNOWN_JAM("unknown_jam"),
    JAM_NOT_EDITABLE("jam_not_editable"),
    DUPLICATE_DATE("duplicate_date"),
    SLOT_FILLED("slot_filled"),
    INSTRUMENT_NOT_EXPANDABLE("instrument_not_expandable"),
    INVALID_NAME("invalid_name"),
    SLOT_TAKEN("slot_taken"),
    SLOT_NOT_IN_LINEUP("slot_not_in_lineup"),
    SLOT_EMPTY("slot_empty"),
    SLOT_CHANGED("slot_changed"),
    UNORDERED_SETLIST("unordered_setlist"),
    JAM_EXISTS("jam_exists"),
    TAB_EXISTS("tab_exists"),
    PAST_DATE("past_date"),
    SETLIST_CHANGED("setlist_changed"),
    EMPTY_SETLIST("empty_setlist"),
    INVALID_SONG("invalid_song"),
    INVALID_KEY("invalid_key"),
    INVALID_DATE("invalid_date"),
    INVALID_TIME("invalid_time"),
    INVALID_VENUE("invalid_venue"),

    /** Any code not listed above; never matched by [fromCode] on its own empty [code]. */
    Unknown(""),
    ;

    companion object {
        private val byCode: Map<String, WriteRejection> =
            entries.filter { it != Unknown }.associateBy { it.code }

        /** The entry for a wire [code], or [Unknown]. */
        fun fromCode(code: String): WriteRejection = byCode[code] ?: Unknown
    }
}
