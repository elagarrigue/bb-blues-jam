package com.bbbjam.di

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.setlist.AddSongOutcome
import com.bbbjam.core.data.setlist.AssignSlotOutcome
import com.bbbjam.core.data.setlist.ClearSlotOutcome
import com.bbbjam.core.data.setlist.CreateJamOutcome
import com.bbbjam.core.data.setlist.DeleteJamOutcome
import com.bbbjam.core.data.setlist.ExtraParticipantOutcome
import com.bbbjam.core.data.setlist.JamCreator
import com.bbbjam.core.data.setlist.MoveSongOutcome
import com.bbbjam.core.data.setlist.PublishOutcome
import com.bbbjam.core.data.setlist.RemoveSongOutcome
import com.bbbjam.core.data.setlist.SetJamStatusOutcome
import com.bbbjam.core.data.setlist.SetKeyOutcome
import com.bbbjam.core.data.setlist.SetSlotCountOutcome
import com.bbbjam.core.data.setlist.SetlistJamEditRepository
import com.bbbjam.core.data.setlist.SetlistJamStatusRepository
import com.bbbjam.core.data.setlist.SetlistPublishRepository
import com.bbbjam.core.data.setlist.SetlistRepository
import com.bbbjam.core.data.setlist.SetlistSongAdder
import com.bbbjam.core.data.setlist.SetlistSongMover
import com.bbbjam.core.data.setlist.SetlistSongRemover
import com.bbbjam.core.data.setlist.UpdateJamOutcome
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.MusicianName
import com.bbbjam.core.model.SlotPosition
import com.bbbjam.core.model.SongId
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class AppModuleBindingsTest {
    private val date = LocalDate.of(2026, 10, 31)
    private val songId = SongId("red-house")

    @Test
    fun `app module binds the narrow status and publish contracts to the same SetlistRepository instance`() {
        val repository = FakeSetlistRepository()
        val application = koinApplication {
            allowOverride(true)
            modules(appModule, module { single<SetlistRepository> { repository } })
        }
        try {
            assertSame(repository, application.koin.get<SetlistJamStatusRepository>())
            assertSame(repository, application.koin.get<SetlistPublishRepository>())
            assertSame(repository, application.koin.get<SetlistJamEditRepository>())
        } finally {
            application.close()
        }
    }

    @Test
    fun `narrow write contracts delegate to the same SetlistRepository with outcomes unchanged`() {
        val repository = FakeSetlistRepository()
        val application = koinApplication {
            allowOverride(true)
            modules(appModule, module { single<SetlistRepository> { repository } })
        }
        try {
            val key = Key("C")
            val time = LocalTime.of(21, 30)
            runBlocking {
                val added = application.koin.get<SetlistSongAdder>().addSong(date, songId, key)
                val removed = application.koin.get<SetlistSongRemover>().removeSong(date, songId)
                val moved = application.koin.get<SetlistSongMover>().moveSong(date, songId, 4)
                val created = application.koin.get<JamCreator>().createJam(date, time, "La Macanuda")
                assertSame(repository.addOutcome, added)
                assertSame(repository.removeOutcome, removed)
                assertSame(repository.moveOutcome, moved)
                assertSame(repository.createOutcome, created)
            }
            assertEquals(listOf<List<Any?>>(listOf(date, songId, key)), repository.addCalls)
            assertEquals(listOf<List<Any?>>(listOf(date, songId)), repository.removeCalls)
            assertEquals(listOf<List<Any?>>(listOf(date, songId, 4)), repository.moveCalls)
            assertEquals(listOf<List<Any?>>(listOf(date, time, "La Macanuda")), repository.createCalls)
        } finally {
            application.close()
        }
    }
}

internal class FakeSetlistRepository : SetlistRepository {
    val addOutcome: AddSongOutcome = AddSongOutcome.Added(7)
    val removeOutcome: RemoveSongOutcome = RemoveSongOutcome.NotRemoved(WriteOutcome.Rejected("song_not_in_setlist"))
    val moveOutcome: MoveSongOutcome = MoveSongOutcome.NotMoved(WriteOutcome.Rejected("position_changed"))
    val createOutcome: CreateJamOutcome = CreateJamOutcome.Created(
        LocalDate.of(2026, 10, 31),
        LocalTime.of(21, 30),
        "x",
    )
    val addCalls = mutableListOf<List<Any?>>()
    val removeCalls = mutableListOf<List<Any?>>()
    val moveCalls = mutableListOf<List<Any?>>()
    val createCalls = mutableListOf<List<Any?>>()

    override suspend fun addSong(jamDate: LocalDate, songId: SongId, key: Key): AddSongOutcome {
        addCalls += listOf(jamDate, songId, key)
        return addOutcome
    }

    override fun observeAdds() = emptyFlow<List<com.bbbjam.core.data.setlist.SetlistAdd>>()

    override suspend fun removeSong(jamDate: LocalDate, songId: SongId): RemoveSongOutcome {
        removeCalls += listOf(jamDate, songId)
        return removeOutcome
    }

    override fun observeRemoves() = emptyFlow<List<com.bbbjam.core.data.setlist.SetlistRemove>>()

    override suspend fun setKey(jamDate: LocalDate, songId: SongId, key: Key): SetKeyOutcome = SetKeyOutcome.KeySet

    override fun observeKeyChanges() = emptyFlow<List<com.bbbjam.core.data.setlist.KeyChange>>()

    override suspend fun setSlotCount(
        jamDate: LocalDate,
        songId: SongId,
        instrument: Instrument,
        count: Int,
    ): SetSlotCountOutcome = SetSlotCountOutcome.SlotCountSet

    override fun observeLineupChanges() = emptyFlow<List<com.bbbjam.core.data.setlist.LineupChange>>()

    override suspend fun assignSlot(
        jamDate: LocalDate,
        songId: SongId,
        instrument: Instrument,
        ordinal: SlotPosition,
        musicianName: MusicianName,
    ): AssignSlotOutcome = AssignSlotOutcome.Assigned

    override fun observeAssignments() = emptyFlow<List<com.bbbjam.core.data.setlist.Assignment>>()

    override fun dismiss(id: Long) = Unit

    override suspend fun clearSlot(
        jamDate: LocalDate,
        songId: SongId,
        instrument: Instrument,
        ordinal: SlotPosition,
        expectedName: String,
    ): ClearSlotOutcome = ClearSlotOutcome.Cleared

    override fun observeSlotClears() = emptyFlow<List<com.bbbjam.core.data.setlist.SlotClear>>()

    override suspend fun moveSong(jamDate: LocalDate, songId: SongId, toPosition: Int): MoveSongOutcome {
        moveCalls += listOf(jamDate, songId, toPosition)
        return moveOutcome
    }

    override fun observeMoves() = emptyFlow<List<com.bbbjam.core.data.setlist.SetlistMove>>()

    override suspend fun publishSetlist(jamDate: LocalDate): PublishOutcome =
        PublishOutcome.Published(alreadyPublished = false)

    override fun observePublishes() = emptyFlow<List<com.bbbjam.core.data.setlist.SetlistPublish>>()

    override fun dismissPublish(id: Long) = Unit

    override suspend fun createJam(date: LocalDate, startTime: LocalTime, venue: String): CreateJamOutcome {
        createCalls += listOf(date, startTime, venue)
        return createOutcome
    }

    override suspend fun setJamStatus(jamDate: LocalDate, status: JamStatus): SetJamStatusOutcome =
        SetJamStatusOutcome.StatusSet(status, changed = true)

    override fun observeJamStatusChanges() = emptyFlow<List<com.bbbjam.core.data.setlist.JamStatusChange>>()

    override fun dismissJamStatusChange(id: Long) = Unit

    override suspend fun updateJam(
        jamDate: LocalDate,
        newDate: LocalDate,
        startTime: LocalTime,
        venue: String,
    ): UpdateJamOutcome = UpdateJamOutcome.Updated(newDate, startTime, venue, changed = true)

    override suspend fun deleteJam(jamDate: LocalDate, expectedSongs: Int): DeleteJamOutcome =
        DeleteJamOutcome.Deleted(expectedSongs)

    override fun observeJamEdits() = emptyFlow<List<com.bbbjam.core.data.setlist.JamEdit>>()

    override fun dismissJamEdit(id: Long) = Unit

    override suspend fun addExtraParticipant(
        jamDate: LocalDate,
        songId: SongId,
        name: String,
        instrument: String,
    ): ExtraParticipantOutcome = ExtraParticipantOutcome.Changed

    override suspend fun removeExtraParticipant(
        jamDate: LocalDate,
        songId: SongId,
        ordinal: Int,
        expectedName: String,
        expectedInstrument: String,
    ): ExtraParticipantOutcome = ExtraParticipantOutcome.NotChanged(WriteOutcome.Rejected("extra_changed"))

    override fun observeExtraParticipantChanges() =
        emptyFlow<List<com.bbbjam.core.data.setlist.ExtraParticipantChange>>()
}
