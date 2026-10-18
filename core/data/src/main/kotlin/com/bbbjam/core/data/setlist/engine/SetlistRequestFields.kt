package com.bbbjam.core.data.setlist.engine

/**
 * The request field names every setlist write shares (`docs/apps-script-api.md`): the jam's date,
 * the song's id and, for the writes that carry one, the key the admin set (D-08). Fields only one
 * write sends stay with that write.
 */
internal object SetlistRequestFields {
    const val DATE = "date"
    const val SONG_ID = "songId"
    const val KEY = "key"
}
