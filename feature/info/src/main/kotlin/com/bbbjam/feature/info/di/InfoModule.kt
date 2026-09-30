package com.bbbjam.feature.info.di

import com.bbbjam.feature.info.InfoPresenter
import org.koin.dsl.module

/** The Info feature's Koin module. `ExternalLinkOpener` is bound by `:app`. */
val infoModule = module {
    factory { InfoPresenter(get()) }
}
