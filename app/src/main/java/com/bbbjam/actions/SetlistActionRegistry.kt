package com.bbbjam.actions

import com.bbbjam.core.data.setlist.AddSongOutcome
import com.bbbjam.core.data.setlist.AssignSlotOutcome
import com.bbbjam.core.data.setlist.ClearSlotOutcome
import com.bbbjam.core.data.setlist.ExtraParticipantOutcome
import com.bbbjam.core.data.setlist.MoveSongOutcome
import com.bbbjam.core.data.setlist.PublishOutcome
import com.bbbjam.core.data.setlist.RemoveSongOutcome
import com.bbbjam.core.data.setlist.SetKeyOutcome
import com.bbbjam.core.data.setlist.SetSlotCountOutcome
import com.bbbjam.core.data.setlist.SetlistRepository
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.MusicianName
import com.bbbjam.core.model.SlotPosition
import com.bbbjam.core.model.SongId
import java.time.LocalDate

enum class SetlistMutation {
    ADD_SONG,
    REMOVE_SONG,
    SET_KEY,
    SET_SLOT_COUNT,
    ASSIGN_SLOT,
    CLEAR_SLOT,
    ADD_EXTRA_PARTICIPANT,
    REMOVE_EXTRA_PARTICIPANT,
    MOVE_SONG,
    PUBLISH_SETLIST,
}

sealed interface SetlistAction {
    val mutation: SetlistMutation

    data class AddSong(val jamDate: LocalDate, val songId: SongId, val key: Key) : SetlistAction {
        override val mutation = SetlistMutation.ADD_SONG
    }

    data class RemoveSong(val jamDate: LocalDate, val songId: SongId) : SetlistAction {
        override val mutation = SetlistMutation.REMOVE_SONG
    }

    data class SetKey(val jamDate: LocalDate, val songId: SongId, val key: Key) : SetlistAction {
        override val mutation = SetlistMutation.SET_KEY
    }

    data class SetSlotCount(val jamDate: LocalDate, val songId: SongId, val instrument: Instrument, val count: Int) :
        SetlistAction {
        override val mutation = SetlistMutation.SET_SLOT_COUNT
    }

    data class AssignSlot(
        val jamDate: LocalDate,
        val songId: SongId,
        val instrument: Instrument,
        val ordinal: SlotPosition,
        val musicianName: MusicianName,
    ) : SetlistAction {
        override val mutation = SetlistMutation.ASSIGN_SLOT
    }

    data class ClearSlot(
        val jamDate: LocalDate,
        val songId: SongId,
        val instrument: Instrument,
        val ordinal: SlotPosition,
        val expectedName: String,
    ) : SetlistAction {
        override val mutation = SetlistMutation.CLEAR_SLOT
    }

    data class AddExtraParticipant(
        val jamDate: LocalDate,
        val songId: SongId,
        val name: String,
        val instrument: String,
    ) : SetlistAction {
        override val mutation = SetlistMutation.ADD_EXTRA_PARTICIPANT
    }

    data class RemoveExtraParticipant(
        val jamDate: LocalDate,
        val songId: SongId,
        val ordinal: Int,
        val expectedName: String,
        val expectedInstrument: String,
    ) : SetlistAction {
        override val mutation = SetlistMutation.REMOVE_EXTRA_PARTICIPANT
    }

    data class MoveSong(val jamDate: LocalDate, val songId: SongId, val toPosition: Int) : SetlistAction {
        override val mutation = SetlistMutation.MOVE_SONG
    }

    data class PublishSetlist(val jamDate: LocalDate) : SetlistAction {
        override val mutation = SetlistMutation.PUBLISH_SETLIST
    }

    companion object {
        private val DATE = LocalDate.of(2026, 10, 31)
        private val SONG = SongId("red-house")
        private val KEY = Key("C")
        private val SLOT = SlotPosition(1)
        private val NAME = requireNotNull(MusicianName.parseOrNull("Ana"))

        val inventory: List<SetlistAction> = listOf(
            AddSong(DATE, SONG, KEY),
            RemoveSong(DATE, SONG),
            SetKey(DATE, SONG, KEY),
            SetSlotCount(DATE, SONG, Instrument.GUITAR, 2),
            AssignSlot(DATE, SONG, Instrument.GUITAR, SLOT, NAME),
            ClearSlot(DATE, SONG, Instrument.GUITAR, SLOT, NAME.value),
            AddExtraParticipant(DATE, SONG, NAME.value, "saxo"),
            RemoveExtraParticipant(DATE, SONG, 1, NAME.value, "saxo"),
            MoveSong(DATE, SONG, 2),
            PublishSetlist(DATE),
        )
    }
}

sealed interface SetlistActionResult {
    data class SongAdded(val outcome: AddSongOutcome) : SetlistActionResult
    data class SongRemoved(val outcome: RemoveSongOutcome) : SetlistActionResult
    data class KeySet(val outcome: SetKeyOutcome) : SetlistActionResult
    data class SlotCountSet(val outcome: SetSlotCountOutcome) : SetlistActionResult
    data class SlotAssigned(val outcome: AssignSlotOutcome) : SetlistActionResult
    data class SlotCleared(val outcome: ClearSlotOutcome) : SetlistActionResult
    data class ExtraParticipantAdded(val outcome: ExtraParticipantOutcome) : SetlistActionResult
    data class ExtraParticipantRemoved(val outcome: ExtraParticipantOutcome) : SetlistActionResult
    data class SongMoved(val outcome: MoveSongOutcome) : SetlistActionResult
    data class SetlistPublished(val outcome: PublishOutcome) : SetlistActionResult
}

class SetlistActionRegistry(private val repository: SetlistRepository) {
    val registeredMutations: Set<SetlistMutation> = SetlistAction.inventory.map { it.mutation }.toSet()

    suspend fun dispatch(action: SetlistAction): SetlistActionResult = when (action) {
        is SetlistAction.AddSong -> SetlistActionResult.SongAdded(
            repository.addSong(action.jamDate, action.songId, action.key),
        )

        is SetlistAction.RemoveSong -> SetlistActionResult.SongRemoved(
            repository.removeSong(action.jamDate, action.songId),
        )

        is SetlistAction.SetKey -> SetlistActionResult.KeySet(
            repository.setKey(action.jamDate, action.songId, action.key),
        )

        is SetlistAction.SetSlotCount -> SetlistActionResult.SlotCountSet(
            repository.setSlotCount(action.jamDate, action.songId, action.instrument, action.count),
        )

        is SetlistAction.AssignSlot -> SetlistActionResult.SlotAssigned(
            repository.assignSlot(
                action.jamDate,
                action.songId,
                action.instrument,
                action.ordinal,
                action.musicianName,
            ),
        )

        is SetlistAction.ClearSlot -> SetlistActionResult.SlotCleared(
            repository.clearSlot(action.jamDate, action.songId, action.instrument, action.ordinal, action.expectedName),
        )

        is SetlistAction.AddExtraParticipant -> SetlistActionResult.ExtraParticipantAdded(
            repository.addExtraParticipant(action.jamDate, action.songId, action.name, action.instrument),
        )

        is SetlistAction.RemoveExtraParticipant -> SetlistActionResult.ExtraParticipantRemoved(
            repository.removeExtraParticipant(
                action.jamDate,
                action.songId,
                action.ordinal,
                action.expectedName,
                action.expectedInstrument,
            ),
        )

        is SetlistAction.MoveSong -> SetlistActionResult.SongMoved(
            repository.moveSong(action.jamDate, action.songId, action.toPosition),
        )

        is SetlistAction.PublishSetlist -> SetlistActionResult.SetlistPublished(
            repository.publishSetlist(action.jamDate),
        )
    }
}
