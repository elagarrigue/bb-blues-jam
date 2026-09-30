package com.bbbjam

import android.app.Application
import com.bbbjam.di.appModule
import com.bbbjam.feature.info.di.infoModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/** Starts Koin with every module; only `:app` calls `startKoin` (D-16). */
class BluesJamApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@BluesJamApp)
            modules(appModule, infoModule)
        }
    }
}
