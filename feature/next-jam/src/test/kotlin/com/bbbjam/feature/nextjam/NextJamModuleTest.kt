package com.bbbjam.feature.nextjam

import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.feature.nextjam.di.nextJamModule
import java.time.Clock
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class NextJamModuleTest {
    @Test
    fun `nextJamModule resolves a new presenter each time once the data bindings exist`() {
        val dataBindings = module {
            single<JamsRepository> { FakeJamsRepository() }
            single { JamCalendar(Clock.systemUTC(), JamCalendar.BUENOS_AIRES) }
        }
        val app = koinApplication { modules(nextJamModule, dataBindings) }
        try {
            val first = app.koin.get<NextJamPresenter>()
            val second = app.koin.get<NextJamPresenter>()
            // factory, not single: presenters hold no state; the composition does.
            assertNotSame(first, second)
        } finally {
            app.close()
        }
    }
}
