package com.bbbjam.feature.nextjam.di

import com.bbbjam.feature.nextjam.NextJamPresenter
import com.bbbjam.feature.nextjam.addsong.AddSongPresenter
import com.bbbjam.feature.nextjam.assign.AssignMusicianPresenter
import com.bbbjam.feature.nextjam.setkey.SetKeyPresenter
import com.bbbjam.feature.nextjam.songdetail.SongDetailPresenter
import org.koin.dsl.module

/**
 * The Próxima jam feature's Koin module. `JamsRepository`, `JamCalendar`, `AdminSession`,
 * `SetlistRepository` and `CatalogRepository` come from `dataModule`. The song detail lives here
 * since `song-detail-replaces-row-expansion` (it was `:feature:song-detail`).
 */
val nextJamModule = module {
    factory { NextJamPresenter(get(), get(), get(), get()) }
    factory { AddSongPresenter(get(), get(), get()) }
    factory { AssignMusicianPresenter(get(), get(), get()) }
    factory { SetKeyPresenter(get(), get(), get()) }
    factory { SongDetailPresenter(get(), get(), get()) }
}
