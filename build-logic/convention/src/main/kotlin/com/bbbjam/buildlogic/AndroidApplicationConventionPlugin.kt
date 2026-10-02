package com.bbbjam.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * The `:app` module: the common Android setup, `targetSdk` from the catalog and BuildConfig, which
 * carries the Apps Script URL read from the git-ignored `local.properties`.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            extensions.configure<ApplicationExtension> {
                configureAndroidCommon(this)
                defaultConfig.targetSdk = libs.intVersion("targetSdk")
                buildFeatures.buildConfig = true
            }
        }
    }
}
