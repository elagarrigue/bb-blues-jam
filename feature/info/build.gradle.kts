plugins {
    id("bluesjam.android.feature")
}

android {
    namespace = "com.bbbjam.feature.info"
}

dependencies {
    // The only project dependencies a feature may have are :core:* (D-03); :core:ui brings the
    // presenter contracts, the theme, the Compose runtime, ui and material3.
    implementation(project(":core:ui"))
    // admin-passphrase-login: AdminSession, the admin mode the login sets and Info shows (D-15).
    implementation(project(":core:data"))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
