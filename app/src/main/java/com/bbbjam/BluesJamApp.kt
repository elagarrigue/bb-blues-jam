package com.bbbjam

import android.app.Application
import android.util.Log
import com.bbbjam.core.data.catalog.CatalogRepository
import com.bbbjam.core.data.catalog.toLogLine
import com.bbbjam.core.data.di.dataModule
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.jams.toLogLine
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SongId
import com.bbbjam.debug.debugOverrides
import com.bbbjam.debug.isDemo
import com.bbbjam.di.appModule
import com.bbbjam.feature.info.di.infoModule
import com.bbbjam.feature.nextjam.di.nextJamModule
import com.bbbjam.feature.pastjams.di.pastJamsModule
import com.bbbjam.feature.songdetail.di.songDetailModule
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Starts Koin with every module; only `:app` calls `startKoin` (D-16). Then refreshes the catalog
 * and the jams once per process start, concurrently (user approval Q3), and logs each outcome:
 * counts, ids, dates, issues and the failure kind, never the URL or a musician's name. Once both are
 * done it logs one line from the jams cache: the upcoming/past split and how many setlist songs
 * resolve in the cached catalog. An exception that escapes is logged, not fatal. In a debug build
 * with `bluesjam.demoUpcomingJam=true` the jams are decorated with the demo upcoming jam, marked
 * `(demo)` in that line.
 */
class BluesJamApp : Application() {
    private val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            Log.e(LOG_TAG, "startup refresh failed", error)
        },
    )

    override fun onCreate() {
        super.onCreate()
        val koin = startKoin {
            androidContext(this@BluesJamApp)
            modules(appModule, dataModule, infoModule, nextJamModule, pastJamsModule, songDetailModule)
        }.koin
        // Debug only, behind bluesjam.demoUpcomingJam: the demo upcoming jam (debug-demo-upcoming-jam).
        // Loaded after dataModule so it overrides JamsRepository; release loads nothing.
        koin.loadModules(koin.debugOverrides(), allowOverride = true)
        val catalog = koin.get<CatalogRepository>()
        val jams = koin.get<JamsRepository>()
        appScope.launch {
            val catalogDone = async { Log.i(LOG_TAG, catalog.refresh().toLogLine()) }
            val jamsDone = async { Log.i(LOG_TAG, jams.refresh().toLogLine()) }
            catalogDone.await()
            jamsDone.await()
            val catalogIds = catalog.observeCatalog().first().songs.map { it.id }.toSet()
            Log.i(LOG_TAG, jams.observeJams().first().toCacheLine { it in catalogIds })
        }
    }

    private companion object {
        const val LOG_TAG = "BluesJam"

        /**
         * `jams cache: upcoming <date|none>[ draft][ (demo)], past N, songs S (C from catalog)`: counts
         * and dates only. `draft` marks an upcoming jam whose status is DRAFT (`unpublished-setlist-state`);
         * `(demo)` marks the debug-only demo jam, so evidence never mistakes it for real data.
         */
        fun JamsSnapshot.toCacheLine(inCatalog: (SongId) -> Boolean): String {
            val songs = (listOfNotNull(upcoming) + past).flatMap { (it.setlist as? Setlist.Available)?.songs.orEmpty() }
            val upcomingText = upcoming?.let { jam ->
                val draft = if (jam.status == JamStatus.DRAFT) " draft" else ""
                val demo = if (jam.isDemo()) " (demo)" else ""
                "${jam.date}$draft$demo"
            } ?: "none"
            return "jams cache: upcoming $upcomingText, past ${past.size}, " +
                "songs ${songs.size} (${songs.count { inCatalog(it.songId) }} from catalog)"
        }
    }
}
