// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}

// detekt and ktlint run in every module that compiles Kotlin, including future :feature:*
// modules, without per-module build code (see the architecture skill, Build Conventions).
val ktlintVersion = libs.versions.ktlint.get()
val detektConfig = file("config/detekt/detekt.yml")

subprojects {
    val applyQualityTools = {
        pluginManager.apply("dev.detekt")
        pluginManager.apply("org.jlleitschuh.gradle.ktlint")
        extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
            buildUponDefaultConfig.set(true)
            config.setFrom(detektConfig)
        }
        extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
            version.set(ktlintVersion)
        }
    }
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") { applyQualityTools() }
    pluginManager.withPlugin("com.android.base") { applyQualityTools() }
}
