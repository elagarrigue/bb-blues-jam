package com.bbbjam.feature.nextjam.di

import com.bbbjam.feature.nextjam.NextJamPresenter
import org.koin.dsl.module

/** The Próxima jam feature's Koin module. `JamsRepository` and `JamCalendar` come from `dataModule`. */
val nextJamModule = module {
    factory { NextJamPresenter(get(), get()) }
}
