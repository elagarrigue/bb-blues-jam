package com.bbbjam.core.data.catalog

import com.bbbjam.core.data.DataScope
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.cache.CatalogDao
import com.bbbjam.core.data.cache.CatalogSongEntity
import com.bbbjam.core.data.cache.SyncStateEntity
import com.bbbjam.core.data.cache.toDomain
import com.bbbjam.core.data.cache.toEntity
import com.bbbjam.core.data.decodeDataFailure
import com.bbbjam.core.data.encode
import com.bbbjam.core.data.remote.AppsScriptEnvelope
import com.bbbjam.core.data.remote.AppsScriptTransport
import com.bbbjam.core.data.remote.Decoded
import com.bbbjam.core.data.remote.Resources
import com.bbbjam.core.data.remote.SongDto
import com.bbbjam.core.data.remote.TransportResult
import java.time.Clock
import java.time.Instant
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
 */
internal class DefaultCatalogRepository(
    private val transport: AppsScriptTransport,
    private val dao: CatalogDao,
    private val clock: Clock,
    private val scope: DataScope,
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
                if (isRefreshDue(cached.state, clock.instant())) scope.launch { refresh() }
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
                is Decoded.Failed -> {
                    dao.recordFailure(Resources.CATALOG, now, decoded.failure.encode())
                    RefreshOutcome.Failed(decoded.failure)
                }

                is Decoded.Ok -> {
                    val mapped = CatalogMapper.map(decoded.value)
                    val rows = mapped.songs.mapIndexed { index, song -> song.toEntity(sheetOrder = index + 1) }
                    dao.replaceCatalog(rows, SyncStateEntity(Resources.CATALOG, now, now, failure = null))
                    RefreshOutcome.Updated(mapped.songs.size, mapped.rejected, mapped.dropped)
                }
            }
        } finally {
            refreshing.value = false
        }
    }

    /** Stale ([Freshness.isStale]) and the last attempt, if any, at least the retry interval ago. */
    private fun isRefreshDue(state: SyncStateEntity?, now: Instant): Boolean {
        val stale = state.toFreshness(isRefreshing = false).isStale(now)
        val attemptedAt = state?.attemptedAt?.let(Instant::ofEpochMilli)
        val retryAllowed = attemptedAt == null || !now.isBefore(attemptedAt.plus(CatalogRepository.MIN_RETRY_INTERVAL))
        return stale && retryAllowed
    }

    private class CachedCatalog(
        val songs: List<CatalogSongEntity>,
        val state: SyncStateEntity?,
        val isRefreshing: Boolean,
    ) {
        fun toSnapshot() = CatalogSnapshot(songs.map { it.toDomain() }, state.toFreshness(isRefreshing))
    }

    private companion object {
        const val SONGS = "songs"

        fun SyncStateEntity?.toFreshness(isRefreshing: Boolean): Freshness = Freshness(
            fetchedAt = this?.fetchedAt?.let(Instant::ofEpochMilli),
            lastFailure = this?.failure?.let(::decodeDataFailure),
            isRefreshing = isRefreshing,
        )
    }
}
