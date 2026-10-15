package com.bbbjam.feature.pastjams

import com.bbbjam.core.data.admin.AdminSession
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.data.setlist.JamCreator
import com.bbbjam.core.data.setlist.SetlistJamEditRepository
import com.bbbjam.core.data.setlist.SetlistJamStatusRepository
import com.bbbjam.core.data.setlist.SetlistPublishRepository
import com.bbbjam.feature.pastjams.di.pastJamsModule
import java.time.Clock
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class PastJamsModuleTest {
    @Test
    fun `pastJamsModule resolves new list and detail presenters each time once the data bindings exist`() {
        val dataBindings = module {
            single<JamsRepository> { FakeJamsRepository() }
            single { JamCalendar(Clock.systemUTC(), JamCalendar.BUENOS_AIRES) }
            single<AdminSession> { FakeAdminSession() }
            single<SetlistJamStatusRepository> { FakeJamStatusRepository() }
            single<SetlistPublishRepository> { FakePublishRepository() }
            single<JamCreator> { FakeJamCreator() }
            single<SetlistJamEditRepository> { FakeJamEditRepository() }
        }
        val app = koinApplication { modules(pastJamsModule, dataBindings) }
        try {
            val first = app.koin.get<PastJamsPresenter>()
            val second = app.koin.get<PastJamsPresenter>()
            // factory, not single: presenters hold no state; the composition does.
            assertNotSame(first, second)
            assertNotSame(app.koin.get<PastJamDetailPresenter>(), app.koin.get<PastJamDetailPresenter>())
        } finally {
            app.close()
        }
    }
}
