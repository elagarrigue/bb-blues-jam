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

// The DevExpert Inference key (D-22, assistant-agent-core) also lives only in local.properties
// (devexpert.key). It is built into every build type, release included: the risk of a key in the APK
// is accepted. Without it the app builds with "" and the assistant answers NotConfigured. Never log it.
val devexpertKey: String = localProperties.getProperty("devexpert.key", "").trim()

// Debug-only demo upcoming jam (debug-demo-upcoming-jam): bluesjam.demoUpcomingJam=true in
// local.properties shows a fixed demo jam built in app/src/debug when the Sheet has no upcoming jam,
// so device checks need no temporary Sheet data. Absent means false; release is always false.
val demoUpcomingJam: Boolean = localProperties.getProperty("bluesjam.demoUpcomingJam", "").trim() == "true"

// Draft variant (unpublished-setlist-state): bluesjam.demoUpcomingJamDraft=true makes the demo jam a
// DRAFT with the same songs, so a device check can see the draft card. Effective only with the demo
// flag on. Absent means false; release is always false.
val demoUpcomingJamDraft: Boolean =
    localProperties.getProperty("bluesjam.demoUpcomingJamDraft", "").trim() == "true"

// Created variant (jam-status-created, D-26): bluesjam.demoUpcomingJamCreated=true makes the demo jam a
// CREADA with the same songs, so a device check can see the date-only musician view. Effective only
// with the demo flag (or the live flag) on; the draft flag wins if both are on. Absent means false;
// release is always false.
val demoUpcomingJamCreated: Boolean =
    localProperties.getProperty("bluesjam.demoUpcomingJamCreated", "").trim() == "true"

// Debug-only admin session (debug-admin-session): bluesjam.debugAdmin=true starts a debug build in
// admin mode without a login, so device checks of admin controls need no passphrase typed. It draws
// controls only and authorizes nothing (Apps Script checks every write). Absent means false; release
// is always false.
val debugAdmin: Boolean = localProperties.getProperty("bluesjam.debugAdmin", "").trim() == "true"

// Debug-only live demo jam (live-refresh-during-jam): bluesjam.demoUpcomingJamLive=true replaces the
// upcoming jam, whatever the cache holds, with the demo jam dated today and starting 10 minutes after
// the process started (Buenos Aires), so its live window is open and the 30 s refresh can be watched
// in logcat ("demo jams refresh: …"). Works on its own. Absent means false; release is always false.
val demoUpcomingJamLive: Boolean =
    localProperties.getProperty("bluesjam.demoUpcomingJamLive", "").trim() == "true"

android {
    namespace = "com.bbbjam"

    defaultConfig {
        applicationId = "com.bbbjam"
        versionCode = 1
        versionName = "1.0"

        val escapedUrl = appsScriptUrl.replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "APPS_SCRIPT_URL", "\"$escapedUrl\"")

        val escapedKey = devexpertKey.replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "DEVEXPERT_KEY", "\"$escapedKey\"")
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "DEMO_UPCOMING_JAM", demoUpcomingJam.toString())
            buildConfigField("boolean", "DEMO_UPCOMING_JAM_DRAFT", demoUpcomingJamDraft.toString())
            buildConfigField("boolean", "DEMO_UPCOMING_JAM_CREATED", demoUpcomingJamCreated.toString())
            buildConfigField("boolean", "DEBUG_ADMIN", debugAdmin.toString())
            buildConfigField("boolean", "DEMO_UPCOMING_JAM_LIVE", demoUpcomingJamLive.toString())
        }
        release {
            buildConfigField("boolean", "DEMO_UPCOMING_JAM", "false")
            buildConfigField("boolean", "DEMO_UPCOMING_JAM_DRAFT", "false")
            buildConfigField("boolean", "DEMO_UPCOMING_JAM_CREATED", "false")
            buildConfigField("boolean", "DEBUG_ADMIN", "false")
            buildConfigField("boolean", "DEMO_UPCOMING_JAM_LIVE", "false")
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
    implementation(project(":core:assistant"))
    implementation(project(":feature:assistant"))
    implementation(project(":feature:info"))
    implementation(project(":feature:next-jam"))
    implementation(project(":feature:past-jams"))
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    // Reads the admin flag once at the composition root (admin-mode-highlight), the same
    // koinInject() convention every feature presenter already uses (D-16).
    implementation(libs.koin.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    // Backports the Android 12 splash screen (band logo on the window background) down to minSdk.
    implementation(libs.androidx.core.splashscreen)
    // Navigation Compose lives only in :app (D-03, Konsist navigation-only-in-app): features take
    // callbacks and plain values, never a NavController.
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    testImplementation(libs.junit)
    testImplementation(testFixtures(project(":core:data")))
}
