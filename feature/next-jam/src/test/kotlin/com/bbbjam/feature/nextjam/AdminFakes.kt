package com.bbbjam.feature.nextjam

import com.bbbjam.core.data.admin.AdminSession
import com.bbbjam.core.data.admin.LoginOutcome
import com.bbbjam.core.data.setlist.AddSongOutcome
import com.bbbjam.core.data.setlist.AssignSlotOutcome
import com.bbbjam.core.data.setlist.Assignment
import com.bbbjam.core.data.setlist.ClearSlotOutcome
import com.bbbjam.core.data.setlist.ExtraParticipantChange
import com.bbbjam.core.data.setlist.ExtraParticipantOutcome
import com.bbbjam.core.data.setlist.KeyChange
import com.bbbjam.core.data.setlist.LineupChange
import com.bbbjam.core.data.setlist.MoveSongOutcome
import com.bbbjam.core.data.setlist.PublishOutcome
import com.bbbjam.core.data.setlist.RemoveSongOutcome
import com.bbbjam.core.data.setlist.SetKeyOutcome
import com.bbbjam.core.data.setlist.SetSlotCountOutcome
import com.bbbjam.core.data.setlist.SetlistAdd
import com.bbbjam.core.data.setlist.SetlistMove
import com.bbbjam.core.data.setlist.SetlistPublish
import com.bbbjam.core.data.setlist.SetlistRemove
import com.bbbjam.core.data.setlist.SetlistRepository
import com.bbbjam.core.data.setlist.SlotClear
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.MusicianName
import com.bbbjam.core.model.SlotPosition
import com.bbbjam.core.model.SongId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** An admin flag the test sets; it never stores or sends anything. Musician (false) by default. */
class FakeAdminSession(isAdmin: Boolean = false) : AdminSession {
    val isAdmin = MutableStateFlow(isAdmin)

    override fun observeIsAdmin(): Flow<Boolean> = isAdmin

    override suspend fun logIn(passphrase: String): LoginOutcome = LoginOutcome.Unavailable

    override suspend fun logOut() {
        isAdmin.value = false
    }
}

/**
 * Records every add, remove and dismiss; [adds] and [removes] are what `observeAdds` and
 * `observeRemoves` emit, set by the test.
 */
class FakeSetlistRepository : SetlistRepository {
    data class AddCall(val jamDate: LocalDate, val songId: SongId, val key: Key)

    val adds = MutableStateFlow<List<SetlistAdd>>(emptyList())
    val addCalls = mutableListOf<AddCall>()
    val dismissed = mutableListOf<Long>()
    val removes = MutableStateFlow<List<SetlistRemove>>(emptyList())
    val removeCalls = mutableListOf<Pair<LocalDate, SongId>>()
    val keyChanges = MutableStateFlow<List<KeyChange>>(emptyList())
    val keyCalls = mutableListOf<AddCall>()
    val lineupChanges = MutableStateFlow<List<LineupChange>>(emptyList())
    val assignments = MutableStateFlow<List<Assignment>>(emptyList())
    val extraParticipantChanges = MutableStateFlow<List<ExtraParticipantChange>>(emptyList())
    data class ExtraAddCall(val date: LocalDate, val id: SongId, val name: String, val instrument: String)
    val extraAddCalls = mutableListOf<ExtraAddCall>()
    data class ExtraRemoveCall(
        val date: LocalDate,
        val id: SongId,
        val ordinal: Int,
        val name: String,
        val instrument: String,
    )
    val extraRemoveCalls = mutableListOf<ExtraRemoveCall>()
    val slotClears = MutableStateFlow<List<SlotClear>>(emptyList())
    val moves = MutableStateFlow<List<SetlistMove>>(emptyList())
    val publishes = MutableStateFlow<List<SetlistPublish>>(emptyList())
    val publishCalls = mutableListOf<LocalDate>()
    val publishResults = ArrayDeque<PublishOutcome>()
    var publishHold: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    val moveCalls = mutableListOf<Triple<LocalDate, SongId, Int>>()
    var moveHold: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    data class AssignmentCall(
        val date: LocalDate,
        val id: SongId,
        val instrument: Instrument,
        val ordinal: SlotPosition,
        val name: MusicianName,
    )
    val assignmentCalls = mutableListOf<AssignmentCall>()

    override suspend fun addSong(jamDate: LocalDate, songId: SongId, key: Key): AddSongOutcome {
        addCalls += AddCall(jamDate, songId, key)
        return AddSongOutcome.Added(addCalls.size)
    }

    override fun observeAdds(): Flow<List<SetlistAdd>> = adds

    override suspend fun removeSong(jamDate: LocalDate, songId: SongId): RemoveSongOutcome {
        removeCalls += jamDate to songId
        return RemoveSongOutcome.Removed
    }

    override fun observeRemoves(): Flow<List<SetlistRemove>> = removes

    override suspend fun setKey(jamDate: LocalDate, songId: SongId, key: Key): SetKeyOutcome {
        keyCalls += AddCall(jamDate, songId, key)
        return SetKeyOutcome.KeySet
    }

    override fun observeKeyChanges(): Flow<List<KeyChange>> = keyChanges

    data class LineupCall(val date: LocalDate, val id: SongId, val instrument: Instrument, val count: Int)
    val lineupCalls = mutableListOf<LineupCall>()
    var lineupHold: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override suspend fun setSlotCount(
        jamDate: LocalDate,
        songId: SongId,
        instrument: Instrument,
        count: Int,
    ): SetSlotCountOutcome {
        lineupCalls += LineupCall(jamDate, songId, instrument, count)
        lineupHold?.await()
        return SetSlotCountOutcome.SlotCountSet
    }

    override fun observeLineupChanges(): Flow<List<LineupChange>> = lineupChanges

    override suspend fun assignSlot(
        jamDate: LocalDate,
        songId: SongId,
        instrument: Instrument,
        ordinal: SlotPosition,
        musicianName: MusicianName,
    ): AssignSlotOutcome {
        assignmentCalls += AssignmentCall(jamDate, songId, instrument, ordinal, musicianName)
        return AssignSlotOutcome.Assigned
    }

    override fun observeAssignments(): Flow<List<Assignment>> = assignments

    override suspend fun addExtraParticipant(
        jamDate: LocalDate,
        songId: SongId,
        name: String,
        instrument: String,
    ): ExtraParticipantOutcome {
        extraAddCalls += ExtraAddCall(jamDate, songId, name, instrument)
        return ExtraParticipantOutcome.Changed
    }

    override suspend fun removeExtraParticipant(
        jamDate: LocalDate,
        songId: SongId,
        ordinal: Int,
        expectedName: String,
        expectedInstrument: String,
    ): ExtraParticipantOutcome {
        extraRemoveCalls += ExtraRemoveCall(jamDate, songId, ordinal, expectedName, expectedInstrument)
        return ExtraParticipantOutcome.Changed
    }

    override fun observeExtraParticipantChanges(): Flow<List<ExtraParticipantChange>> = extraParticipantChanges

    override suspend fun clearSlot(
        jamDate: LocalDate,
        songId: SongId,
        instrument: Instrument,
        ordinal: SlotPosition,
        expectedName: String,
    ): ClearSlotOutcome = ClearSlotOutcome.Cleared

    override fun observeSlotClears(): Flow<List<SlotClear>> = slotClears

    override suspend fun moveSong(jamDate: LocalDate, songId: SongId, toPosition: Int): MoveSongOutcome {
        moveCalls += Triple(jamDate, songId, toPosition)
        moveHold?.await()
        return MoveSongOutcome.Moved
    }

    override fun observeMoves(): Flow<List<SetlistMove>> = moves

    override suspend fun publishSetlist(jamDate: LocalDate): PublishOutcome {
        publishCalls += jamDate
        publishes.value = publishes.value + SetlistPublish(900, jamDate, SetlistPublish.State.Sending)
        publishHold?.await()
        publishes.value = publishes.value.filterNot { it.id == 900L }
        return publishResults.removeFirstOrNull() ?: PublishOutcome.Published(alreadyPublished = false)
    }

    override fun observePublishes(): Flow<List<SetlistPublish>> = publishes

    override fun dismissPublish(id: Long) {
        dismissed += id
        publishes.value = publishes.value.filterNot { it.id == id && it.state is SetlistPublish.State.Failed }
    }

    override fun dismiss(id: Long) {
        dismissed += id
    }
}
