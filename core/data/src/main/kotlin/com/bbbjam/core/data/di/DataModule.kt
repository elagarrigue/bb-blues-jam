package com.bbbjam.core.data.di

import android.util.Log
import com.bbbjam.core.data.AppsScriptEndpoint
import com.bbbjam.core.data.DataScope
import com.bbbjam.core.data.cache.BluesJamDatabase
import com.bbbjam.core.data.catalog.CatalogRepository
import com.bbbjam.core.data.catalog.DefaultCatalogRepository
import com.bbbjam.core.data.jams.DefaultJamsRepository
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.data.remote.AppsScriptTransport
import com.bbbjam.core.data.remote.OkHttpAppsScriptTransport
import java.time.Clock
import org.koin.dsl.module

private const val LOG_TAG = "BluesJam"

/**
 * The data layer. Needs two bindings from `:app`: `Context` (through `androidContext`) and
 * [AppsScriptEndpoint]. Everything is a `single`, built by constructor. Today's date for jams comes
 * from [JamCalendar] in Buenos Aires (user approval P7 of `jams-repository-cache`).
 */
val dataModule = module {
    single<Clock> { Clock.systemUTC() }
    single { JamCalendar(get(), JamCalendar.BUENOS_AIRES) }
    single { DataScope.create { error -> Log.e(LOG_TAG, "background data work failed", error) } }
    single { OkHttpAppsScriptTransport.client() }
    single<AppsScriptTransport> { OkHttpAppsScriptTransport(get(), get<AppsScriptEndpoint>().url) }
    single { BluesJamDatabase.create(get()) }
    single { get<BluesJamDatabase>().catalogDao() }
    single<CatalogRepository> { DefaultCatalogRepository(get(), get(), get(), get()) }
    single { get<BluesJamDatabase>().jamsDao() }
    single<JamsRepository> { DefaultJamsRepository(get(), get(), get(), get()) }
}
