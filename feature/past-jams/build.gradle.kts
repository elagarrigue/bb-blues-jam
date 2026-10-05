plugins {
    id("bluesjam.android.feature")
}

android {
    namespace = "com.bbbjam.feature.pastjams"
}

dependencies {
    // The only project dependencies a feature may have are :core:* (D-03). :core:ui brings the
    // presenter contracts, the theme, the state components and the shared date names; :core:data
    // brings JamsRepository, JamCalendar and, through its api, :core:model.
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
