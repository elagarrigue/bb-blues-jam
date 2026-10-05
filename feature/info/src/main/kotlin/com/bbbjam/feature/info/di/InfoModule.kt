package com.bbbjam.feature.info.di

import com.bbbjam.feature.info.AdminLoginPresenter
import com.bbbjam.feature.info.InfoPresenter
import org.koin.dsl.module

/**
 * The Info feature's Koin module. `ExternalLinkOpener` is bound by `:app`; `AdminSession` by
 * `:core:data`'s `dataModule`.
 */
val infoModule = module {
    factory { InfoPresenter(get(), get()) }
    factory { AdminLoginPresenter(get()) }
}
