package com.bbbjam.debug

import com.bbbjam.BuildConfig
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.model.Jam
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Debug variant of the overrides `BluesJamApp` loads after every other module. With
 * `bluesjam.demoUpcomingJam=true` in `local.properties` ([BuildConfig.DEMO_UPCOMING_JAM]) it rebinds
 * [JamsRepository] to [DemoUpcomingJamRepository] around the real one, resolved here once so the
 * override can wrap it. With `bluesjam.demoUpcomingJamDraft=true` as well
 * ([BuildConfig.DEMO_UPCOMING_JAM_DRAFT], `unpublished-setlist-state`) the demo jam is a draft.
 * The release variant returns no module.
 */
internal fun Koin.debugOverrides(): List<Module> {
    if (!BuildConfig.DEMO_UPCOMING_JAM) return emptyList()
    val real = get<JamsRepository>()
    val calendar = get<JamCalendar>()
    return listOf(
        module {
            single<JamsRepository> {
                DemoUpcomingJamRepository(real, calendar, draft = BuildConfig.DEMO_UPCOMING_JAM_DRAFT)
            }
        },
    )
}

/** True for the demo jam, so the startup log can mark it `(demo)`. Always false in release. */
internal fun Jam.isDemo(): Boolean = venue == DemoUpcomingJam.VENUE
