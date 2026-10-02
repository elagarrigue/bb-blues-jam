package com.bbbjam.core.data.catalog

import com.bbbjam.core.data.DataFailure
import org.junit.Assert.assertEquals
import org.junit.Test

class RefreshOutcomeTest {

    @Test
    fun `an update logs counts, ids and issues`() {
        val outcome = RefreshOutcome.Updated(
            songCount = 3,
            rejected = listOf(
                RejectedSong(4, "sin-tono", listOf(SongIssue.MissingDefaultKey, SongIssue.InvalidTempo)),
                RejectedSong(5, null, listOf(SongIssue.MissingId)),
            ),
            dropped = listOf(DroppedFields(2, "crossroads", listOf(SongIssue.InvalidSongsterrId))),
        )

        assertEquals(
            "catalog refresh: updated 3 songs, 2 rejected, 1 with dropped fields" +
                "; rejected #4 sin-tono: MissingDefaultKey, InvalidTempo" +
                "; rejected #5 (no id): MissingId" +
                "; dropped #2 crossroads: InvalidSongsterrId",
            outcome.toLogLine(),
        )
    }

    @Test
    fun `a failure logs its kind`() {
        assertEquals("catalog refresh: failed Offline", RefreshOutcome.Failed(DataFailure.Offline).toLogLine())
        assertEquals(
            "catalog refresh: failed Service(code=missing_header)",
            RefreshOutcome.Failed(DataFailure.Service("missing_header")).toLogLine(),
        )
        assertEquals(
            "catalog refresh: failed NotConfigured",
            RefreshOutcome.Failed(DataFailure.NotConfigured).toLogLine(),
        )
    }
}
