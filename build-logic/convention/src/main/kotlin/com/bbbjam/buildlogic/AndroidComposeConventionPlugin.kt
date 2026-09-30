package com.bbbjam.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Compose on an Android module: the Compose compiler plugin and `buildFeatures.compose`. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            check(pluginManager.hasPlugin("com.android.base")) {
                "bluesjam.android.compose needs an Android convention applied before it in $path"
            }
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            extensions.configure<CommonExtension> {
                buildFeatures.compose = true
            }
        }
    }
}
