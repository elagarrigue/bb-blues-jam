package com.bbbjam.debug

import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.admin.AdminSession
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsRefreshOutcome
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.testing.FakeAdminSession
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class DebugOverridesTest {
    private class FakeEmptyJams : JamsRepository {
        override fun observeJams(): Flow<JamsSnapshot> = emptyFlow()

        override suspend fun refresh(): JamsRefreshOutcome = JamsRefreshOutcome.Failed(DataFailure.Offline)
    }

    @Test
    fun demoFlagAloneOverridesOnlyTheJams() {
        val koin = koinWithRealBindings()
        val realAdmin = koin.get<AdminSession>()

        koin.loadModules(koin.debugOverrides(DebugFlags(demoUpcomingJam = true)), allowOverride = true)

        assertTrue(koin.get<JamsRepository>() is DemoUpcomingJamRepository)
        assertSame(realAdmin, koin.get<AdminSession>())
    }

    @Test
    fun liveDemoFlagAloneOverridesOnlyTheJams() {
        val koin = koinWithRealBindings()
        val realAdmin = koin.get<AdminSession>()

        koin.loadModules(
            koin.debugOverrides(DebugFlags(demoUpcomingJamLive = true), log = {}),
            allowOverride = true,
        )

        assertTrue(koin.get<JamsRepository>() is DemoUpcomingJamRepository)
        assertSame(realAdmin, koin.get<AdminSession>())
    }

    @Test
    fun noFlagOverridesNothing() {
        assertEquals(emptyList<Any>(), koinWithRealBindings().debugOverrides(DebugFlags()))
    }

    private fun koinWithRealBindings(): Koin = koinApplication {
        modules(
            module {
                single<AdminSession> { FakeAdminSession(isAdmin = false) }
                single<JamsRepository> { FakeEmptyJams() }
                single {
                    JamCalendar(
                        Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC),
                        JamCalendar.BUENOS_AIRES,
                    )
                }
            },
        )
    }.koin
}
