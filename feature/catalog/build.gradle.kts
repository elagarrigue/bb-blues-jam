plugins {
    id("bluesjam.android.feature")
}

android {
    namespace = "com.bbbjam.feature.catalog"
}

dependencies {
    // The only project dependencies a feature may have are :core:* (D-03); :core:ui brings the
    // presenter contracts, the theme, the Compose runtime, ui and material3.
    implementation(project(":core:ui"))
    // admin-catalog-tab: the catalog and jams repositories, the catalog edit contract and AdminSession.
    implementation(project(":core:data"))
    testImplementation(testFixtures(project(":core:data")))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
