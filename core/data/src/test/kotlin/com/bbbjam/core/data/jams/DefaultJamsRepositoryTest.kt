package com.bbbjam.core.data.jams

import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.DataScope
import com.bbbjam.core.data.Fixtures
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.MutableClock
import com.bbbjam.core.data.admin.AdminCredentialStore
import com.bbbjam.core.data.admin.AdminWriter
import com.bbbjam.core.data.cache.CatalogSongEntity
import com.bbbjam.core.data.cache.JamRows
import com.bbbjam.core.data.cache.JamWithChildren
import com.bbbjam.core.data.cache.JamsDao
import com.bbbjam.core.data.cache.SyncStateEntity
import com.bbbjam.core.data.remote.AppsScriptPostTransport
import com.bbbjam.core.data.remote.AppsScriptTransport
import com.bbbjam.core.data.remote.JamDto
import com.bbbjam.core.data.remote.OkHttpAppsScriptTransport
import com.bbbjam.core.data.remote.TransportResult
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SetlistProblem
import com.bbbjam.core.model.Slot
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The repository over a real in-memory Room cache and a fake transport (one test uses OkHttp against
 * a MockWebServer). Refreshes run in the test's background scope on the test dispatcher, so an
 * exception escaping a background refresh fails the test.
 */
class DefaultJamsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val database = Fixtures.inMemoryDatabase()
    private val dao = FailingJamsDao(database.jamsDao())
    private val clock = MutableClock(JULY_20)
    private val calendar = JamCalendar(clock, JamCalendar.BUENOS_AIRES)
    private val transport = FakeTransport(TransportResult.Body(Fixtures.sample("jams-seed.json")))
    private val post = FakePost(adminBody())

    /** One store per test: DataStore allows a single active instance per file. */
    private var adminStore: AdminCredentialStore? = null

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `online, the seed's jam is upcoming with 13 songs in order, seven open slots each, fetched now`() = runTest {
        val repository = repository()

        val outcome = repository.refresh()

        assertEquals(JamsRefreshOutcome.Updated(1, emptyList(), emptyList(), emptyList()), outcome)
        val snapshot = repository.observeJams().first()
        val upcoming = checkNotNull(snapshot.upcoming)
        assertEquals(LocalDate.of(2026, 7, 25), upcoming.date)
        val songs = (upcoming.setlist as Setlist.Available).songs
        assertEquals((1..13).toList(), songs.map { it.position })
        assertTrue(songs.all { it.lineup.openSlots.size == 7 })
        assertEquals(emptyList<Jam>(), snapshot.past)
        assertEquals(Freshness(fetchedAt = JULY_20, lastFailure = null, isRefreshing = false), snapshot.freshness)
        assertEquals(listOf("jams"), transport.resources)
    }

    @Test
    fun `offline, the cached jams come back with their old time, the failure, and stale`() = runTest {
        val repository = repository()
        repository.refresh()
        clock.instant = JULY_20.plus(Duration.ofMinutes(5))
        transport.result = TransportResult.Failed(DataFailure.Offline)

        assertEquals(JamsRefreshOutcome.Failed(DataFailure.Offline), repository.refresh())

        val snapshot = repository.observeJams().first()
        assertEquals(13, (snapshot.upcoming?.setlist as Setlist.Available).songs.size)
        assertEquals(JULY_20, snapshot.freshness.fetchedAt)
        assertEquals(DataFailure.Offline, snapshot.freshness.lastFailure)
        assertTrue(snapshot.freshness.isStale(clock.instant))
    }

    @Test
    fun `offline through OkHttp still returns the cached jams`() = runTest {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse.Builder().body(Fixtures.sample("jams-seed.json")).build())
        val url = server.url("/exec").toString()
        val repository = repository(OkHttpAppsScriptTransport(OkHttpAppsScriptTransport.client(), url))
        assertEquals(JamsRefreshOutcome.Updated(1, emptyList(), emptyList(), emptyList()), repository.refresh())
        assertEquals("jams", server.takeRequest().url.queryParameter("resource"))
        server.close()

        assertEquals(JamsRefreshOutcome.Failed(DataFailure.Offline), repository.refresh())

        val snapshot = repository.observeJams().first()
        assertEquals(LocalDate.of(2026, 7, 25), snapshot.upcoming?.date)
        assertEquals(DataFailure.Offline, snapshot.freshness.lastFailure)
    }

    @Test
    fun `withheld and broken setlists are states, past jams newest first, four rows rejected`() = runTest {
        transport.result = TransportResult.Body(Fixtures.sample("jams-edge.json"))
        clock.instant = OCTOBER_2
        val repository = repository()

        val outcome = repository.refresh() as JamsRefreshOutcome.Updated

        assertEquals(5, outcome.jamCount)
        assertEquals(listOf(3, 6, 7, 8), outcome.rejected.map { it.index })
        assertEquals(2, outcome.issues.size)
        assertEquals(emptyList<LocalDate>(), outcome.heldBack)
        val snapshot = repository.observeJams().first()
        assertNull(snapshot.upcoming)
        assertEquals(
            listOf("2026-09-26", "2026-08-29", "2026-05-30", "2026-04-25", "2026-02-28"),
            snapshot.past.map { it.date.toString() },
        )
        assertEquals(Setlist.Withheld, snapshot.past[0].setlist)
        assertEquals(Setlist.Unavailable(SetlistProblem.MISSING_TAB), snapshot.past[2].setlist)
        assertEquals(Setlist.Unavailable(SetlistProblem.INVALID_TAB), snapshot.past[3].setlist)
        assertTrue(snapshot.past[1].setlist is Setlist.Available)
        assertTrue(snapshot.past[4].setlist is Setlist.Available)
    }

    @Test
    fun `past lineups keep only filled slots, never an open one`() = runTest {
        transport.result = TransportResult.Body(Fixtures.sample("jams-edge.json"))
        clock.instant = OCTOBER_2
        val repository = repository()
        repository.refresh()

        val past = repository.observeJams().first().past

        val songs = past.flatMap { (it.setlist as? Setlist.Available)?.songs.orEmpty() }
        assertTrue(songs.none { it.lineup.openSlots.isNotEmpty() })
        val august = (past[1].setlist as Setlist.Available).songs
        assertEquals(
            listOf("Ana", "Luis", "Marta", "Diego", "Sofía"),
            august[0].lineup.slots.map { it.musicianName },
        )
        assertEquals(listOf(Slot(Instrument.GUITAR, "Pedro")), august[1].lineup.slots)
        assertEquals(Lineup(emptyList()), august[2].lineup)
        assertEquals(2, august[0].extraParticipants.size)
    }

    @Test
    fun `the earliest future jam is upcoming, later ones are held back, and past is newest first`() = runTest {
        transport.result = TransportResult.Body(Fixtures.sample("jams-edge.json"))
        clock.instant = Instant.parse("2026-08-01T15:00:00Z")
        val repository = repository()

        val outcome = repository.refresh() as JamsRefreshOutcome.Updated

        assertEquals(listOf(LocalDate.of(2026, 9, 26)), outcome.heldBack)
        val snapshot = repository.observeJams().first()
        val upcoming = checkNotNull(snapshot.upcoming)
        assertEquals(LocalDate.of(2026, 8, 29), upcoming.date)
        // The upcoming jam keeps its open slots: that is where a musician can play.
        assertEquals(5, (upcoming.setlist as Setlist.Available).songs[1].lineup.openSlots.size)
        assertEquals(listOf("2026-05-30", "2026-04-25", "2026-02-28"), snapshot.past.map { it.date.toString() })
    }

    @Test
    fun `a jam is upcoming through its own night in Buenos Aires and past from the next day`() = runTest {
        val repository = repository()
        repository.refresh()

        // 23:30 on 25 July in Buenos Aires is already 26 July in UTC.
        clock.instant = Instant.parse("2026-07-26T02:30:00Z")
        val night = repository.observeJams().first()
        assertEquals(LocalDate.of(2026, 7, 25), night.upcoming?.date)

        clock.instant = Instant.parse("2026-07-26T03:00:00Z")
        val nextDay = repository.observeJams().first()
        assertNull(nextDay.upcoming)
        assertEquals(listOf(LocalDate.of(2026, 7, 25)), nextDay.past.map { it.date })
        repository.refresh()
    }

    @Test
    fun `a full disk on replace is a storage failure for an explicit refresh and the cache is untouched`() = runTest {
        val repository = repository()
        repository.refresh()
        transport.result = TransportResult.Body(Fixtures.sample("jams-edge.json"))
        dao.failReplace = true
        clock.instant = JULY_20.plusSeconds(1)

        val outcome = repository.refresh()

        assertEquals(JamsRefreshOutcome.Failed(DataFailure.Storage("SQLiteFullException")), outcome)
        val snapshot = repository.observeJams().first()
        assertEquals(LocalDate.of(2026, 7, 25), snapshot.upcoming?.date)
        assertEquals(JULY_20, snapshot.freshness.fetchedAt)
        assertEquals(DataFailure.Storage("SQLiteFullException"), snapshot.freshness.lastFailure)
    }

    @Test
    fun `a full disk during a background refresh started by a collector does not crash`() = runTest {
        dao.failReplace = true
        val repository = repository()

        val first = repository.observeJams().first()
        testScheduler.advanceUntilIdle()

        assertNull(first.upcoming)
        assertEquals(1, transport.calls)
        val failed = repository.observeJams().first()
        assertEquals(DataFailure.Storage("SQLiteFullException"), failed.freshness.lastFailure)
        assertNull(failed.upcoming)
    }

    @Test
    fun `when even recording the failure is refused, the outcome is still a storage failure`() = runTest {
        dao.failReplace = true
        dao.failRecord = true
        val repository = repository()

        assertEquals(JamsRefreshOutcome.Failed(DataFailure.Storage("SQLiteFullException")), repository.refresh())
        transport.result = TransportResult.Failed(DataFailure.Offline)
        assertEquals(JamsRefreshOutcome.Failed(DataFailure.Storage("SQLiteFullException")), repository.refresh())
    }

    @Test
    fun `a mapper exception is an invalid response, not a crash, and the cache is untouched`() = runTest {
        repository().refresh()
        val repository =
            repository(mapper = { _, _ -> throw IllegalArgumentException("Setlist positions [2, 1]\nsecond line") })

        val outcome = repository.refresh()

        assertEquals(
            JamsRefreshOutcome.Failed(DataFailure.InvalidResponse("mapping: Setlist positions [2, 1]")),
            outcome,
        )
        assertEquals(LocalDate.of(2026, 7, 25), repository.observeJams().first().upcoming?.date)
    }

    @Test
    fun `titles come from the catalog when the song is there, and a catalog replace re-emits without a jams refresh`() =
        runTest {
            transport.result = TransportResult.Body(Fixtures.sample("jams-edge.json"))
            clock.instant = OCTOBER_2
            val catalogDao = database.catalogDao()
            catalogDao.replaceCatalog(listOf(catalogSong("crossroads", "Crossroads (catálogo)")), catalogState())
            val repository = repository()
            repository.refresh()

            val august = repository.observeJams().first().past[1].songs()
            assertEquals(
                listOf("The Thrill Is Gone", "Crossroads (catálogo)", "Got My Mojo Working"),
                august.map {
                    it.title
                },
            )
            assertEquals(Key("A"), august[1].key)

            catalogDao.replaceCatalog(listOf(catalogSong("the-thrill-is-gone", "Thrill (catálogo)")), catalogState())
            val after = repository.observeJams().first().past[1].songs()
            assertEquals(listOf("Thrill (catálogo)", "Crossroads", "Got My Mojo Working"), after.map { it.title })
            assertEquals(Key("Bm"), after[0].key)
            assertEquals(1, transport.calls)
        }

    @Test
    fun `a service error or an invalid body leaves the cache untouched`() = runTest {
        val repository = repository()
        repository.refresh()
        val bodies = mapOf(
            """{"schemaVersion":1,"error":{"code":"missing_header","message":"m"}}""" to
                DataFailure.Service("missing_header"),
            "<!DOCTYPE html><html></html>" to DataFailure.InvalidResponse("body is not a JSON object"),
            """{"schemaVersion":2,"jams":[]}""" to DataFailure.InvalidResponse("schemaVersion 2"),
            """{"schemaVersion":1}""" to DataFailure.InvalidResponse("missing jams"),
        )
        bodies.forEach { (body, failure) ->
            transport.result = TransportResult.Body(body)

            assertEquals(JamsRefreshOutcome.Failed(failure), repository.refresh())

            val snapshot = repository.observeJams().first()
            assertEquals(LocalDate.of(2026, 7, 25), snapshot.upcoming?.date)
            assertEquals(JULY_20, snapshot.freshness.fetchedAt)
            assertEquals(failure, snapshot.freshness.lastFailure)
        }
    }

    @Test
    fun `a valid envelope with no jams empties the cache`() = runTest {
        val repository = repository()
        repository.refresh()
        transport.result = TransportResult.Body(Fixtures.jamsBody("[]"))

        assertEquals(JamsRefreshOutcome.Updated(0, emptyList(), emptyList(), emptyList()), repository.refresh())

        val snapshot = repository.observeJams().first()
        assertNull(snapshot.upcoming)
        assertEquals(emptyList<Jam>(), snapshot.past)
        assertNull(snapshot.freshness.lastFailure)
    }

    @Test
    fun `not configured makes no request and fails with NotConfigured`() = runTest {
        val repository = repository(OkHttpAppsScriptTransport(OkHttpAppsScriptTransport.client(), null))

        assertEquals(JamsRefreshOutcome.Failed(DataFailure.NotConfigured), repository.refresh())

        val snapshot = repository.observeJams().first()
        assertNull(snapshot.upcoming)
        assertEquals(
            Freshness(fetchedAt = null, lastFailure = DataFailure.NotConfigured, isRefreshing = false),
            snapshot.freshness,
        )
    }

    @Test
    fun `a failed local read emits one storage failure with nothing fetched, then completes`() = runTest {
        dao.failRead = SQLiteException("no such table: jam")
        val repository = repository()

        val emitted = repository.observeJams().toList()

        assertEquals(
            listOf(
                JamsSnapshot(
                    upcoming = null,
                    past = emptyList(),
                    freshness = Freshness(null, DataFailure.Storage("SQLiteException"), isRefreshing = false),
                ),
            ),
            emitted,
        )
        assertEquals(0, transport.calls)

        // Re-subscribing once the read works serves the cache again (list-states retry).
        dao.failRead = null
        repository.refresh()
        assertEquals(LocalDate.of(2026, 7, 25), repository.observeJams().first().upcoming?.date)
    }

    @Test
    fun `a read failure that is not a SQLException is rethrown`() = runTest {
        dao.failRead = IllegalArgumentException("not a storage failure")
        val repository = repository()

        val thrown = runCatching { repository.observeJams().first() }.exceptionOrNull()

        assertTrue(thrown is IllegalArgumentException)
    }

    @Test
    fun `collecting an empty cache starts one background refresh`() = runTest {
        val repository = repository()

        val first = repository.observeJams().first()
        testScheduler.runCurrent()

        assertNull(first.upcoming)
        assertEquals(1, transport.calls)
        val loaded = repository.observeJams().first { it.upcoming != null && !it.freshness.isRefreshing }
        assertEquals(LocalDate.of(2026, 7, 25), loaded.upcoming?.date)
        assertEquals(1, transport.calls)
    }

    @Test
    fun `a fresh cache is not refreshed on collection, one stale after 30 minutes is`() = runTest {
        val repository = repository()
        repository.refresh()

        clock.instant = JULY_20.plus(Duration.ofMinutes(30))
        collectOnce(repository)
        assertEquals(1, transport.calls)

        clock.instant = JULY_20.plus(Duration.ofMinutes(31))
        collectOnce(repository)
        assertEquals(2, transport.calls)
        repository.refresh()
    }

    @Test
    fun `no automatic retry within 60 seconds of the last attempt`() = runTest {
        val repository = repository()
        transport.result = TransportResult.Failed(DataFailure.Offline)
        repository.refresh()

        clock.instant = JULY_20.plusSeconds(59)
        collectOnce(repository)
        assertEquals(1, transport.calls)

        clock.instant = JULY_20.plusSeconds(60)
        collectOnce(repository)
        assertEquals(2, transport.calls)
        assertEquals(JamsRefreshOutcome.Failed(DataFailure.Offline), repository.refresh())
    }

    @Test
    fun `concurrent refreshes and collectors share one request`() = runTest {
        val gate = CompletableDeferred<Unit>()
        transport.gate = gate
        val repository = repository()

        val first = async { repository.refresh() }
        val second = async { repository.refresh() }
        testScheduler.runCurrent()
        val refreshing = repository.observeJams().first { it.freshness.isRefreshing }
        repository.observeJams().first()
        testScheduler.runCurrent()
        gate.complete(Unit)

        assertEquals(first.await(), second.await())
        assertTrue(refreshing.freshness.isRefreshing)
        assertEquals(1, transport.calls)
        assertFalse(repository.observeJams().first().freshness.isRefreshing)
    }

    // ---- admin-add-song-to-setlist: the admin read ----

    @Test
    fun `with no passphrase stored the refresh is the anonymous GET and nothing is posted`() = runTest {
        val repository = repository()

        repository.refresh()

        assertEquals(1, transport.calls)
        assertTrue(post.bodies.isEmpty())
    }

    @Test
    fun `with a passphrase stored the refresh is the admin read, and the cache holds the draft's songs`() = runTest {
        val repository = repository()
        adminStore().save(TEST_VALUE)
        post.answer =
            adminBody(jamJson("2026-07-25", "BORRADOR", "[${rowJson(2, "red-house")},${rowJson(1, "crossroads")}]"))

        val outcome = repository.refresh()

        assertEquals(JamsRefreshOutcome.Updated(1, emptyList(), emptyList(), emptyList(), adminRead = true), outcome)
        assertTrue(outcome.toLogLine().endsWith(" (admin read)"))
        assertFalse(outcome.toLogLine().contains(TEST_VALUE))
        assertEquals(0, transport.calls)
        assertEquals(listOf("readJams"), post.actions())
        val upcoming = checkNotNull(repository.observeJams().first().upcoming)
        assertEquals(JamStatus.DRAFT, upcoming.status)
        assertEquals(listOf("crossroads", "red-house"), upcoming.songs().map { it.songId.value })
        assertTrue(upcoming.songs().all { it.lineup == Lineup.default() })
        // The musicians' view of the same cache still withholds the draft.
        assertEquals(Setlist.Withheld, upcoming.setlistForMusicians())
    }

    @Test
    fun `for the admin a draft with no tab yet is an empty available setlist`() = runTest {
        val repository = repository()
        adminStore().save(TEST_VALUE)
        post.answer = adminBody(jamJson("2026-07-25", "BORRADOR", null, """{"code":"missing_tab","message":"m"}"""))

        repository.refresh()

        assertEquals(Setlist.Available(emptyList()), repository.observeJams().first().upcoming?.setlist)
    }

    @Test
    fun `a refused passphrase falls back to the GET and is not sent again until another one is stored`() = runTest {
        val repository = repository()
        adminStore().save(TEST_VALUE)
        post.answer = TransportResult.Body(error("invalid_passphrase"))

        assertEquals(JamsRefreshOutcome.Updated(1, emptyList(), emptyList(), emptyList()), repository.refresh())
        assertEquals(JamsRefreshOutcome.Updated(1, emptyList(), emptyList(), emptyList()), repository.refresh())

        assertEquals(1, post.bodies.size)
        assertEquals(2, transport.calls)
        assertEquals(TEST_VALUE, adminStore().passphrase())

        adminStore().save("$TEST_VALUE-new")
        post.answer = adminBody(jamJson("2026-07-25", "BORRADOR", "[${rowJson(1, "crossroads")}]"))
        assertEquals(
            JamsRefreshOutcome.Updated(1, emptyList(), emptyList(), emptyList(), adminRead = true),
            repository.refresh(),
        )
        assertEquals(2, post.bodies.size)
        assertEquals(2, transport.calls)
    }

    @Test
    fun `any other admin read failure is a failed refresh that keeps the draft songs and never falls back`() = runTest {
        val repository = repository()
        adminStore().save(TEST_VALUE)
        post.answer = adminBody(jamJson("2026-07-25", "BORRADOR", "[${rowJson(1, "crossroads")}]"))
        repository.refresh()
        val failures = listOf(
            TransportResult.Failed(DataFailure.Offline) to DataFailure.Offline,
            TransportResult.Body(error("busy")) to DataFailure.Service("busy"),
            TransportResult.Body(error("rate_limited")) to DataFailure.Service("rate_limited"),
            TransportResult.Body(error("internal_error")) to DataFailure.Service("internal_error"),
            TransportResult.Body("<html></html>") to DataFailure.InvalidResponse("body is not a JSON object"),
            TransportResult.Body("""{"schemaVersion":1,"ok":true}""") to DataFailure.InvalidResponse("missing jams"),
        )

        failures.forEach { (answer, failure) ->
            post.answer = answer

            assertEquals(answer.toString(), JamsRefreshOutcome.Failed(failure, adminRead = true), repository.refresh())
            val snapshot = repository.observeJams().first()
            assertEquals(answer.toString(), listOf("crossroads"), snapshot.upcoming?.songs()?.map { it.songId.value })
            assertEquals(answer.toString(), failure, snapshot.freshness.lastFailure)
        }
        assertEquals(0, transport.calls)
        assertEquals(TEST_VALUE, adminStore().passphrase())
    }

    private fun Jam.songs() = (setlist as Setlist.Available).songs

    /** Collects the first snapshot and runs whatever refresh that collection launched up to its request. */
    private suspend fun TestScope.collectOnce(repository: JamsRepository) {
        repository.observeJams().first()
        testScheduler.runCurrent()
    }

    /**
     * The repository over this test's store. The store runs on the test scheduler ([backgroundScope]),
     * so `runCurrent` still drives a refresh up to its request.
     */
    private fun TestScope.repository(
        transport: AppsScriptTransport = this@DefaultJamsRepositoryTest.transport,
        mapper: (List<JamDto>, Boolean) -> MappedJams = JamsMapper::map,
    ): DefaultJamsRepository {
        val store = adminStore()
        return DefaultJamsRepository(
            transport,
            dao,
            calendar,
            DataScope(backgroundScope),
            AdminWriter(post, store),
            store,
            mapper,
        )
    }

    private fun TestScope.adminStore(): AdminCredentialStore = adminStore ?: AdminCredentialStore(
        AdminCredentialStore.dataStore(backgroundScope) {
            File(folder.root, "${AdminCredentialStore.FILE_NAME}.preferences_pb")
        },
    ).also { adminStore = it }

    private fun catalogSong(id: String, title: String) =
        CatalogSongEntity(id, 1, title, "Artista del catálogo", "E", null, null, emptyList(), null)

    private fun catalogState() = SyncStateEntity("catalog", 1, 1, null)

    /** The real DAO, except that a test can make its writes fail as on a full disk. */
    private class FailingJamsDao(private val real: JamsDao) : JamsDao by real {
        var failReplace = false
        var failRecord = false

        /** When set, collecting [observeJams] throws it, as a Room read that fails. */
        var failRead: Throwable? = null

        override fun observeJams(): Flow<List<JamWithChildren>> {
            val failure = failRead ?: return real.observeJams()
            return flow { throw failure }
        }

        override suspend fun replaceJams(rows: JamRows, state: SyncStateEntity) {
            if (failReplace) throw SQLiteFullException("disk full")
            real.replaceJams(rows, state)
        }

        override suspend fun recordFailure(resource: String, attemptedAt: Long, failure: String) {
            if (failRecord) throw SQLiteFullException("disk full")
            real.recordFailure(resource, attemptedAt, failure)
        }
    }

    private class FakePost(var answer: TransportResult) : AppsScriptPostTransport {
        val bodies = mutableListOf<String>()

        fun actions(): List<String> = bodies.map { Regex("\"action\":\"([^\"]+)\"").find(it)!!.groupValues[1] }

        override suspend fun post(json: String): TransportResult {
            bodies += json
            return answer
        }
    }

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
        /** 12:00 on 20 July 2026 in Buenos Aires. */
        val JULY_20: Instant = Instant.parse("2026-07-20T15:00:00Z")

        /** 12:00 on 2 October 2026 in Buenos Aires. */
        val OCTOBER_2: Instant = Instant.parse("2026-10-02T15:00:00Z")

        const val TEST_VALUE = "not-a-real-passphrase"

        fun adminBody(vararg jams: String) =
            TransportResult.Body("""{"schemaVersion":1,"ok":true,"jams":[${jams.joinToString(",")}]}""")

        fun jamJson(date: String, status: String, setlist: String?, error: String? = null) =
            """{"date":"$date","startTime":"21:00","venue":"La Macanuda","status":"$status",""" +
                """"setlist":${setlist ?: "null"},"setlistError":${error ?: "null"}}"""

        fun rowJson(position: Int, songId: String): String {
            val slots = listOf("guitar1", "guitar2", "bass", "drums", "vocals", "harmonica", "keyboards")
                .joinToString(",") { "\"$it\":null" }
            return """{"position":"$position","songId":"$songId","title":"T","artist":"A","key":"A",""" +
                """"slots":{$slots},"extraParticipants":null}"""
        }

        fun error(code: String) = """{"schemaVersion":1,"error":{"code":"$code","message":"m"}}"""
    }
}
