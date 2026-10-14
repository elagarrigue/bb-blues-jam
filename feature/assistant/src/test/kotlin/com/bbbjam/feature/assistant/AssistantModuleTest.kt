package com.bbbjam.feature.assistant

import com.bbbjam.core.data.assistant.SetlistAssistant
import com.bbbjam.feature.assistant.di.assistantModule
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class AssistantModuleTest {
    @Test
    fun `the chat is one instance for the process and the presenter is new each time`() {
        val dataBindings = module { single<SetlistAssistant> { FakeSetlistAssistant() } }
        val app = koinApplication { modules(assistantModule, dataBindings) }
        try {
            assertSame(app.koin.get<AssistantChat>(), app.koin.get<AssistantChat>())
            assertNotSame(app.koin.get<AssistantPresenter>(), app.koin.get<AssistantPresenter>())
        } finally {
            app.close()
        }
    }
}
