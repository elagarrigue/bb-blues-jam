package com.bbbjam.core.data.setlist

import com.bbbjam.core.data.DataScope
import com.bbbjam.core.data.admin.AdminWriter
import com.bbbjam.core.data.cache.CatalogDao
import com.bbbjam.core.data.cache.SetlistDao
import com.bbbjam.core.data.setlist.add.SetlistSongAddRepository
import com.bbbjam.core.data.setlist.add.SetlistSongAdds
import com.bbbjam.core.data.setlist.assign.SetlistAssignmentRepository
import com.bbbjam.core.data.setlist.assign.SetlistAssignments
import com.bbbjam.core.data.setlist.clear.SetlistSlotClearRepository
import com.bbbjam.core.data.setlist.clear.SetlistSlotClears
import com.bbbjam.core.data.setlist.engine.SetlistMutationQueue
import com.bbbjam.core.data.setlist.engine.WriteFailureLog
import com.bbbjam.core.data.setlist.extras.SetlistExtraParticipantRepository
import com.bbbjam.core.data.setlist.extras.SetlistExtraParticipants
import com.bbbjam.core.data.setlist.jamcreate.SetlistJamCreationRepository
import com.bbbjam.core.data.setlist.jamcreate.SetlistJamCreations
import com.bbbjam.core.data.setlist.jamedit.SetlistJamEditRepository
import com.bbbjam.core.data.setlist.jamedit.SetlistJamEdits
import com.bbbjam.core.data.setlist.jamstatus.SetlistJamStatusRepository
import com.bbbjam.core.data.setlist.jamstatus.SetlistJamStatuses
import com.bbbjam.core.data.setlist.key.SetlistKeyChangeRepository
import com.bbbjam.core.data.setlist.key.SetlistKeyChanges
import com.bbbjam.core.data.setlist.lineup.SetlistLineupChanges
import com.bbbjam.core.data.setlist.lineup.SetlistLineupRepository
import com.bbbjam.core.data.setlist.move.SetlistMoveRepository
import com.bbbjam.core.data.setlist.move.SetlistMoves
import com.bbbjam.core.data.setlist.publish.SetlistPublishRepository
import com.bbbjam.core.data.setlist.publish.SetlistPublishes
import com.bbbjam.core.data.setlist.remove.SetlistRemovals
import com.bbbjam.core.data.setlist.remove.SetlistSongRemoveRepository

/**
 * [SetlistRepository] over the admin POST path, by delegation: each of the twelve mutation
 * contracts is implemented by its own manager, built here over the same [queue]. The pending and
 * failed entries live in memory; the cache only ever receives what the server confirmed, so a
 * failed write leaves no row anywhere.
 *
 * Ordering: every manager publishes its entry under the queue's order lock and sends its request
 * under its write lock (both mutexes are fair), so writes are sent one at a time in call order. The
 * write runs in [scope], and the caller only awaits it. [failureLog] receives the safe diagnostics of
 * the publish, status, edit and creation writes.
 */
internal class DefaultSetlistRepository(
    writer: AdminWriter,
    setlistDao: SetlistDao,
    catalogDao: CatalogDao,
    scope: DataScope,
    queue: SetlistMutationQueue,
    failureLog: WriteFailureLog,
) : SetlistRepository,
    SetlistSongAddRepository by SetlistSongAdds(writer, setlistDao, catalogDao, scope, queue),
    SetlistSongRemoveRepository by SetlistRemovals(writer, setlistDao, catalogDao, scope, queue),
    SetlistKeyChangeRepository by SetlistKeyChanges(writer, setlistDao, catalogDao, scope, queue),
    SetlistLineupRepository by SetlistLineupChanges(writer, setlistDao, catalogDao, scope, queue),
    SetlistAssignmentRepository by SetlistAssignments(writer, setlistDao, catalogDao, scope, queue),
    SetlistSlotClearRepository by SetlistSlotClears(writer, setlistDao, catalogDao, scope, queue),
    SetlistMoveRepository by SetlistMoves(writer, setlistDao, catalogDao, scope, queue),
    SetlistExtraParticipantRepository by SetlistExtraParticipants(writer, setlistDao, catalogDao, scope, queue),
    SetlistPublishRepository by SetlistPublishes(writer, setlistDao, scope, queue, failureLog),
    SetlistJamStatusRepository by SetlistJamStatuses(writer, setlistDao, scope, queue, failureLog),
    SetlistJamCreationRepository by SetlistJamCreations(writer, setlistDao, scope, queue, failureLog),
    SetlistJamEditRepository by SetlistJamEdits(writer, setlistDao, scope, queue, failureLog)
