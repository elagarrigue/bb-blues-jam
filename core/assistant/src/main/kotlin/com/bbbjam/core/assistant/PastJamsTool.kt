package com.bbbjam.core.assistant

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.Setlist
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

/**
 * Read-only tool `pastJams` (D-24): past jams, newest first, with who played each song. It holds a
 * [JamsRepository] and nothing else. Only past jams are listed (never the upcoming jam or a draft).
 * Per jam a `fecha YYYY-MM-DD` line, then per song
 * `id | title | artist | key | players: Name (instrument), ...` extras included.
 */
internal class PastJamsTool(private val jams: JamsRepository) :
    SimpleTool<PastJamsTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description = "Lists past jams, newest first: the songs played and who played each instrument. " +
            "With a musician name, only the songs that musician played. An empty name lists every jam.",
    ) {

    @Serializable
    data class Args(
        @property:LLMDescription("A musician's name, ignoring case and accents. Empty for everyone.")
        val musician: String,
    )

    override suspend fun execute(args: Args): String = render(jams.observeJams().first().past, args.musician)

    companion object {
        const val NAME = "pastJams"

        const val MAX_JAMS = 24

        const val NO_JAMS = "No past jams recorded."

        /** The tool's answer for [past] (newest first) filtered by [musician]; pure. */
        fun render(past: List<Jam>, musician: String): String {
            val needle = ToolText.normalize(musician)
            val blocks = past.mapNotNull { jam -> block(jam, needle) }.take(MAX_JAMS)
            return if (blocks.isEmpty()) NO_JAMS else blocks.joinToString("\n")
        }

        private fun block(jam: Jam, needle: String): String? {
            val songs = (jam.setlistForMusicians() as? Setlist.Available)?.songs.orEmpty()
            val shown = if (needle.isEmpty()) {
                songs
            } else {
                songs.filter { song -> players(song).any { (name, _) -> needle in ToolText.normalize(name) } }
            }
            return if (shown.isEmpty()) {
                null
            } else {
                (listOf("fecha ${jam.date}") + shown.map(::line)).joinToString("\n")
            }
        }

        private fun players(song: JamSong): List<Pair<String, String>> = song.lineup.slots.mapNotNull { slot ->
            slot.musicianName?.let { it to slot.instrument.name.lowercase(Locale.ROOT) }
        } + song.extraParticipants.map { it.name to it.instrument }

        private fun line(song: JamSong): String {
            val who = players(song).joinToString(", ") { (name, instrument) -> "$name ($instrument)" }
            return "${song.songId.value} | ${song.title} | ${song.artist} | ${song.key.value} | players: $who"
        }
    }
}
