package com.bbbjam.core.data.di

import com.bbbjam.core.data.AppsScriptEndpoint
import com.bbbjam.core.data.DataScope
import com.bbbjam.core.data.cache.BluesJamDatabase
import com.bbbjam.core.data.catalog.CatalogRepository
import com.bbbjam.core.data.catalog.DefaultCatalogRepository
import com.bbbjam.core.data.remote.AppsScriptTransport
import com.bbbjam.core.data.remote.OkHttpAppsScriptTransport
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

/**
 * The data layer. Needs two bindings from `:app`: `Context` (through `androidContext`) and
 * [AppsScriptEndpoint]. Everything is a `single`, built by constructor.
 */
val dataModule = module {
    single<Clock> { Clock.systemUTC() }
    single { DataScope(CoroutineScope(SupervisorJob() + Dispatchers.IO)) }
    single { OkHttpAppsScriptTransport.client() }
    single<AppsScriptTransport> { OkHttpAppsScriptTransport(get(), get<AppsScriptEndpoint>().url) }
    single { BluesJamDatabase.create(get()) }
    single { get<BluesJamDatabase>().catalogDao() }
    single<CatalogRepository> { DefaultCatalogRepository(get(), get(), get(), get()) }
}
