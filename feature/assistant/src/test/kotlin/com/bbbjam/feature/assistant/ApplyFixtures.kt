package com.bbbjam.feature.assistant

import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsRefreshOutcome
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.setlist.AddSongOutcome
import com.bbbjam.core.data.setlist.CreateJamOutcome
import com.bbbjam.core.data.setlist.JamCreator
import com.bbbjam.core.data.setlist.SetlistSongAdder
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SetlistProblem
import com.bbbjam.core.model.SongId
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

/** A [SetlistSongAdder] with scripted outcomes by song id (default: Added) and optional gates. */
internal class FakeSetlistSongAdder : SetlistSongAdder {
    val calls = mutableListOf<AddCall>()
    val outcomes = mutableMapOf<SongId, AddSongOutcome>()
    val gates = mutableMapOf<SongId, CompletableDeferred<Unit>>()

    fun reject(n: Int, outcome: WriteOutcome) {
        outcomes[SongId("s$n")] = AddSongOutcome.NotAdded(outcome)
    }

    override suspend fun addSong(jamDate: LocalDate, songId: SongId, key: Key): AddSongOutcome {
        calls += AddCall(jamDate, songId, key)
        gates[songId]?.await()
        return outcomes[songId] ?: AddSongOutcome.Added(calls.size)
    }
}

internal fun jamSong(n: Int) = JamSong(n, SongId("s$n"), "Tema $n", "Artista $n", Key("Bm"), Lineup.default())

internal fun jam(status: JamStatus = JamStatus.DRAFT, setlist: Setlist = Setlist.Available(emptyList())) =
    Jam(JAM_DATE, LocalTime.of(21, 0), "Sala", status, setlist)

internal fun snapshot(upcoming: Jam? = jam()) =
    JamsSnapshot(upcoming, emptyList(), Freshness(Instant.EPOCH, null, false))

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
internal class FakeJamCreator : JamCreator {
    val calls = mutableListOf<CreateCall>()
    var outcome: CreateJamOutcome? = null
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun createJam(date: LocalDate, startTime: LocalTime, venue: String): CreateJamOutcome {
        calls += CreateCall(date, startTime, venue)
        gate?.await()
        return outcome ?: CreateJamOutcome.Created(date, startTime, venue)
    }
}
