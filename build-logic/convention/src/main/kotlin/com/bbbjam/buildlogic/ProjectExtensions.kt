package com.bbbjam.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/** The root version catalog, `gradle/libs.versions.toml`. */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/** A `[versions]` entry read as an integer, such as `compileSdk`. */
internal fun VersionCatalog.intVersion(alias: String): Int = findVersion(alias).get().requiredVersion.toInt()

/** A `[libraries]` entry, such as `koin-core`. */
internal fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> = findLibrary(alias).get()

/**
 * SDK levels from the catalog, Java 11 and core library desugaring, shared by every Android module.
 *
 * Desugaring is on everywhere because `minSdk` is below 26: lint `NewApi` fails a library that uses
 * java.time without it even when `:app` has it, and a library that enables it without `:app` fails
 * `:app:checkDebugAarMetadata`.
 */
internal fun Project.configureAndroidCommon(android: CommonExtension) {
    android.apply {
        compileSdk { version = release(libs.intVersion("compileSdk")) }
        defaultConfig.minSdk = libs.intVersion("minSdk")
        compileOptions.sourceCompatibility = JavaVersion.VERSION_11
        compileOptions.targetCompatibility = JavaVersion.VERSION_11
        compileOptions.isCoreLibraryDesugaringEnabled = true
    }
    dependencies {
        add("coreLibraryDesugaring", libs.library("desugar-jdk-libs"))
    }
}
