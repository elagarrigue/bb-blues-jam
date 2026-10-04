package com.bbbjam.core.data.jams

import android.database.SQLException
import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.DataScope
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.cache.JamWithChildren
import com.bbbjam.core.data.cache.JamsDao
import com.bbbjam.core.data.cache.SyncStateEntity
import com.bbbjam.core.data.cache.isRefreshDue
import com.bbbjam.core.data.cache.jamRows
import com.bbbjam.core.data.cache.mappingFailure
import com.bbbjam.core.data.cache.recordedFailure
import com.bbbjam.core.data.cache.toDomain
import com.bbbjam.core.data.cache.toFreshness
import com.bbbjam.core.data.cache.toStorageFailure
import com.bbbjam.core.data.remote.AppsScriptEnvelope
import com.bbbjam.core.data.remote.AppsScriptTransport
import com.bbbjam.core.data.remote.Decoded
import com.bbbjam.core.data.remote.JamDto
import com.bbbjam.core.data.remote.Resources
import com.bbbjam.core.data.remote.TransportResult
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import java.time.LocalDate
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Cache-first [JamsRepository]: Room is the source of every emission, the network only replaces it.
 * Refreshes run in [scope], so one outlives the caller that started it, and are single-flight. The
 * upcoming/past split is computed on each emission with [calendar]'s today, never stored.
 */
internal class DefaultJamsRepository(
    private val transport: AppsScriptTransport,
    private val dao: JamsDao,
    private val calendar: JamCalendar,
    private val scope: DataScope,
    private val mapper: (List<JamDto>) -> MappedJams = JamsMapper::map,
) : JamsRepository {

    private val refreshing = MutableStateFlow(false)
    private val mutex = Mutex()
    private var inFlight: Deferred<JamsRefreshOutcome>? = null

    override fun observeJams(): Flow<JamsSnapshot> = flow {
        var checked = false
        combine(dao.observeJams(), dao.observeSyncState(Resources.JAMS), refreshing) { jams, state, isRefreshing ->
            CachedJams(jams, state, isRefreshing)
        }.collect { cached ->
            if (!checked) {
                checked = true
                if (cached.state.isRefreshDue(calendar.now())) scope.launch { refresh() }
            }
            emit(cached.toSnapshot(calendar.today()))
        }
    }.catch { error ->
        // A Room read that fails (a corrupt or unreadable cache) must not crash the collector: it is
        // reported once as a storage failure with nothing fetched, and the flow completes. Only
        // SQLException: anything else, cancellation included, is rethrown (`list-states`, S1).
        if (error !is SQLException) throw error
        emit(JamsSnapshot(null, emptyList(), Freshness(null, error.toStorageFailure(), isRefreshing = false)))
    }

    override suspend fun refresh(): JamsRefreshOutcome {
        val shared = mutex.withLock {
            inFlight?.takeIf { it.isActive } ?: scope.async { fetchAndStore() }.also { inFlight = it }
        }
        return shared.await()
    }

    private suspend fun fetchAndStore(): JamsRefreshOutcome {
        refreshing.value = true
        try {
            val result = transport.get(Resources.JAMS)
            val now = calendar.now().toEpochMilli()
            val decoded = when (result) {
                is TransportResult.Failed -> Decoded.Failed(result.failure)
                is TransportResult.Body -> AppsScriptEnvelope.decode(result.text, JAMS, JamDto.serializer())
            }
            return when (decoded) {
                is Decoded.Failed -> failed(now, decoded.failure)
                is Decoded.Ok -> store(now, decoded.value)
            }
        } finally {
            refreshing.value = false
        }
    }

    /** Maps and stores [rows]; a mapper bug or a refused write is a failure, never a crash. */
    private suspend fun store(now: Long, rows: List<JamDto>): JamsRefreshOutcome {
        val mapped = try {
            mapper(rows)
        } catch (e: IllegalArgumentException) {
            return failed(now, mappingFailure(e))
        }
        return try {
            val jams = mapped.jams.map { it.jam }
            dao.replaceJams(
                jamRows(mapped.jams.map { it.jam to it.slotColumns }),
                SyncStateEntity(Resources.JAMS, now, now, failure = null),
            )
            JamsRefreshOutcome.Updated(jams.size, mapped.rejected, mapped.issues, heldBack(jams, calendar.today()))
        } catch (e: SQLException) {
            failed(now, e.toStorageFailure())
        }
    }

    private suspend fun failed(now: Long, failure: DataFailure): JamsRefreshOutcome.Failed =
        JamsRefreshOutcome.Failed(recordedFailure(failure) { dao.recordFailure(Resources.JAMS, now, it) })

    private class CachedJams(val jams: List<JamWithChildren>, val state: SyncStateEntity?, val isRefreshing: Boolean) {
        fun toSnapshot(today: LocalDate): JamsSnapshot {
            val (past, current) = jams.map { it.toDomain() }.partition { it.isHistorical(today) }
            return JamsSnapshot(
                upcoming = current.minByOrNull { it.date },
                past = past.sortedByDescending { it.date }.map { it.withoutUnrecordedSlots() },
                freshness = state.toFreshness(isRefreshing),
            )
        }
    }

    private companion object {
        const val JAMS = "jams"

        /** The future jams after the upcoming one, in date order (user approval P5). */
        fun heldBack(jams: List<Jam>, today: LocalDate): List<LocalDate> =
            jams.filterNot { it.isHistorical(today) }.map { it.date }.sorted().drop(1)

        /** In a past jam an empty slot means "not recorded": only filled slots are kept (P6). */
        fun Jam.withoutUnrecordedSlots(): Jam {
            val available = setlist as? Setlist.Available ?: return this
            val songs = available.songs.map { song ->
                song.copy(lineup = Lineup(song.lineup.slots.filter { it.isFilled }))
            }
            return copy(setlist = available.copy(songs = songs))
        }
    }
}
