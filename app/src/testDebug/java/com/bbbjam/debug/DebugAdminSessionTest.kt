package com.bbbjam.debug

import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.admin.AdminSession
import com.bbbjam.core.data.admin.LoginOutcome
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsRefreshOutcome
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.data.jams.JamsSnapshot
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class DebugAdminSessionTest {
    private class FakeAdminSession(isAdmin: Boolean) : AdminSession {
        val isAdmin = MutableStateFlow(isAdmin)
        val logInCalls = mutableListOf<String>()
        var logOutCalls = 0

        override fun observeIsAdmin(): Flow<Boolean> = isAdmin

        override suspend fun logIn(passphrase: String): LoginOutcome {
            logInCalls += passphrase
            return LoginOutcome.WrongPassphrase
        }

        override suspend fun logOut() {
            logOutCalls++
            isAdmin.value = false
        }
    }

    private class FakeJams : JamsRepository {
        override fun observeJams(): Flow<JamsSnapshot> = emptyFlow()

        override suspend fun refresh(): JamsRefreshOutcome = JamsRefreshOutcome.Failed(DataFailure.Offline)
    }

    @Test
    fun forcedAdminEmitsTrueWhileTheRealSessionIsLoggedOut() = runBlocking {
        val real = FakeAdminSession(isAdmin = false)

        assertTrue(DebugAdminSession(real).observeIsAdmin().first())
    }

    @Test
    fun logOutEndsTheForcedStateAndCallsTheRealLogOutOnce() = runBlocking {
        val real = FakeAdminSession(isAdmin = true)
        val session = DebugAdminSession(real)

        session.logOut()

        assertEquals(1, real.logOutCalls)
        assertFalse(session.observeIsAdmin().first())
        real.isAdmin.value = true
        assertTrue("after logout it follows the real value", session.observeIsAdmin().first())
    }

    @Test
    fun logInDelegatesOnceWithTheSameArgument() = runBlocking {
        val real = FakeAdminSession(isAdmin = false)

        val outcome = DebugAdminSession(real).logIn("typed text")

        assertEquals(listOf("typed text"), real.logInCalls)
        assertSame(LoginOutcome.WrongPassphrase, outcome)
    }

    @Test
    fun adminFlagAloneOverridesOnlyTheAdminSession() {
        val koin = koinWithRealBindings()
        val realJams = koin.get<JamsRepository>()

        koin.loadModules(koin.debugOverrides(DebugFlags(debugAdmin = true)), allowOverride = true)

        assertTrue(koin.get<AdminSession>() is DebugAdminSession)
        assertSame(realJams, koin.get<JamsRepository>())
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
    fun bothFlagsOverrideBothAndNoFlagOverridesNothing() {
        val koin = koinWithRealBindings()
        assertEquals(emptyList<Any>(), koin.debugOverrides(DebugFlags()))

        koin.loadModules(
            koin.debugOverrides(DebugFlags(demoUpcomingJam = true, debugAdmin = true)),
            allowOverride = true,
        )

        assertTrue(koin.get<JamsRepository>() is DemoUpcomingJamRepository)
        assertTrue(koin.get<AdminSession>() is DebugAdminSession)
    }

    private fun koinWithRealBindings(): Koin = koinApplication {
        modules(
            module {
                single<AdminSession> { FakeAdminSession(isAdmin = false) }
                single<JamsRepository> { FakeJams() }
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
