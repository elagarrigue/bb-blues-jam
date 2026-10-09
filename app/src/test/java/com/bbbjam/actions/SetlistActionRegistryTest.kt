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
import com.bbbjam.di.appModule
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class SetlistActionRegistryTest {
    private val date = LocalDate.of(2026, 10, 31)
    private val songId = SongId("red-house")
    private val key = Key("C")
    private val instrument = Instrument.GUITAR
    private val ordinal = SlotPosition(2)
    private val musician = requireNotNull(MusicianName.parseOrNull("Ana"))

    @Test
    fun `inventory contains every mutation once and dispatch delegates exact inputs and outcomes`() = runBlocking {
        val repository = RecordingSetlistRepository()
        val registry = SetlistActionRegistry(repository)
        val actions = listOf(
            SetlistAction.AddSong(date, songId, key),
            SetlistAction.RemoveSong(date, songId),
            SetlistAction.SetKey(date, songId, key),
            SetlistAction.SetSlotCount(date, songId, instrument, 2),
            SetlistAction.AssignSlot(date, songId, instrument, ordinal, musician),
            SetlistAction.ClearSlot(date, songId, instrument, ordinal, musician.value),
            SetlistAction.AddExtraParticipant(date, songId, "Ada", "saxo"),
            SetlistAction.RemoveExtraParticipant(date, songId, 3, "Ada", "saxo"),
            SetlistAction.MoveSong(date, songId, 4),
            SetlistAction.PublishSetlist(date),
        )
        assertEquals(10, SetlistAction.inventory.size)
        assertEquals(10, SetlistAction.inventory.map(SetlistAction::mutation).toSet().size)
        assertEquals(SetlistMutation.entries.toSet(), registry.registeredMutations)
        assertEquals(actions.map(SetlistAction::mutation), SetlistAction.inventory.map(SetlistAction::mutation))

        val results = mutableListOf<SetlistActionResult>()
        actions.forEach { results += registry.dispatch(it) }
        assertEquals(
            listOf(
                SetlistActionResult.SongAdded(AddSongOutcome.Added(7)),
                SetlistActionResult.SongRemoved(RemoveSongOutcome.Removed),
                SetlistActionResult.KeySet(SetKeyOutcome.KeySet),
                SetlistActionResult.SlotCountSet(SetSlotCountOutcome.SlotCountSet),
                SetlistActionResult.SlotAssigned(AssignSlotOutcome.Assigned),
                SetlistActionResult.SlotCleared(ClearSlotOutcome.Cleared),
                SetlistActionResult.ExtraParticipantAdded(ExtraParticipantOutcome.Changed),
                SetlistActionResult.ExtraParticipantRemoved(
                    ExtraParticipantOutcome.NotChanged(
                        com.bbbjam.core.data.admin.WriteOutcome.Rejected("extra_changed"),
                    ),
                ),
                SetlistActionResult.SongMoved(MoveSongOutcome.Moved),
                SetlistActionResult.SetlistPublished(PublishOutcome.Published(alreadyPublished = false)),
            ),
            results,
        )
        assertEquals(
            listOf(
                RecordedCall(SetlistMutation.ADD_SONG, listOf(date, songId, key)),
                RecordedCall(SetlistMutation.REMOVE_SONG, listOf(date, songId)),
                RecordedCall(SetlistMutation.SET_KEY, listOf(date, songId, key)),
                RecordedCall(SetlistMutation.SET_SLOT_COUNT, listOf(date, songId, instrument, 2)),
                RecordedCall(SetlistMutation.ASSIGN_SLOT, listOf(date, songId, instrument, ordinal, musician)),
                RecordedCall(SetlistMutation.CLEAR_SLOT, listOf(date, songId, instrument, ordinal, "Ana")),
                RecordedCall(SetlistMutation.ADD_EXTRA_PARTICIPANT, listOf(date, songId, "Ada", "saxo")),
                RecordedCall(SetlistMutation.REMOVE_EXTRA_PARTICIPANT, listOf(date, songId, 3, "Ada", "saxo")),
                RecordedCall(SetlistMutation.MOVE_SONG, listOf(date, songId, 4)),
                RecordedCall(SetlistMutation.PUBLISH_SETLIST, listOf(date)),
            ),
            repository.calls,
        )
    }

    @Test
    fun `app module resolves a singleton registry backed by its SetlistRepository binding`() {
        val repository = RecordingSetlistRepository()
        val application = koinApplication {
            allowOverride(true)
            modules(
                appModule,
                module { single<SetlistRepository> { repository } },
            )
        }
        try {
            val first = application.koin.get<SetlistActionRegistry>()
            val second = application.koin.get<SetlistActionRegistry>()
            assertSame(first, second)
            assertSame(repository, application.koin.get<SetlistRepository>())
            runBlocking { first.dispatch(SetlistAction.RemoveSong(date, songId)) }
            assertEquals(RecordedCall(SetlistMutation.REMOVE_SONG, listOf(date, songId)), repository.calls.single())
        } finally {
            application.close()
        }
    }
}

private data class RecordedCall(val mutation: SetlistMutation, val arguments: List<Any?>)

private class RecordingSetlistRepository : SetlistRepository {
    val calls = mutableListOf<RecordedCall>()

    private fun record(mutation: SetlistMutation, vararg args: Any?) {
        calls += RecordedCall(mutation, args.toList())
    }

    override suspend fun addSong(jamDate: LocalDate, songId: SongId, key: Key): AddSongOutcome {
        record(SetlistMutation.ADD_SONG, jamDate, songId, key)
        return AddSongOutcome.Added(7)
    }

    override fun observeAdds() = emptyFlow<List<com.bbbjam.core.data.setlist.SetlistAdd>>()

    override suspend fun removeSong(jamDate: LocalDate, songId: SongId): RemoveSongOutcome {
        record(SetlistMutation.REMOVE_SONG, jamDate, songId)
        return RemoveSongOutcome.Removed
    }

    override fun observeRemoves() = emptyFlow<List<com.bbbjam.core.data.setlist.SetlistRemove>>()

    override suspend fun setKey(jamDate: LocalDate, songId: SongId, key: Key): SetKeyOutcome {
        record(SetlistMutation.SET_KEY, jamDate, songId, key)
        return SetKeyOutcome.KeySet
    }

    override fun observeKeyChanges() = emptyFlow<List<com.bbbjam.core.data.setlist.KeyChange>>()

    override suspend fun setSlotCount(
        jamDate: LocalDate,
        songId: SongId,
        instrument: Instrument,
        count: Int,
    ): SetSlotCountOutcome {
        record(SetlistMutation.SET_SLOT_COUNT, jamDate, songId, instrument, count)
        return SetSlotCountOutcome.SlotCountSet
    }

    override fun observeLineupChanges() = emptyFlow<List<com.bbbjam.core.data.setlist.LineupChange>>()

    override suspend fun assignSlot(
        jamDate: LocalDate,
        songId: SongId,
        instrument: Instrument,
        ordinal: SlotPosition,
        musicianName: MusicianName,
    ): AssignSlotOutcome {
        record(SetlistMutation.ASSIGN_SLOT, jamDate, songId, instrument, ordinal, musicianName)
        return AssignSlotOutcome.Assigned
    }

    override fun observeAssignments() = emptyFlow<List<com.bbbjam.core.data.setlist.Assignment>>()

    override fun dismiss(id: Long) = Unit

    override suspend fun clearSlot(
        jamDate: LocalDate,
        songId: SongId,
        instrument: Instrument,
        ordinal: SlotPosition,
        expectedName: String,
    ): ClearSlotOutcome {
        record(SetlistMutation.CLEAR_SLOT, jamDate, songId, instrument, ordinal, expectedName)
        return ClearSlotOutcome.Cleared
    }

    override fun observeSlotClears() = emptyFlow<List<com.bbbjam.core.data.setlist.SlotClear>>()

    override suspend fun moveSong(jamDate: LocalDate, songId: SongId, toPosition: Int): MoveSongOutcome {
        record(SetlistMutation.MOVE_SONG, jamDate, songId, toPosition)
        return MoveSongOutcome.Moved
    }

    override fun observeMoves() = emptyFlow<List<com.bbbjam.core.data.setlist.SetlistMove>>()

    override suspend fun publishSetlist(jamDate: LocalDate): PublishOutcome {
        record(SetlistMutation.PUBLISH_SETLIST, jamDate)
        return PublishOutcome.Published(alreadyPublished = false)
    }

    override fun observePublishes() = emptyFlow<List<com.bbbjam.core.data.setlist.SetlistPublish>>()

    override fun dismissPublish(id: Long) = Unit

    override suspend fun addExtraParticipant(
        jamDate: LocalDate,
        songId: SongId,
        name: String,
        instrument: String,
    ): ExtraParticipantOutcome {
        record(SetlistMutation.ADD_EXTRA_PARTICIPANT, jamDate, songId, name, instrument)
        return ExtraParticipantOutcome.Changed
    }

    override suspend fun removeExtraParticipant(
        jamDate: LocalDate,
        songId: SongId,
        ordinal: Int,
        expectedName: String,
        expectedInstrument: String,
    ): ExtraParticipantOutcome {
        record(SetlistMutation.REMOVE_EXTRA_PARTICIPANT, jamDate, songId, ordinal, expectedName, expectedInstrument)
        return ExtraParticipantOutcome.NotChanged(com.bbbjam.core.data.admin.WriteOutcome.Rejected("extra_changed"))
    }

    override fun observeExtraParticipantChanges() =
        emptyFlow<List<com.bbbjam.core.data.setlist.ExtraParticipantChange>>()
}
