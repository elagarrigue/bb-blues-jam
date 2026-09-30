package com.bbbjam.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** An Android library with presenters: Compose plus the Molecule test stack on the JVM. */
class AndroidPresenterConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("bluesjam.android.library")
            pluginManager.apply("bluesjam.android.compose")
            extensions.configure<LibraryExtension> {
                // The Compose runtime calls android.os.Trace while composing; Molecule tests run on
                // the JVM against the stub android.jar, whose methods throw unless they return defaults.
                testOptions.unitTests.isReturnDefaultValues = true
            }
            dependencies {
                add("testImplementation", libs.library("junit"))
                add("testImplementation", libs.library("molecule-runtime"))
                add("testImplementation", libs.library("turbine"))
                add("testImplementation", libs.library("kotlinx-coroutines-test"))
            }
        }
    }
}
