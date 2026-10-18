plugins {
    id("bluesjam.android.data")
}

android {
    namespace = "com.bbbjam.core.data"
    // Fakes of the contracts in this module that several modules' tests share (architecture skill, Shared test fixtures).
    testFixtures {
        enable = true
    }
}

val koogWithoutJackson: ExternalModuleDependency.() -> Unit = {
    exclude(group = "com.fasterxml.jackson.core")
    exclude(group = "com.fasterxml.jackson.module")
    exclude(group = "com.fasterxml.jackson")
    exclude(group = "ai.koog", module = "serialization-jackson")
}

dependencies {
    api(project(":core:model"))
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    // Koog 1.3.0 pulls Jackson through serialization-jackson, and jackson-module-kotlin cannot be dexed at
    // minSdk 24 ("Increase the minSdkVersion to 26 or above", docs/evidence/assistant-agent-core/
    // assemble-failure-jackson.txt). The agent uses the kotlinx serializer, so Jackson is never loaded.
    implementation(libs.koog.agents.core, koogWithoutJackson)
    implementation(libs.koog.http.client.ktor, koogWithoutJackson)
    implementation(libs.ktor.client.okhttp)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    testFixturesApi(libs.kotlinx.coroutines.core)
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
    // Opt-in live call to the real gateway (assistant-agent-core). Off unless -Pbbbjam.assistantLive=true;
    // the test reads the key itself from local.properties, never through Gradle.
    systemProperty("bbbjam.assistantLive", providers.gradleProperty("bbbjam.assistantLive").getOrElse("false"))
}
