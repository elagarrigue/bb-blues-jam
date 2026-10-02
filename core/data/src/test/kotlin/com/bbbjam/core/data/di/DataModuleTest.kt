package com.bbbjam.core.data.di

import android.content.Context
import android.content.ContextWrapper
import com.bbbjam.core.data.AppsScriptEndpoint
import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.Fixtures
import com.bbbjam.core.data.cache.BluesJamDatabase
import com.bbbjam.core.data.catalog.CatalogRepository
import com.bbbjam.core.data.catalog.RefreshOutcome
import java.time.Clock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class DataModuleTest {

    @Test
    fun `dataModule resolves one catalog repository once Context and the endpoint are bound`() {
        val database = Fixtures.inMemoryDatabase()
        // What :app provides (androidContext, the BuildConfig endpoint), plus an in-memory database in
        // place of the file one, which needs a real Context.
        val testModule = module {
            single<Context> { ContextWrapper(null) }
            single { AppsScriptEndpoint.of("") }
            single<BluesJamDatabase> { database }
        }
        val app = koinApplication { modules(dataModule, testModule) }
        try {
            val first = app.koin.get<CatalogRepository>()

            // single, not factory: the repository owns the single-flight state.
            assertSame(first, app.koin.get<CatalogRepository>())
            assertEquals(Clock.systemUTC().zone, app.koin.get<Clock>().zone)
            assertEquals(RefreshOutcome.Failed(DataFailure.NotConfigured), runBlocking { first.refresh() })
        } finally {
            app.close()
            database.close()
        }
    }
}
