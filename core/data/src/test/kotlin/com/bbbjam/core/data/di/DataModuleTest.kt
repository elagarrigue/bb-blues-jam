package com.bbbjam.core.data.di

import android.content.Context
import android.content.ContextWrapper
import com.bbbjam.core.data.AppsScriptEndpoint
import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.Fixtures
import com.bbbjam.core.data.admin.AdminCredentialStore
import com.bbbjam.core.data.admin.AdminSession
import com.bbbjam.core.data.admin.AdminWriter
import com.bbbjam.core.data.admin.LoginOutcome
import com.bbbjam.core.data.cache.BluesJamDatabase
import com.bbbjam.core.data.catalog.CatalogRepository
import com.bbbjam.core.data.catalog.RefreshOutcome
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsRefreshOutcome
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.data.remote.AppsScriptPostTransport
import com.bbbjam.core.data.remote.AppsScriptTransport
import com.bbbjam.core.data.setlist.SetlistRepository
import java.io.File
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class DataModuleTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `dataModule resolves one catalog and one jams repository once Context and the endpoint are bound`() {
        val database = Fixtures.inMemoryDatabase()
        // What :app provides (androidContext, the BuildConfig endpoint), plus an in-memory database in
        // place of the file one, which needs a real Context.
        val testModule = module {
            single<Context> { ContextWrapper(null) }
            single { AppsScriptEndpoint.of("") }
            single<BluesJamDatabase> { database }
            // The jams refresh reads the store (admin-add-song-to-setlist); the production file needs
            // a real Context, so the store is the same factory over a temp file.
            single {
                AdminCredentialStore(
                    AdminCredentialStore.dataStore {
                        File(folder.root, "${AdminCredentialStore.FILE_NAME}.preferences_pb")
                    },
                )
            }
        }
        val app = koinApplication { modules(dataModule, testModule) }
        try {
            val first = app.koin.get<CatalogRepository>()

            // single, not factory: the repository owns the single-flight state.
            assertSame(first, app.koin.get<CatalogRepository>())
            assertEquals(Clock.systemUTC().zone, app.koin.get<Clock>().zone)
            assertEquals(RefreshOutcome.Failed(DataFailure.NotConfigured), runBlocking { first.refresh() })

            val jams = app.koin.get<JamsRepository>()
            assertSame(jams, app.koin.get<JamsRepository>())
            assertEquals(ZoneId.of("America/Argentina/Buenos_Aires"), app.koin.get<JamCalendar>().zone)
            assertEquals(JamsRefreshOutcome.Failed(DataFailure.NotConfigured), runBlocking { jams.refresh() })

            // admin-passphrase-login: one session, and GET and POST share the one transport. Resolving
            // it does not open the DataStore file (produceFile is lazy), which needs a real Context.
            val session = app.koin.get<AdminSession>()
            assertSame(session, app.koin.get<AdminSession>())
            assertSame(app.koin.get<AppsScriptTransport>(), app.koin.get<AppsScriptPostTransport>())
            assertEquals(LoginOutcome.WrongPassphrase, runBlocking { session.logIn("   ") })

            // apps-script-write-auth: one shared write path.
            assertSame(app.koin.get<AdminWriter>(), app.koin.get<AdminWriter>())

            // admin-add-song-to-setlist: one setlist repository, whose entries live in memory.
            assertSame(app.koin.get<SetlistRepository>(), app.koin.get<SetlistRepository>())
        } finally {
            app.close()
            database.close()
        }
    }
}
