package com.bbbjam.core.data.assistant

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
        val byId = catalog.associateBy { it.id }
        val used = HashSet<SongId>()
        val accepted = ArrayList<ProposedSong>()
        val rejected = ArrayList<String>()
        for (proposed in dto.songs) {
            val raw = proposed.songId.trim()
            val song = SongId.parseOrNull(raw)?.let { byId[it] }
            if (song != null && used.add(song.id)) {
                accepted += ProposedSong(song.id, song.title, song.artist, song.defaultKey, proposed.reason.trim())
            } else {
                rejected += raw
            }
        }
        return AssistantReply.Proposal(
            songs = accepted,
            message = dto.message.trim(),
            questions = dto.questions.map { it.trim() }.filter { it.isNotEmpty() },
            rejectedSongIds = rejected,
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
}
