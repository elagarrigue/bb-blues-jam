package com.bbbjam.core.data.catalog

import android.database.SQLException
import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.DataScope
import com.bbbjam.core.data.cache.CatalogDao
import com.bbbjam.core.data.cache.CatalogSongEntity
import com.bbbjam.core.data.cache.SyncStateEntity
import com.bbbjam.core.data.cache.isRefreshDue
import com.bbbjam.core.data.cache.mappingFailure
import com.bbbjam.core.data.cache.recordedFailure
import com.bbbjam.core.data.cache.toDomain
import com.bbbjam.core.data.cache.toEntity
import com.bbbjam.core.data.cache.toFreshness
import com.bbbjam.core.data.cache.toStorageFailure
import com.bbbjam.core.data.remote.AppsScriptEnvelope
import com.bbbjam.core.data.remote.AppsScriptTransport
import com.bbbjam.core.data.remote.Decoded
import com.bbbjam.core.data.remote.Resources
import com.bbbjam.core.data.remote.SongDto
import com.bbbjam.core.data.remote.TransportResult
import java.time.Clock
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Cache-first [CatalogRepository]: Room is the source of every emission, the network only replaces
 * it. Refreshes run in [scope], so one outlives the caller that started it, and are single-flight.
 * A refused write ([DataFailure.Storage]) or a mapper exception is a failure outcome, never a crash.
 */
internal class DefaultCatalogRepository(
    private val transport: AppsScriptTransport,
    private val dao: CatalogDao,
    private val clock: Clock,
    private val scope: DataScope,
    private val mapper: (List<SongDto>) -> MappedCatalog = CatalogMapper::map,
) : CatalogRepository {

    private val refreshing = MutableStateFlow(false)
    private val mutex = Mutex()
    private var inFlight: Deferred<RefreshOutcome>? = null

    override fun observeCatalog(): Flow<CatalogSnapshot> = flow {
        var checked = false
        combine(dao.observeSongs(), dao.observeSyncState(Resources.CATALOG), refreshing) { songs, state, isRefreshing ->
            CachedCatalog(songs, state, isRefreshing)
        }.collect { cached ->
            if (!checked) {
                checked = true
                if (cached.state.isRefreshDue(clock.instant())) scope.launch { refresh() }
            }
            emit(cached.toSnapshot())
        }
    }

    override suspend fun refresh(): RefreshOutcome {
        val shared = mutex.withLock {
            inFlight?.takeIf { it.isActive } ?: scope.async { fetchAndStore() }.also { inFlight = it }
        }
        return shared.await()
    }

    private suspend fun fetchAndStore(): RefreshOutcome {
        refreshing.value = true
        try {
            val result = transport.get(Resources.CATALOG)
            val now = clock.instant().toEpochMilli()
            val decoded = when (result) {
                is TransportResult.Failed -> Decoded.Failed(result.failure)
                is TransportResult.Body -> AppsScriptEnvelope.decode(result.text, SONGS, SongDto.serializer())
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
    private suspend fun store(now: Long, rows: List<SongDto>): RefreshOutcome {
        val mapped = try {
            mapper(rows)
        } catch (e: IllegalArgumentException) {
            return failed(now, mappingFailure(e))
        }
        return try {
            val entities = mapped.songs.mapIndexed { index, song -> song.toEntity(sheetOrder = index + 1) }
            dao.replaceCatalog(entities, SyncStateEntity(Resources.CATALOG, now, now, failure = null))
            RefreshOutcome.Updated(mapped.songs.size, mapped.rejected, mapped.dropped)
        } catch (e: SQLException) {
            failed(now, e.toStorageFailure())
        }
    }

    private suspend fun failed(now: Long, failure: DataFailure): RefreshOutcome.Failed =
        RefreshOutcome.Failed(recordedFailure(failure) { dao.recordFailure(Resources.CATALOG, now, it) })

    private class CachedCatalog(
        val songs: List<CatalogSongEntity>,
        val state: SyncStateEntity?,
        val isRefreshing: Boolean,
    ) {
        fun toSnapshot() = CatalogSnapshot(songs.map { it.toDomain() }, state.toFreshness(isRefreshing))
    }

    private companion object {
        const val SONGS = "songs"
    }
}
