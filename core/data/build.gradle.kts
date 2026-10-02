plugins {
    id("bluesjam.android.data")
}

android {
    namespace = "com.bbbjam.core.data"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.sqlite.bundled.jvm)
    testImplementation(platform(libs.koin.bom))
    testImplementation(libs.koin.test)
}

// The mapper tests read docs/api-samples and docs/sheet-seed, which are not inputs of the test task
// by default. Without this, editing a sample leaves the tests UP-TO-DATE and the gate stays green.
tasks.withType<Test>().configureEach {
    inputs.dir(rootProject.file("docs/api-samples"))
        .withPropertyName("apiSamples").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.file("docs/sheet-seed"))
        .withPropertyName("sheetSeed").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("bbbjam.rootDir", rootDir.absolutePath)
}
