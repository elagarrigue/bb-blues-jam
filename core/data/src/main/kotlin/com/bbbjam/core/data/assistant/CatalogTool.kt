package com.bbbjam.core.data.assistant

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import com.bbbjam.core.data.catalog.CatalogRepository
import com.bbbjam.core.model.Song
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

/**
 * Read-only tool `searchCatalog` (D-24): lists the cached catalog, optionally filtered. It holds a
 * [CatalogRepository] and nothing else, so no write is reachable from it. One compact line per song,
 * in Sheet order: `id | title | artist | key | tempo` (tempo omitted when unknown).
 */
internal class CatalogTool(private val catalog: CatalogRepository) :
    SimpleTool<CatalogTool.Args>(
        argsType = typeToken<Args>(),
        name = NAME,
        description = "Lists songs of the band's catalog, the only songs that may be proposed. " +
            "Matches the query against title and artist, ignoring case and accents. " +
            "An empty query lists the whole catalog.",
    ) {

    @Serializable
    data class Args(
        @property:LLMDescription("Text to look for in the title or artist. Empty for the whole catalog.")
        val query: String,
    )

    override suspend fun execute(args: Args): String = render(catalog.observeCatalog().first().songs, args.query)

    companion object {
        const val NAME = "searchCatalog"

        const val NOT_LOADED = "The catalog is not loaded yet."

        /** The tool's answer for [songs] filtered by [query]; pure, so tests need no Koog runtime. */
        fun render(songs: List<Song>, query: String): String {
            if (songs.isEmpty()) return NOT_LOADED
            val needle = ToolText.normalize(query)
            val matches = if (needle.isEmpty()) {
                songs
            } else {
                songs.filter { needle in ToolText.normalize(it.title) || needle in ToolText.normalize(it.artist) }
            }
            val header = if (needle.isEmpty()) {
                "Catalog: ${matches.size} songs (id | title | artist | key | tempo)"
            } else {
                "Catalog: ${matches.size} songs match \"${query.trim()}\" (id | title | artist | key | tempo)"
            }
            return (listOf(header) + matches.map(::line)).joinToString("\n")
        }

        private fun line(song: Song): String = buildList {
            add(song.id.value)
            add(song.title)
            add(song.artist)
            add(song.defaultKey.value)
            song.tempo?.let { add(it.name.lowercase(Locale.ROOT)) }
        }.joinToString(" | ")
    }
}

/** Text helpers shared by the tools. */
internal object ToolText {
    private val MARKS = Regex("\\p{M}+")

    /** [text] lowercased, without accents, trimmed: the comparison form for every tool filter. */
    fun normalize(text: String): String =
        MARKS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase(Locale.ROOT).trim()
}
