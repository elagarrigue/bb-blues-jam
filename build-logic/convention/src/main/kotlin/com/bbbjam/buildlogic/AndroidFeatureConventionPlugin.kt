package com.bbbjam.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** A `:feature:*` module: a presenter module with its one Koin module (`koin-core`, `koin-compose`). */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("bluesjam.android.presenter")
            dependencies {
                add("implementation", platform(libs.library("koin-bom")))
                add("implementation", libs.library("koin-core"))
                add("implementation", libs.library("koin-compose"))
            }
        }
    }
}
