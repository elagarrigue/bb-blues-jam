package com.bbbjam.feature.assistant.di

import android.util.Log
import com.bbbjam.feature.assistant.AssistantChat
import com.bbbjam.feature.assistant.AssistantPresenter
import com.bbbjam.feature.assistant.JamCreationRunner
import com.bbbjam.feature.assistant.ProposalApplier
import com.bbbjam.feature.assistant.RunLock
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

private const val LOG_TAG = "BluesJam"

/**
 * The Asistente feature's Koin module. `AssistantChat` is a `single` (the conversation lives for the
 * process, Decision 1); the presenter is a `factory`. `SetlistAssistant` comes from `dataModule`.
 * The chat's scope logs only the exception's class name, never its message (it could carry a URL).
 */
val assistantModule = module {
    single {
        AssistantChat(
            assistant = get(),
            scope = CoroutineScope(
                SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
                    Log.e(LOG_TAG, "assistant request failed: ${error::class.java.name}")
                },
            ),
        )
    }
    // The load of a proposal outlives the tab too; the write contracts are bound in `:app` to the action registry.
    // One write at a time across the list loads and the jam creations.
    single { RunLock() }
    single {
        ProposalApplier(
            adder = get(),
            remover = get(),
            mover = get(),
            creator = get(),
            runLock = get(),
            scope = CoroutineScope(
                SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
                    Log.e(LOG_TAG, "assistant apply failed: ${error::class.java.name}")
                },
            ),
        )
    }
    // Creating a jam outlives the tab too; `JamCreator` is bound in `:app` to the action registry.
    single {
        JamCreationRunner(
            creator = get(),
            jams = get(),
            runLock = get(),
            scope = CoroutineScope(
                SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
                    Log.e(LOG_TAG, "assistant create jam failed: ${error::class.java.name}")
                },
            ),
        )
    }
    factory { AssistantPresenter(get(), get(), get(), get(), get()) }
}
