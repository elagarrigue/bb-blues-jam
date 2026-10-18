package com.bbbjam.core.assistant

import com.bbbjam.core.model.Song
import com.bbbjam.core.model.SongId

/**
 * Turns the model's [ProposalDto] into a catalog-only [AssistantReply.Proposal] (D-14, D-08). Pure.
 * Title, artist and key always come from the catalog, never from the model. An id that is malformed,
 * not in the catalog, or already used keeps nothing and is listed in `rejectedSongIds` (the first
 * occurrence of a repeated id is kept).
 */
internal object ProposalValidator {
    fun validate(dto: ProposalDto, catalog: List<Song>): AssistantReply.Proposal {
        val accumulator = Accumulator(catalog)
        val accepted = dto.songs.mapNotNull { accumulator.offer(it) }
        return AssistantReply.Proposal(
            songs = accepted,
            message = dto.message.trim(),
            questions = dto.questions.map { it.trim() }.filter { it.isNotEmpty() },
            rejectedSongIds = accumulator.rejected,
        )
    }

    /** The validated [proposal] as the DTO shape the model produced, for the next turn's history. */
    fun toDto(proposal: AssistantReply.Proposal): ProposalDto = ProposalDto(
        songs = proposal.songs.map {
            ProposedSongDto(it.songId.value, it.title, it.artist, it.key.value, it.reason)
        },
        message = proposal.message,
        questions = proposal.questions,
        jam = proposal.jam?.let { JamProposalDto(it.date.toString(), it.startTime.toString(), it.venue) },
    )

    /**
     * The per-song step of the validation, shared by the final answer and the streamed songs so the two
     * cannot drift: trimming, id parsing, catalog lookup, the first occurrence of a repeated id wins, and
     * title, artist and key from the catalog. [rejected] lists the trimmed ids of what was not accepted.
     */
    class Accumulator(catalog: List<Song>) {
        private val byId = catalog.associateBy { it.id }
        private val used = HashSet<SongId>()
        private val rejectedIds = ArrayList<String>()

        val rejected: List<String>
            get() = rejectedIds

        /** The accepted song, or null (and the id is recorded as rejected). */
        fun offer(proposed: ProposedSongDto): ProposedSong? {
            val raw = proposed.songId.trim()
            val song = SongId.parseOrNull(raw)?.let { byId[it] }
            if (song != null && used.add(song.id)) {
                return ProposedSong(song.id, song.title, song.artist, song.defaultKey, proposed.reason.trim())
            }
            rejectedIds += raw
            return null
        }
    }
}
