import java.util.Properties

plugins {
    id("bluesjam.android.application")
    id("bluesjam.android.compose")
}

// The Apps Script /exec URL lives only in the git-ignored local.properties
// (bluesjam.appsScriptUrl), never in a tracked file. Without it the app builds with "" and every
// read fails as NotConfigured (docs/apps-script-api.md, Transport).
val localProperties: Properties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}
val appsScriptUrl: String = localProperties.getProperty("bluesjam.appsScriptUrl", "").trim()

// Debug-only demo upcoming jam (debug-demo-upcoming-jam): bluesjam.demoUpcomingJam=true in
// local.properties shows a fixed demo jam built in app/src/debug when the Sheet has no upcoming jam,
// so device checks need no temporary Sheet data. Absent means false; release is always false.
val demoUpcomingJam: Boolean = localProperties.getProperty("bluesjam.demoUpcomingJam", "").trim() == "true"

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
        debug {
            buildConfigField("boolean", "DEMO_UPCOMING_JAM", demoUpcomingJam.toString())
        }
        release {
            buildConfigField("boolean", "DEMO_UPCOMING_JAM", "false")
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
    implementation(project(":feature:next-jam"))
    implementation(project(":feature:song-detail"))
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    // Navigation Compose lives only in :app (D-03, Konsist navigation-only-in-app): features take
    // callbacks and plain values, never a NavController.
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
