plugins {
    id("bluesjam.android.feature")
}

android {
    namespace = "com.bbbjam.feature.info"
}

dependencies {
    // The only project dependency a feature may have is :core:* (D-03); :core:ui brings the
    // presenter contracts, the theme, the Compose runtime, ui and material3.
    implementation(project(":core:ui"))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
