package com.bbbjam.feature.pastjams.di

import com.bbbjam.feature.pastjams.PastJamDetailPresenter
import com.bbbjam.feature.pastjams.PastJamsPresenter
import org.koin.dsl.module

/**
 * The Anteriores feature's Koin module: the list and a past jam's detail. `JamsRepository` and
 * `JamCalendar` come from `dataModule`.
 */
val pastJamsModule = module {
    factory { PastJamsPresenter(get(), get()) }
    factory { PastJamDetailPresenter(get()) }
}
