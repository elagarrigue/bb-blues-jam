package com.bbbjam.feature.pastjams.di

import com.bbbjam.feature.pastjams.PastJamDetailPresenter
import com.bbbjam.feature.pastjams.PastJamsPresenter
import org.koin.dsl.module

/**
 * The Jams tab's Koin module: the list and a past jam's detail. `JamsRepository`, `JamCalendar` and
 * `AdminSession` come from `dataModule`; the narrow `SetlistJamStatusRepository` and
 * `SetlistPublishRepository` are bound in `:app` to the one `SetlistRepository` instance (J1), as is
 * `JamCreator` (to the action registry).
 */
val pastJamsModule = module {
    factory { PastJamsPresenter(get(), get(), get(), get(), get(), get()) }
    factory { PastJamDetailPresenter(get()) }
}
