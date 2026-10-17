package com.bbbjam.feature.assistant

import com.bbbjam.core.data.assistant.SetlistAssistant
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.data.setlist.JamCreator
import com.bbbjam.core.data.setlist.SetlistSongAdder
import com.bbbjam.core.data.setlist.SetlistSongMover
import com.bbbjam.core.data.setlist.SetlistSongRemover
import com.bbbjam.feature.assistant.chat.AssistantChat
import com.bbbjam.feature.assistant.di.assistantModule
import com.bbbjam.feature.assistant.run.JamCreationRunner
import com.bbbjam.feature.assistant.run.ProposalApplier
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class AssistantModuleTest {
    @Test
    fun `the chat is one instance for the process and the presenter is new each time`() {
        val dataBindings = module {
            single<SetlistAssistant> { FakeSetlistAssistant() }
            single<SetlistSongAdder> { FakeSetlistSongAdder() }
            single<SetlistSongRemover> { FakeSetlistSongRemover() }
            single<SetlistSongMover> { FakeSetlistSongMover() }
            single<JamCreator> { FakeJamCreator() }
            single { testCalendar() }
            single<JamsRepository> { FakeJamsRepository() }
        }
        val app = koinApplication { modules(assistantModule, dataBindings) }
        try {
            assertSame(app.koin.get<AssistantChat>(), app.koin.get<AssistantChat>())
            assertNotSame(app.koin.get<AssistantPresenter>(), app.koin.get<AssistantPresenter>())
            assertSame(app.koin.get<ProposalApplier>(), app.koin.get<ProposalApplier>())
            assertSame(app.koin.get<JamCreationRunner>(), app.koin.get<JamCreationRunner>())
        } finally {
            app.close()
        }
    }
}
