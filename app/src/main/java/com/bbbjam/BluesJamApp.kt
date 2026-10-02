package com.bbbjam

import android.app.Application
import android.util.Log
import com.bbbjam.core.data.catalog.CatalogRepository
import com.bbbjam.core.data.catalog.toLogLine
import com.bbbjam.core.data.di.dataModule
import com.bbbjam.di.appModule
import com.bbbjam.feature.info.di.infoModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Starts Koin with every module; only `:app` calls `startKoin` (D-16). Then refreshes the catalog
 * once per process start (user approval Q3) and logs the outcome: counts, ids, issues and the
 * failure kind, never the URL.
 */
class BluesJamApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val koin = startKoin {
            androidContext(this@BluesJamApp)
            modules(appModule, dataModule, infoModule)
        }.koin
        val catalog = koin.get<CatalogRepository>()
        appScope.launch {
            Log.i(LOG_TAG, catalog.refresh().toLogLine())
        }
    }

    private companion object {
        const val LOG_TAG = "BluesJam"
    }
}
