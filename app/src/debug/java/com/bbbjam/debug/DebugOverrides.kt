package com.bbbjam.debug

import android.util.Log
import com.bbbjam.BuildConfig
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamStatus
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The debug flags read from `local.properties` into `BuildConfig`. A parameter of
 * [debugOverrides] so a JVM test can turn each one on and off on its own.
 */
internal data class DebugFlags(
    val demoUpcomingJam: Boolean = false,
    val demoUpcomingJamDraft: Boolean = false,
    val demoUpcomingJamCreated: Boolean = false,
    val demoUpcomingJamLive: Boolean = false,
) {
    companion object {
        /** The flags of this debug build. */
        val fromBuildConfig = DebugFlags(
            demoUpcomingJam = BuildConfig.DEMO_UPCOMING_JAM,
            demoUpcomingJamDraft = BuildConfig.DEMO_UPCOMING_JAM_DRAFT,
            demoUpcomingJamCreated = BuildConfig.DEMO_UPCOMING_JAM_CREATED,
            demoUpcomingJamLive = BuildConfig.DEMO_UPCOMING_JAM_LIVE,
        )
    }
}

/**
 * Debug variant of the overrides `BluesJamApp` loads after every other module, one per flag on, each
 * independent of the others:
 * - `bluesjam.demoUpcomingJam=true` ([BuildConfig.DEMO_UPCOMING_JAM]) rebinds [JamsRepository] to
 *   [DemoUpcomingJamRepository] around the real one; with `bluesjam.demoUpcomingJamDraft=true` as
 *   well ([BuildConfig.DEMO_UPCOMING_JAM_DRAFT], `unpublished-setlist-state`) the demo jam is a draft;
 *   with `bluesjam.demoUpcomingJamCreated=true` ([BuildConfig.DEMO_UPCOMING_JAM_CREATED],
 *   `jam-status-created`) it is a CREADA jam (the draft flag wins if both are on).
 * - `bluesjam.demoUpcomingJamLive=true` ([BuildConfig.DEMO_UPCOMING_JAM_LIVE], `live-refresh-during-jam`)
 *   also rebinds [JamsRepository] to [DemoUpcomingJamRepository], in live mode: the demo replaces the
 *   upcoming jam and starts 10 minutes after the process started, and every refresh is logged with [log].
 *
 * Each real binding is resolved here once so the override can wrap it. The release variant returns
 * no module.
 */
internal fun Koin.debugOverrides(): List<Module> = debugOverrides(DebugFlags.fromBuildConfig)

internal fun Koin.debugOverrides(
    flags: DebugFlags,
    log: (String) -> Unit = {
        Log.i(LOG_TAG, it)
    },
): List<Module> = buildList {
    if (flags.demoUpcomingJam || flags.demoUpcomingJamLive) {
        val real = get<JamsRepository>()
        val calendar = get<JamCalendar>()
        add(
            module {
                single<JamsRepository> {
                    DemoUpcomingJamRepository(
                        real,
                        calendar,
                        status = flags.demoStatus(),
                        live = flags.demoUpcomingJamLive,
                        log = log,
                    )
                }
            },
        )
    }
}

/** The demo jam's status for these flags: the draft flag wins over the created one. */
internal fun DebugFlags.demoStatus(): JamStatus = when {
    demoUpcomingJamDraft -> JamStatus.DRAFT
    demoUpcomingJamCreated -> JamStatus.CREATED
    else -> JamStatus.PUBLISHED
}

/** True for the demo jam, so the startup log can mark it `(demo)`. Always false in release. */
internal fun Jam.isDemo(): Boolean = venue == DemoUpcomingJam.VENUE

/** The app's log tag (`BluesJamApp.LOG_TAG`, private there), so the demo lines sit with the startup lines. */
private const val LOG_TAG = "BluesJam"
