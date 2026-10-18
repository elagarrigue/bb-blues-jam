package com.bbbjam.core.assistant.di

import com.bbbjam.core.assistant.AssistantHttp
import com.bbbjam.core.assistant.AssistantStatusProbe
import com.bbbjam.core.assistant.DefaultSetlistAssistant
import com.bbbjam.core.assistant.SetlistAssistant
import org.koin.dsl.module

/**
 * The setlist assistant (`assistant-agent-core`, moved here by `core-assistant-module`). Needs an
 * `AssistantCredentials` from `:app` and the catalog and jams repositories and the calendar from
 * `dataModule`; the agent reads those and nothing else. Named apart from the feature's `assistantModule`.
 */
val assistantCoreModule = module {
    single<SetlistAssistant> {
        DefaultSetlistAssistant(get(), get(), get(), get(), AssistantHttp.executorFactory(), AssistantStatusProbe())
    }
}
