package com.bbbjam.feature.catalog

import com.bbbjam.core.data.admin.AdminSession
import com.bbbjam.core.data.catalog.CatalogRepository
import com.bbbjam.core.data.catalog.edit.CatalogEditRepository
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.data.testing.FakeAdminSession
import com.bbbjam.core.data.testing.FakeCatalogRepository
import com.bbbjam.core.data.testing.FakeJamsRepository
import com.bbbjam.feature.catalog.di.catalogModule
import java.time.Clock
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class CatalogModuleTest {
    @Test
    fun `catalogModule resolves a new presenter each time once the data bindings exist`() {
        val dataBindings = module {
            single<CatalogRepository> { FakeCatalogRepository() }
            single<CatalogEditRepository> { FakeCatalogEdits() }
            single<JamsRepository> { FakeJamsRepository() }
            single { JamCalendar(Clock.systemUTC(), JamCalendar.BUENOS_AIRES) }
            single<AdminSession> { FakeAdminSession() }
        }
        val app = koinApplication { modules(catalogModule, dataBindings) }
        try {
            // factory, not single: presenters hold no state; the composition does.
            assertNotSame(app.koin.get<CatalogPresenter>(), app.koin.get<CatalogPresenter>())
        } finally {
            app.close()
        }
    }
}
