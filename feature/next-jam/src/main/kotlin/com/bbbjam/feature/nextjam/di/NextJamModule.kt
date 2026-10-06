package com.bbbjam.feature.nextjam.di

import com.bbbjam.feature.nextjam.AddSongPresenter
import com.bbbjam.feature.nextjam.NextJamPresenter
import org.koin.dsl.module

/**
 * The Próxima jam feature's Koin module. `JamsRepository`, `JamCalendar`, `AdminSession`,
 * `SetlistRepository` and `CatalogRepository` come from `dataModule`.
 */
val nextJamModule = module {
    factory { NextJamPresenter(get(), get(), get(), get()) }
    factory { AddSongPresenter(get(), get(), get()) }
}
