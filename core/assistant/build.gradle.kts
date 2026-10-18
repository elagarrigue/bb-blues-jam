plugins {
    id("bluesjam.android.data")
}

android {
    namespace = "com.bbbjam.core.assistant"
}

val koogWithoutJackson: ExternalModuleDependency.() -> Unit = {
    exclude(group = "com.fasterxml.jackson.core")
    exclude(group = "com.fasterxml.jackson.module")
    exclude(group = "com.fasterxml.jackson")
    exclude(group = "ai.koog", module = "serialization-jackson")
}

dependencies {
    api(project(":core:model"))
    // The one core-to-core edge: the agent reads the catalog and jams repositories (assistant-reads-only).
    implementation(project(":core:data"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    // Koog 1.3.0 pulls Jackson through serialization-jackson, and jackson-module-kotlin cannot be dexed at
    // minSdk 24 ("Increase the minSdkVersion to 26 or above", docs/evidence/assistant-agent-core/
    // assemble-failure-jackson.txt). The agent uses the kotlinx serializer, so Jackson is never loaded.
    implementation(libs.koog.agents.core, koogWithoutJackson)
    implementation(libs.koog.http.client.ktor, koogWithoutJackson)
    implementation(libs.ktor.client.okhttp)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(platform(libs.koin.bom))
    testImplementation(libs.koin.test)
}

tasks.withType<Test>().configureEach {
    systemProperty("bbbjam.rootDir", rootDir.absolutePath)
    // Opt-in live call to the real gateway (assistant-agent-core). Off unless -Pbbbjam.assistantLive=true;
    // the test reads the key itself from local.properties, never through Gradle.
    systemProperty("bbbjam.assistantLive", providers.gradleProperty("bbbjam.assistantLive").getOrElse("false"))
}
