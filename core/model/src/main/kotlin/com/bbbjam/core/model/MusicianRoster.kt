package com.bbbjam.core.model

import java.util.Locale

/**
 * The key two spellings of one musician share: whitespace collapsed, trimmed and lowercased. The
 * admin always uses distinct names (user decision of 9 November 2026), so equal keys are one person.
 * Not accent-insensitive.
 */
fun musicianMatchKey(name: String): String = collapseWhitespace(name).lowercase(Locale.ROOT)

/** True when [matchKey] (see [musicianMatchKey]) is a slot's musician or an extra participant of this song. */
fun JamSong.involves(matchKey: String): Boolean = participantNames().any { musicianMatchKey(it) == matchKey }

/** One distinct musician of a setlist: [name] as first written, [matchKey], and the songs they are in. */
data class RosterEntry(val name: String, val matchKey: String, val songCount: Int)

/**
 * The distinct musicians of these songs (assigned slots and extras), deduplicated by match key,
 * shown with the first spelling in position order, sorted by key. Open slots and placeholder cells
 * (no letter or digit, such as `-`) are not musicians. A song counts once for a name even when it
 * holds that name in a slot and in the extras.
 */
fun List<JamSong>.musicianRoster(): List<RosterEntry> {
    val firstSpelling = linkedMapOf<String, String>()
    val songCounts = mutableMapOf<String, Int>()
    sortedBy { it.position }.forEach { song ->
        val keysInSong = mutableSetOf<String>()
        song.participantNames().forEach { raw ->
            val name = collapseWhitespace(raw)
            val key = musicianMatchKey(name)
            if (name.any(Char::isLetterOrDigit)) {
                firstSpelling.getOrPut(key) { name }
                keysInSong += key
            }
        }
        keysInSong.forEach { songCounts.merge(it, 1, Int::plus) }
    }
    return firstSpelling.entries
        .sortedBy { it.key }
        .map { (key, name) -> RosterEntry(name, key, songCounts.getValue(key)) }
}

private fun JamSong.participantNames(): List<String> =
    lineup.slots.mapNotNull { it.musicianName } + extraParticipants.map { it.name }
