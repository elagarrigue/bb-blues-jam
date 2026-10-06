package com.bbbjam.feature.nextjam

import com.bbbjam.core.data.admin.AdminSession
import com.bbbjam.core.data.catalog.CatalogRepository
import com.bbbjam.core.data.catalog.CatalogSnapshot
import com.bbbjam.core.data.catalog.RefreshOutcome
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.data.setlist.SetlistRepository
import com.bbbjam.feature.nextjam.di.nextJamModule
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class NextJamModuleTest {
    @Test
    fun `nextJamModule resolves new presenters each time once the data bindings exist`() {
        val dataBindings = module {
            single<JamsRepository> { FakeJamsRepository() }
            single { JamCalendar(Clock.systemUTC(), JamCalendar.BUENOS_AIRES) }
            single<AdminSession> { FakeAdminSession() }
            single<SetlistRepository> { FakeSetlistRepository() }
            single<CatalogRepository> { NoCatalog }
        }
        val app = koinApplication { modules(nextJamModule, dataBindings) }
        try {
            val first = app.koin.get<NextJamPresenter>()
            val second = app.koin.get<NextJamPresenter>()
            // factory, not single: presenters hold no state; the composition does.
            assertNotSame(first, second)
            assertNotSame(app.koin.get<AddSongPresenter>(), app.koin.get<AddSongPresenter>())
        } finally {
            app.close()
        }
    }
}

private object NoCatalog : CatalogRepository {
    override fun observeCatalog(): Flow<CatalogSnapshot> = emptyFlow()

    override suspend fun refresh(): RefreshOutcome = error("not called")
}
