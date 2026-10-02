package com.bbbjam.core.data.catalog

import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.DataScope
import com.bbbjam.core.data.Fixtures
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.MutableClock
import com.bbbjam.core.data.remote.AppsScriptTransport
import com.bbbjam.core.data.remote.OkHttpAppsScriptTransport
import com.bbbjam.core.data.remote.TransportResult
import com.bbbjam.core.model.SongId
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The repository over a real in-memory Room cache and a fake transport (one test uses OkHttp against
 * a MockWebServer). Refreshes run in the test's background scope on the test dispatcher.
 */
class DefaultCatalogRepositoryTest {
    private val database = Fixtures.inMemoryDatabase()
    private val clock = MutableClock(T0)
    private val transport = FakeTransport(TransportResult.Body(Fixtures.sample("catalog-seed.json")))

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `online first fetch stores every valid song in response order with the clock's instant`() = runTest {
        val repository = repository()

        val outcome = repository.refresh()

        assertEquals(RefreshOutcome.Updated(13, emptyList(), emptyList()), outcome)
        val snapshot = repository.observeCatalog().first()
        assertEquals(Fixtures.sampleSongs("catalog-seed.json").map { it.id }, snapshot.songs.map { it.id.value })
        assertEquals(Freshness(fetchedAt = T0, lastFailure = null, isRefreshing = false), snapshot.freshness)
        assertFalse(snapshot.freshness.isStale(T0))
        assertEquals(listOf("catalog"), transport.resources)
    }

    @Test
    fun `network down returns the cached catalog with its old time, the failure, and stale`() = runTest {
        val repository = repository()
        repository.refresh()
        clock.instant = T0.plus(Duration.ofMinutes(5))
        transport.result = TransportResult.Failed(DataFailure.Offline)

        val outcome = repository.refresh()

        assertEquals(RefreshOutcome.Failed(DataFailure.Offline), outcome)
        val snapshot = repository.observeCatalog().first()
        assertEquals(13, snapshot.songs.size)
        assertEquals(T0, snapshot.freshness.fetchedAt)
        assertEquals(DataFailure.Offline, snapshot.freshness.lastFailure)
        assertTrue(snapshot.freshness.isStale(clock.instant))
        assertEquals(Duration.ofMinutes(5), snapshot.freshness.age(clock.instant))
    }

    @Test
    fun `network down through OkHttp still returns the cached catalog`() = runTest {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse.Builder().body(Fixtures.sample("catalog-seed.json")).build())
        val url = server.url("/exec").toString()
        val repository = repository(OkHttpAppsScriptTransport(OkHttpAppsScriptTransport.client(), url))
        assertEquals(RefreshOutcome.Updated(13, emptyList(), emptyList()), repository.refresh())
        server.close()

        val outcome = repository.refresh()

        assertEquals(RefreshOutcome.Failed(DataFailure.Offline), outcome)
        val snapshot = repository.observeCatalog().first()
        assertEquals(13, snapshot.songs.size)
        assertEquals(SongId("sweet-little-angel"), snapshot.songs.first().id)
        assertEquals(DataFailure.Offline, snapshot.freshness.lastFailure)
    }

    @Test
    fun `a service error or an invalid body leaves the cache untouched`() = runTest {
        val repository = repository()
        repository.refresh()
        val bodies = mapOf(
            """{"schemaVersion":1,"error":{"code":"missing_header","message":"m"}}""" to
                DataFailure.Service("missing_header"),
            "<!DOCTYPE html><html></html>" to DataFailure.InvalidResponse("body is not a JSON object"),
            """{"schemaVersion":2,"songs":[]}""" to DataFailure.InvalidResponse("schemaVersion 2"),
            """{"schemaVersion":1}""" to DataFailure.InvalidResponse("missing songs"),
        )
        bodies.forEach { (body, failure) ->
            transport.result = TransportResult.Body(body)

            assertEquals(RefreshOutcome.Failed(failure), repository.refresh())

            val snapshot = repository.observeCatalog().first()
            assertEquals(13, snapshot.songs.size)
            assertEquals(T0, snapshot.freshness.fetchedAt)
            assertEquals(failure, snapshot.freshness.lastFailure)
        }
        transport.result = TransportResult.Failed(DataFailure.InvalidResponse("HTTP 500"))
        assertEquals(RefreshOutcome.Failed(DataFailure.InvalidResponse("HTTP 500")), repository.refresh())
        assertEquals(13, repository.observeCatalog().first().songs.size)
    }

    @Test
    fun `bad rows are rejected and reported, the rest are stored`() = runTest {
        transport.result = TransportResult.Body(Fixtures.sample("catalog-edge.json"))
        val repository = repository()

        val outcome = repository.refresh()

        val rejected = RejectedSong(
            4,
            "sin-tono",
            listOf(SongIssue.MissingDefaultKey, SongIssue.InvalidTempo, SongIssue.InvalidSongsterrId),
        )
        assertEquals(RefreshOutcome.Updated(3, listOf(rejected), emptyList()), outcome)
        assertEquals(
            listOf("the-thrill-is-gone", "crossroads", "got-my-mojo-working"),
            repository.observeCatalog().first().songs.map { it.id.value },
        )
    }

    @Test
    fun `a song with an invalid optional value is stored without it`() = runTest {
        val song = """{"id":"blues-uno","title":"Blues Uno","artist":"Alguien","defaultKey":"A",""" +
            """"tempo":"rapido","tags":"shuffle","difficulty":"media","songsterrId":"12.5"}"""
        transport.result = TransportResult.Body(Fixtures.catalogBody("[$song]"))
        val repository = repository()

        val outcome = repository.refresh()

        val dropped = DroppedFields(1, "blues-uno", listOf(SongIssue.InvalidTempo, SongIssue.InvalidSongsterrId))
        assertEquals(RefreshOutcome.Updated(1, emptyList(), listOf(dropped)), outcome)
        val stored = repository.observeCatalog().first().songs.single()
        assertNull(stored.tempo)
        assertNull(stored.songsterrId)
        assertEquals(listOf("shuffle"), stored.tags)
    }

    @Test
    fun `a valid envelope with no songs empties the cache`() = runTest {
        val repository = repository()
        repository.refresh()
        transport.result = TransportResult.Body(Fixtures.catalogBody("[]"))

        assertEquals(RefreshOutcome.Updated(0, emptyList(), emptyList()), repository.refresh())

        val snapshot = repository.observeCatalog().first()
        assertEquals(emptyList<Any>(), snapshot.songs)
        assertEquals(T0, snapshot.freshness.fetchedAt)
        assertNull(snapshot.freshness.lastFailure)
    }

    @Test
    fun `not configured makes no request and fails with NotConfigured`() = runTest {
        val repository = repository(OkHttpAppsScriptTransport(OkHttpAppsScriptTransport.client(), null))

        assertEquals(RefreshOutcome.Failed(DataFailure.NotConfigured), repository.refresh())

        val snapshot = repository.observeCatalog().first()
        assertEquals(emptyList<Any>(), snapshot.songs)
        assertEquals(
            Freshness(fetchedAt = null, lastFailure = DataFailure.NotConfigured, isRefreshing = false),
            snapshot.freshness,
        )
    }

    @Test
    fun `freshness turns stale after 30 minutes`() = runTest {
        val repository = repository()
        repository.refresh()

        val freshness = repository.observeCatalog().first().freshness

        assertFalse(freshness.isStale(T0.plus(Duration.ofMinutes(30))))
        assertTrue(freshness.isStale(T0.plus(Duration.ofMinutes(30)).plusMillis(1)))
        assertEquals(Duration.ofMinutes(31), freshness.age(T0.plus(Duration.ofMinutes(31))))
    }

    @Test
    fun `collecting an empty cache starts one background refresh`() = runTest {
        val repository = repository()

        val first = repository.observeCatalog().first()
        testScheduler.runCurrent()

        assertEquals(emptyList<Any>(), first.songs)
        assertEquals(1, transport.calls)
        val loaded = repository.observeCatalog().first { it.songs.isNotEmpty() && !it.freshness.isRefreshing }
        assertEquals(13, loaded.songs.size)
        assertEquals(1, transport.calls)
    }

    @Test
    fun `concurrent refreshes and collectors share one request`() = runTest {
        val gate = CompletableDeferred<Unit>()
        transport.gate = gate
        val repository = repository()

        val first = async { repository.refresh() }
        val second = async { repository.refresh() }
        testScheduler.runCurrent()
        val refreshing = repository.observeCatalog().first { it.freshness.isRefreshing }
        repository.observeCatalog().first()
        testScheduler.runCurrent()
        gate.complete(Unit)

        assertEquals(first.await(), second.await())
        assertTrue(refreshing.freshness.isRefreshing)
        assertEquals(1, transport.calls)
        assertEquals(13, repository.observeCatalog().first().songs.size)
    }

    @Test
    fun `a fresh cache is not refreshed on collection, a stale one is`() = runTest {
        val repository = repository()
        repository.refresh()

        clock.instant = T0.plus(Duration.ofMinutes(10))
        collectOnce(repository)
        assertEquals(1, transport.calls)

        clock.instant = T0.plus(Duration.ofMinutes(31))
        collectOnce(repository)
        assertEquals(2, transport.calls)
        repository.refresh()
    }

    @Test
    fun `no automatic retry within 60 seconds of the last attempt`() = runTest {
        val repository = repository()
        transport.result = TransportResult.Failed(DataFailure.Offline)
        repository.refresh()
        assertEquals(1, transport.calls)

        clock.instant = T0.plusSeconds(59)
        collectOnce(repository)
        assertEquals(1, transport.calls)

        clock.instant = T0.plusSeconds(60)
        collectOnce(repository)
        assertEquals(2, transport.calls)

        assertEquals(RefreshOutcome.Failed(DataFailure.Offline), repository.refresh())
    }

    @Test
    fun `an explicit refresh is never held back by the retry interval`() = runTest {
        val repository = repository()
        transport.result = TransportResult.Failed(DataFailure.Offline)
        repository.refresh()
        transport.result = TransportResult.Body(Fixtures.sample("catalog-seed.json"))
        clock.instant = T0.plusSeconds(1)

        assertEquals(RefreshOutcome.Updated(13, emptyList(), emptyList()), repository.refresh())
        assertEquals(2, transport.calls)
    }

    /** Collects the first snapshot and runs whatever refresh that collection launched up to its request. */
    private suspend fun TestScope.collectOnce(repository: CatalogRepository) {
        repository.observeCatalog().first()
        testScheduler.runCurrent()
    }

    private fun TestScope.repository(transport: AppsScriptTransport = this@DefaultCatalogRepositoryTest.transport) =
        DefaultCatalogRepository(transport, database.catalogDao(), clock, DataScope(backgroundScope))

    private class FakeTransport(var result: TransportResult) : AppsScriptTransport {
        var calls = 0
        val resources = mutableListOf<String>()
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun get(resource: String): TransportResult {
            calls++
            resources += resource
            gate?.await()
            return result
        }
    }

    private companion object {
        val T0: Instant = Instant.parse("2026-10-01T21:00:00Z")
    }
}
