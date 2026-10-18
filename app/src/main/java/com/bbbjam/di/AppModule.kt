package com.bbbjam.di

import com.bbbjam.BuildConfig
import com.bbbjam.core.data.AppsScriptEndpoint
import com.bbbjam.core.data.assistant.AssistantCredentials
import com.bbbjam.core.data.setlist.JamCreator
import com.bbbjam.core.data.setlist.SetlistAssignmentRepository
import com.bbbjam.core.data.setlist.SetlistExtraParticipantRepository
import com.bbbjam.core.data.setlist.SetlistJamCreationRepository
import com.bbbjam.core.data.setlist.SetlistJamEditRepository
import com.bbbjam.core.data.setlist.SetlistJamStatusRepository
import com.bbbjam.core.data.setlist.SetlistKeyChangeRepository
import com.bbbjam.core.data.setlist.SetlistLineupRepository
import com.bbbjam.core.data.setlist.SetlistMoveRepository
import com.bbbjam.core.data.setlist.SetlistPublishRepository
import com.bbbjam.core.data.setlist.SetlistRepository
import com.bbbjam.core.data.setlist.SetlistSlotClearRepository
import com.bbbjam.core.data.setlist.SetlistSongAddRepository
import com.bbbjam.core.data.setlist.SetlistSongAdder
import com.bbbjam.core.data.setlist.SetlistSongMover
import com.bbbjam.core.data.setlist.SetlistSongRemoveRepository
import com.bbbjam.core.data.setlist.SetlistSongRemover
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
    // The assistant applies proposals through narrow `:core:data` contracts, bound straight to the one
    // SetlistRepository instance (no registry in between).
    single<SetlistSongAdder> {
        val repository = get<SetlistRepository>()
        SetlistSongAdder { date, songId, key -> repository.addSong(date, songId, key) }
    }
    single<SetlistSongRemover> {
        val repository = get<SetlistRepository>()
        SetlistSongRemover { date, songId -> repository.removeSong(date, songId) }
    }
    single<SetlistSongMover> {
        val repository = get<SetlistRepository>()
        SetlistSongMover { date, songId, position -> repository.moveSong(date, songId, position) }
    }
    single<JamCreator> {
        val repository = get<SetlistRepository>()
        JamCreator { date, startTime, venue -> repository.createJam(date, startTime, venue) }
    }

    // Every feature sees only the narrow setlist contracts it calls (`setlist-mutation-contracts`), all bound
    // to the one SetlistRepository instance, so the in-memory sending and failure entries are shared
    // between Próxima jam, the song detail, the Jams tab and the assistant.
    single<SetlistJamStatusRepository> { get<SetlistRepository>() }
    single<SetlistJamEditRepository> { get<SetlistRepository>() }
    single<SetlistPublishRepository> { get<SetlistRepository>() }
    single<SetlistAssignmentRepository> { get<SetlistRepository>() }
    single<SetlistExtraParticipantRepository> { get<SetlistRepository>() }
    single<SetlistJamCreationRepository> { get<SetlistRepository>() }
    single<SetlistKeyChangeRepository> { get<SetlistRepository>() }
    single<SetlistLineupRepository> { get<SetlistRepository>() }
    single<SetlistMoveRepository> { get<SetlistRepository>() }
    single<SetlistSlotClearRepository> { get<SetlistRepository>() }
    single<SetlistSongAddRepository> { get<SetlistRepository>() }
    single<SetlistSongRemoveRepository> { get<SetlistRepository>() }
}
