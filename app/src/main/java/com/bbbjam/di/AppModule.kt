package com.bbbjam.di

import com.bbbjam.core.ui.link.ExternalLinkOpener
import com.bbbjam.link.IntentLinkOpener
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Android implementations of `:core:*` contracts that need a Context. */
val appModule = module {
    single<ExternalLinkOpener> { IntentLinkOpener(androidContext()) }
}
