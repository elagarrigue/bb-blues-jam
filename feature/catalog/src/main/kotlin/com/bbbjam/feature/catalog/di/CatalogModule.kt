package com.bbbjam.feature.catalog.di

import com.bbbjam.feature.catalog.CatalogPresenter
import org.koin.dsl.module

/**
 * The Temas tab's Koin module (`admin-catalog-tab`). `CatalogRepository`, `CatalogEditRepository`,
 * `JamsRepository`, `JamCalendar` and `AdminSession` all come from `dataModule`; nothing is bound in `:app`.
 */
val catalogModule = module {
    factory { CatalogPresenter(get(), get(), get(), get(), get()) }
}
