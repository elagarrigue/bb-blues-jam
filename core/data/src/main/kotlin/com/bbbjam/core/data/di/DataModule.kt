package com.bbbjam.core.data.di

import android.util.Log
import com.bbbjam.core.data.AppsScriptEndpoint
import com.bbbjam.core.data.DataScope
import com.bbbjam.core.data.admin.AdminCredentialStore
import com.bbbjam.core.data.admin.AdminSession
import com.bbbjam.core.data.admin.AdminWriter
import com.bbbjam.core.data.admin.DefaultAdminSession
import com.bbbjam.core.data.cache.BluesJamDatabase
import com.bbbjam.core.data.catalog.CatalogRepository
import com.bbbjam.core.data.catalog.DefaultCatalogRepository
import com.bbbjam.core.data.jams.DefaultJamsRepository
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.data.remote.AppsScriptPostTransport
import com.bbbjam.core.data.remote.AppsScriptTransport
import com.bbbjam.core.data.remote.OkHttpAppsScriptTransport
import com.bbbjam.core.data.setlist.DefaultSetlistRepository
import com.bbbjam.core.data.setlist.SetlistRepository
import java.time.Clock
import org.koin.dsl.module

private const val LOG_TAG = "BluesJam"

/**
 * The data layer. Needs two bindings from `:app`: `Context` (through `androidContext`) and
 * [AppsScriptEndpoint]. Everything is a `single`, built by constructor. Today's date for jams comes
 * from [JamCalendar] in Buenos Aires (user approval P7 of `jams-repository-cache`). The GET and
 * POST transports are the same OkHttp instance; [AdminSession] keeps the verified passphrase in
 * DataStore (`admin-passphrase-login`), and [AdminWriter] sends it with every admin write
 * (`apps-script-write-auth`), the jams refresh's admin read and [SetlistRepository]'s mutations
 * (`admin-add-song-to-setlist`).
 */
val dataModule = module {
    single<Clock> { Clock.systemUTC() }
    single { JamCalendar(get(), JamCalendar.BUENOS_AIRES) }
    single { DataScope.create { error -> Log.e(LOG_TAG, "background data work failed", error) } }
    single { OkHttpAppsScriptTransport.client() }
    single { OkHttpAppsScriptTransport(get(), get<AppsScriptEndpoint>().url) }
    single<AppsScriptTransport> { get<OkHttpAppsScriptTransport>() }
    single<AppsScriptPostTransport> { get<OkHttpAppsScriptTransport>() }
    single { BluesJamDatabase.create(get()) }
    single { get<BluesJamDatabase>().catalogDao() }
    single<CatalogRepository> { DefaultCatalogRepository(get(), get(), get(), get()) }
    single { get<BluesJamDatabase>().jamsDao() }
    single<JamsRepository> { DefaultJamsRepository(get(), get(), get(), get(), get(), get()) }
    single { AdminCredentialStore(AdminCredentialStore.dataStore(get())) }
    single<AdminSession> { DefaultAdminSession(get(), get()) }
    single { AdminWriter(get(), get()) }
    single { get<BluesJamDatabase>().setlistDao() }
    single<SetlistRepository> { DefaultSetlistRepository(get(), get(), get(), get()) }
}
