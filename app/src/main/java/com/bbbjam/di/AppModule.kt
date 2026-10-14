package com.bbbjam.di

import com.bbbjam.BuildConfig
import com.bbbjam.actions.RegistrySongAdder
import com.bbbjam.actions.SetlistActionRegistry
import com.bbbjam.core.data.AppsScriptEndpoint
import com.bbbjam.core.data.assistant.AssistantCredentials
import com.bbbjam.core.data.setlist.SetlistRepository
import com.bbbjam.core.data.setlist.SetlistSongAdder
import com.bbbjam.core.ui.link.ExternalLinkOpener
import com.bbbjam.link.IntentLinkOpener
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Android implementations of `:core:*` contracts that need a Context, and the build-time Apps Script
 * URL and the assistant key (D-22), both from the git-ignored `local.properties` that `dataModule` reads through.
 */
val appModule = module {
    single<ExternalLinkOpener> { IntentLinkOpener(androidContext()) }
    single { AppsScriptEndpoint.of(BuildConfig.APPS_SCRIPT_URL) }
    single { AssistantCredentials.of(BuildConfig.DEVEXPERT_KEY) }
    single { SetlistActionRegistry(get<SetlistRepository>()) }
    single<SetlistSongAdder> { RegistrySongAdder(get()) }
}
