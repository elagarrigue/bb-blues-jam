package com.bbbjam.core.assistant.di

import com.bbbjam.core.assistant.AssistantCredentials
import com.bbbjam.core.assistant.FakeCatalog
import com.bbbjam.core.assistant.FakePastJams
import com.bbbjam.core.assistant.SetlistAssistant
import com.bbbjam.core.assistant.fixedCalendar
import com.bbbjam.core.data.catalog.CatalogRepository
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class AssistantCoreModuleTest {
    @Test
    fun `assistantCoreModule resolves one assistant that is not configured without a key`() {
        // What :app and dataModule provide: the credentials, the two repositories and the calendar.
        val provided = module {
            single { AssistantCredentials.of("") }
            single<CatalogRepository> { FakeCatalog(emptyList()) }
            single<JamsRepository> { FakePastJams() }
            single<JamCalendar> { fixedCalendar() }
        }
        val app = koinApplication { modules(assistantCoreModule, provided) }
        try {
            val assistant = app.koin.get<SetlistAssistant>()

            // single, not factory: the status probe and the HTTP factory belong to one assistant.
            assertSame(assistant, app.koin.get<SetlistAssistant>())
            assertFalse(assistant.isConfigured)
        } finally {
            app.close()
        }
    }
}
