package com.bbbjam.feature.songdetail.di

import com.bbbjam.feature.songdetail.SongDetailPresenter
import org.koin.dsl.module

/** The song detail feature's Koin module. `JamsRepository` comes from `dataModule`. */
val songDetailModule = module {
    factory { SongDetailPresenter(get()) }
}
