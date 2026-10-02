package com.bbbjam.core.data

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FreshnessTest {

    @Test
    fun `nothing fetched is stale and has no age`() {
        val freshness = Freshness(fetchedAt = null, lastFailure = null, isRefreshing = false)

        assertTrue(freshness.isStale(T0))
        assertNull(freshness.age(T0))
    }

    @Test
    fun `a success is fresh for 30 minutes`() {
        val freshness = Freshness(fetchedAt = T0, lastFailure = null, isRefreshing = false)

        assertFalse(freshness.isStale(T0))
        assertFalse(freshness.isStale(T0.plus(Freshness.STALE_AFTER)))
        assertTrue(freshness.isStale(T0.plus(Freshness.STALE_AFTER).plusMillis(1)))
        assertEquals(Duration.ofMinutes(12), freshness.age(T0.plus(Duration.ofMinutes(12))))
    }

    @Test
    fun `a failure after a success is stale at once`() {
        val freshness = Freshness(fetchedAt = T0, lastFailure = DataFailure.Offline, isRefreshing = false)

        assertTrue(freshness.isStale(T0.plusSeconds(1)))
    }

    @Test
    fun `every failure survives the cache encoding`() {
        listOf(
            DataFailure.NotConfigured,
            DataFailure.Offline,
            DataFailure.Service("missing_header"),
            DataFailure.InvalidResponse("songs: Field 'x' is required"),
            DataFailure.InvalidResponse(""),
            DataFailure.Storage("SQLiteFullException"),
        ).forEach { assertEquals(it, decodeDataFailure(it.encode())) }
        assertEquals(DataFailure.InvalidResponse("something new"), decodeDataFailure("something new"))
        assertEquals("Storage:SQLiteFullException", DataFailure.Storage("SQLiteFullException").encode())
    }

    private companion object {
        val T0: Instant = Instant.parse("2026-10-01T21:00:00Z")
    }
}
