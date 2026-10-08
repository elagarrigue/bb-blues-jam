package com.bbbjam.core.data.setlist

import com.bbbjam.core.data.DataScope
import com.bbbjam.core.data.admin.AdminWriter
import com.bbbjam.core.data.cache.CatalogDao
import com.bbbjam.core.data.cache.SetlistDao
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.MusicianName
import com.bbbjam.core.model.SlotPosition
import com.bbbjam.core.model.SongId
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [SetlistRepository] over the admin POST path. The pending and failed entries live in memory; the
 * cache only ever receives a song the server confirmed, built from its answer, so a failed write
 * leaves no row anywhere. A removal changes the cache only when the server removed the song, or
 * answered that the Sheet no longer has it. A key change changes the cache only when the server
 * wrote the key, and before its entry is removed.
 *
 * Ordering: [order] is held while a call resolves its song, publishes its entry and queues its write
 * on [writes] (both mutexes are fair), so writes are sent one at a time in call order. The write
 * runs in [scope], and the caller only awaits it.
 */
internal class DefaultSetlistRepository(
    private val writer: AdminWriter,
    private val setlistDao: SetlistDao,
    private val catalogDao: CatalogDao,
    private val scope: DataScope,
) : SetlistRepository {

    private val ids = AtomicLong(0)
    private val order = Mutex()
    private val writes = Mutex()
    private val additions = SetlistSongAdds(writer, setlistDao, catalogDao, scope, ids, order, writes)
    private val removals = SetlistRemovals(writer, setlistDao, catalogDao)
    private val keyChanges = SetlistKeyChanges(writer, setlistDao, catalogDao)
    private val lineupChanges = SetlistLineupChanges(writer, setlistDao, catalogDao)
    private val assignments = SetlistAssignments(writer, setlistDao, catalogDao)

    /**
     * Runs in [scope] from the first instruction (undispatched), so a caller cancelled at any point,
     * even before the song is resolved, never cancels an add it started: a presenter may launch it
     * and close its screen at once.
     */
    override suspend fun addSong(jamDate: LocalDate, songId: SongId, key: Key): AddSongOutcome =
        additions.addSong(jamDate, songId, key)

    override fun observeAdds(): Flow<List<SetlistAdd>> = additions.observe()

    override fun dismiss(id: Long) {
        additions.dismiss(id)
        removals.dismiss(id)
        keyChanges.dismiss(id)
        lineupChanges.dismiss(id)
        assignments.dismiss(id)
    }

    /**
     * Runs in [scope] from the first instruction, like [addSong], and queues behind earlier writes of
     * either kind: the entry is published under [order] and the request sent under [writes].
     */
    override suspend fun removeSong(jamDate: LocalDate, songId: SongId): RemoveSongOutcome =
        scope.async(start = CoroutineStart.UNDISPATCHED) {
            order.withLock {
                val entry = removals.start(ids.incrementAndGet(), jamDate, songId)
                scope.async(start = CoroutineStart.UNDISPATCHED) { writes.withLock { removals.send(entry) } }
            }.await()
        }.await()

    override fun observeRemoves(): Flow<List<SetlistRemove>> = removals.observe()

    /**
     * Runs in [scope] from the first instruction, like [addSong], and queues behind earlier writes of
     * any kind: the entry is published under [order] and the request sent under [writes].
     */
    override suspend fun setKey(jamDate: LocalDate, songId: SongId, key: Key): SetKeyOutcome =
        scope.async(start = CoroutineStart.UNDISPATCHED) {
            order.withLock {
                val entry = keyChanges.start(ids.incrementAndGet(), jamDate, songId, key)
                scope.async(start = CoroutineStart.UNDISPATCHED) { writes.withLock { keyChanges.send(entry) } }
            }.await()
        }.await()

    override fun observeKeyChanges(): Flow<List<KeyChange>> = keyChanges.observe()

    override suspend fun setSlotCount(
        jamDate: LocalDate,
        songId: SongId,
        instrument: Instrument,
        count: Int,
    ): SetSlotCountOutcome = scope.async(start = CoroutineStart.UNDISPATCHED) {
        order.withLock {
            val entry = lineupChanges.start(ids.incrementAndGet(), jamDate, songId, instrument, count)
            scope.async(start = CoroutineStart.UNDISPATCHED) { writes.withLock { lineupChanges.send(entry) } }
        }.await()
    }.await()

    override fun observeLineupChanges(): Flow<List<LineupChange>> = lineupChanges.observe()

    override suspend fun assignSlot(
        jamDate: LocalDate,
        songId: SongId,
        instrument: Instrument,
        ordinal: SlotPosition,
        musicianName: MusicianName,
    ): AssignSlotOutcome = scope.async(start = CoroutineStart.UNDISPATCHED) {
        order.withLock {
            val (entry, target, localError) = assignments.start(
                ids.incrementAndGet(),
                jamDate,
                songId,
                instrument,
                ordinal,
                musicianName,
            )
            scope.async(start = CoroutineStart.UNDISPATCHED) {
                writes.withLock { assignments.send(entry, target, localError) }
            }
        }.await()
    }.await()

    override fun observeAssignments(): Flow<List<Assignment>> = assignments.observe()
}
