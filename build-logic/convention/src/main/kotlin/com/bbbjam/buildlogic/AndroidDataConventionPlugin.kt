package com.bbbjam.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * The data module (`:core:data`): an Android library with kotlinx.serialization and KSP (for the
 * Room compiler), and the JVM test stack for repositories and Room on the bundled SQLite driver.
 */
class AndroidDataConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("bluesjam.android.library")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
            pluginManager.apply("com.google.devtools.ksp")
            extensions.configure<LibraryExtension> {
                // Room tests build the database on the JVM with ContextWrapper(null); the stub
                // android.jar throws on every call unless its methods return defaults.
                testOptions.unitTests.isReturnDefaultValues = true
            }
            dependencies {
                add("testImplementation", libs.library("junit"))
                add("testImplementation", libs.library("kotlinx-coroutines-test"))
                add("testImplementation", libs.library("turbine"))
            }
        }
    }
}
