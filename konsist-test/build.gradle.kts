// Test-only module: the Konsist architecture suite. It reads every module's sources, so it
// needs no project dependency (Konsist parses source files, not classes).
plugins {
    id("bluesjam.jvm.library")
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.konsist)
}

// Konsist reads every module's sources, which are not inputs of this task by default.
// Without this, a new violation elsewhere leaves the test UP-TO-DATE and the gate stays green.
tasks.test {
    inputs.files(
        fileTree(rootDir) {
            include("**/*.kt", "**/*.kts")
            exclude("**/build/**", "**/.gradle/**", "**/.kotlin/**")
        },
    ).withPropertyName("projectKotlinSources").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("bbbjam.rootDir", rootDir.absolutePath)
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStackTraces = false
    }
}
