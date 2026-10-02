import java.util.Properties

plugins {
    id("bluesjam.android.application")
    id("bluesjam.android.compose")
}

// The Apps Script /exec URL lives only in the git-ignored local.properties
// (bluesjam.appsScriptUrl), never in a tracked file. Without it the app builds with "" and every
// read fails as NotConfigured (docs/apps-script-api.md, Transport).
val appsScriptUrl: String = rootProject.file("local.properties").let { file ->
    if (!file.isFile) return@let ""
    val properties = Properties()
    file.inputStream().use { properties.load(it) }
    properties.getProperty("bluesjam.appsScriptUrl", "").trim()
}

android {
    namespace = "com.bbbjam"

    defaultConfig {
        applicationId = "com.bbbjam"
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val escapedUrl = appsScriptUrl.replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "APPS_SCRIPT_URL", "\"$escapedUrl\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(project(":feature:info"))
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
