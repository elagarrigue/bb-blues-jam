package com.bbbjam.feature.assistant

import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsRefreshOutcome
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.setlist.add.AddSongOutcome
import com.bbbjam.core.data.setlist.add.SetlistSongAdder
import com.bbbjam.core.data.setlist.jamcreate.CreateJamOutcome
import com.bbbjam.core.data.setlist.jamcreate.JamCreator
import com.bbbjam.core.data.setlist.move.MoveSongOutcome
import com.bbbjam.core.data.setlist.move.SetlistSongMover
import com.bbbjam.core.data.setlist.remove.RemoveSongOutcome
import com.bbbjam.core.data.setlist.remove.SetlistSongRemover
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SetlistProblem
import com.bbbjam.core.model.SongId
import com.bbbjam.feature.assistant.run.ProposalApplier
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

internal val JAM_DATE: LocalDate = LocalDate.of(2026, 10, 31)

internal data class AddCall(val date: LocalDate, val songId: SongId, val key: Key)

/** Every write of a run in call order across the fakes: `add s1`, `remove s2`, `move s3@1`, `create`. */
internal typealias CallLog = MutableList<String>

/** A [SetlistSongAdder] with scripted outcomes by song id (default: Added) and optional gates. */
internal class FakeSetlistSongAdder(val log: CallLog = mutableListOf()) : SetlistSongAdder {
    val calls = mutableListOf<AddCall>()
    val outcomes = mutableMapOf<SongId, AddSongOutcome>()
    val gates = mutableMapOf<SongId, CompletableDeferred<Unit>>()

    fun reject(n: Int, outcome: WriteOutcome) {
        outcomes[SongId("s$n")] = AddSongOutcome.NotAdded(outcome)
    }

    override suspend fun addSong(jamDate: LocalDate, songId: SongId, key: Key): AddSongOutcome {
        calls += AddCall(jamDate, songId, key)
        log += "add ${songId.value}"
        gates[songId]?.await()
        return outcomes[songId] ?: AddSongOutcome.Added(calls.size)
    }
}

internal data class RemoveCall(val date: LocalDate, val songId: SongId)

/** A [SetlistSongRemover] with scripted outcomes by song id (default: Removed) and optional gates. */
internal class FakeSetlistSongRemover(val log: CallLog = mutableListOf()) : SetlistSongRemover {
    val calls = mutableListOf<RemoveCall>()
    val outcomes = mutableMapOf<SongId, RemoveSongOutcome>()
    val gates = mutableMapOf<SongId, CompletableDeferred<Unit>>()

    fun reject(n: Int, outcome: WriteOutcome) {
        outcomes[SongId("s$n")] = RemoveSongOutcome.NotRemoved(outcome)
    }

    override suspend fun removeSong(jamDate: LocalDate, songId: SongId): RemoveSongOutcome {
        calls += RemoveCall(jamDate, songId)
        log += "remove ${songId.value}"
        gates[songId]?.await()
        return outcomes[songId] ?: RemoveSongOutcome.Removed
    }
}

internal data class MoveCall(val date: LocalDate, val songId: SongId, val toPosition: Int)

/** A [SetlistSongMover] with scripted outcomes by song id (default: Moved) and optional gates. */
internal class FakeSetlistSongMover(val log: CallLog = mutableListOf()) : SetlistSongMover {
    val calls = mutableListOf<MoveCall>()
    val outcomes = mutableMapOf<SongId, MoveSongOutcome>()
    val gates = mutableMapOf<SongId, CompletableDeferred<Unit>>()

    fun reject(n: Int, outcome: WriteOutcome) {
        outcomes[SongId("s$n")] = MoveSongOutcome.NotMoved(outcome)
    }

    override suspend fun moveSong(jamDate: LocalDate, songId: SongId, toPosition: Int): MoveSongOutcome {
        calls += MoveCall(jamDate, songId, toPosition)
        log += "move ${songId.value}@$toPosition"
        gates[songId]?.await()
        return outcomes[songId] ?: MoveSongOutcome.Moved
    }
}

internal fun jamSong(n: Int) = JamSong(n, SongId("s$n"), "Tema $n", "Artista $n", Key("Bm"), Lineup.default())

internal fun jam(status: JamStatus = JamStatus.DRAFT, setlist: Setlist = Setlist.Available(emptyList())) =
    Jam(JAM_DATE, LocalTime.of(21, 0), "Sala", status, setlist)

internal fun snapshot(upcoming: Jam? = jam()) =
    JamsSnapshot(upcoming, emptyList(), Freshness(Instant.EPOCH, null, false))

/** A list holding the songs `s<n>` in the order given, positions 1.. (unlike [listed], whose position is the id). */
internal fun listedOrder(vararg ids: Int) =
    Setlist.Available(ids.mapIndexed { index, n -> jamSong(n).copy(position = index + 1) })

internal fun listed(vararg ids: Int) = Setlist.Available(ids.map { jamSong(it) })

internal val unreadable = Setlist.Unavailable(SetlistProblem.MISSING_TAB)

/** A [JamsRepository] whose snapshot the test sets; null means "nothing emitted yet". */
internal class FakeJamsRepository(initial: JamsSnapshot? = snapshot()) : JamsRepository {
    private val state = MutableStateFlow(initial)
    var snapshot: JamsSnapshot?
        get() = state.value
        set(value) {
            state.value = value
        }

    override fun observeJams(): Flow<JamsSnapshot> = flow {
        state.collect { value -> if (value != null) emit(value) }
    }

    var refreshes = 0
        private set

    override suspend fun refresh(): JamsRefreshOutcome {
        refreshes++
        return JamsRefreshOutcome.Failed(DataFailure.Offline)
    }
}

/** Today in the tests: 2026-10-14, 17 days before [JAM_DATE]. */
internal val TODAY: LocalDate = LocalDate.of(2026, 10, 14)

internal fun testCalendar() =
    JamCalendar(Clock.fixed(Instant.parse("2026-10-14T15:00:00Z"), ZoneOffset.UTC), JamCalendar.BUENOS_AIRES)

internal data class CreateCall(val date: LocalDate, val time: LocalTime, val venue: String)

/** A [JamCreator] with a scripted outcome (default: Created as asked) and an optional gate. */
internal class FakeJamCreator(val log: CallLog = mutableListOf()) : JamCreator {
    val calls = mutableListOf<CreateCall>()
    var outcome: CreateJamOutcome? = null
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun createJam(date: LocalDate, startTime: LocalTime, venue: String): CreateJamOutcome {
        calls += CreateCall(date, startTime, venue)
        log += "create"
        gate?.await()
        return outcome ?: CreateJamOutcome.Created(date, startTime, venue)
    }
}

/** A [ProposalApplier] over the fakes; each write contract not given is a fresh fake. */
internal fun testApplier(
    adder: SetlistSongAdder = FakeSetlistSongAdder(),
    scope: kotlinx.coroutines.CoroutineScope,
    remover: SetlistSongRemover = FakeSetlistSongRemover(),
    mover: SetlistSongMover = FakeSetlistSongMover(),
    creator: JamCreator = FakeJamCreator(),
) = ProposalApplier(adder, remover, mover, creator, scope)
