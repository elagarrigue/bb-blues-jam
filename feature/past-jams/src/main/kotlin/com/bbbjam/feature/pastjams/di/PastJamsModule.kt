package com.bbbjam.feature.pastjams.di

import com.bbbjam.feature.pastjams.PastJamsPresenter
import org.koin.dsl.module

/** The Anteriores feature's Koin module. `JamsRepository` and `JamCalendar` come from `dataModule`. */
val pastJamsModule = module {
    factory { PastJamsPresenter(get(), get()) }
}
