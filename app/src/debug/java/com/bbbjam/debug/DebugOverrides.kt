package com.bbbjam.debug

import com.bbbjam.BuildConfig
import com.bbbjam.core.data.admin.AdminSession
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.model.Jam
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
    val debugAdmin: Boolean = false,
) {
    companion object {
        /** The flags of this debug build. */
        val fromBuildConfig = DebugFlags(
            demoUpcomingJam = BuildConfig.DEMO_UPCOMING_JAM,
            demoUpcomingJamDraft = BuildConfig.DEMO_UPCOMING_JAM_DRAFT,
            debugAdmin = BuildConfig.DEBUG_ADMIN,
        )
    }
}

/**
 * Debug variant of the overrides `BluesJamApp` loads after every other module, one per flag on, each
 * independent of the others:
 * - `bluesjam.demoUpcomingJam=true` ([BuildConfig.DEMO_UPCOMING_JAM]) rebinds [JamsRepository] to
 *   [DemoUpcomingJamRepository] around the real one; with `bluesjam.demoUpcomingJamDraft=true` as
 *   well ([BuildConfig.DEMO_UPCOMING_JAM_DRAFT], `unpublished-setlist-state`) the demo jam is a draft.
 * - `bluesjam.debugAdmin=true` ([BuildConfig.DEBUG_ADMIN], `debug-admin-session`) rebinds
 *   [AdminSession] to [DebugAdminSession] around the real one.
 *
 * Each real binding is resolved here once so the override can wrap it. The release variant returns
 * no module.
 */
internal fun Koin.debugOverrides(): List<Module> = debugOverrides(DebugFlags.fromBuildConfig)

internal fun Koin.debugOverrides(flags: DebugFlags): List<Module> = buildList {
    if (flags.demoUpcomingJam) {
        val real = get<JamsRepository>()
        val calendar = get<JamCalendar>()
        add(
            module {
                single<JamsRepository> {
                    DemoUpcomingJamRepository(real, calendar, draft = flags.demoUpcomingJamDraft)
                }
            },
        )
    }
    if (flags.debugAdmin) {
        val real = get<AdminSession>()
        add(module { single<AdminSession> { DebugAdminSession(real) } })
    }
}

/** True for the demo jam, so the startup log can mark it `(demo)`. Always false in release. */
internal fun Jam.isDemo(): Boolean = venue == DemoUpcomingJam.VENUE

/**
 * ` admin (debug)` when this build starts in forced admin mode (`debug-admin-session`), so the
 * startup log never passes for a real login. Always empty in release.
 */
internal fun debugAdminLogSuffix(): String = if (BuildConfig.DEBUG_ADMIN) " admin (debug)" else ""
