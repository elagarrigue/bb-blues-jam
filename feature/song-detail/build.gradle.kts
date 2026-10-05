plugins {
    id("bluesjam.android.feature")
}

android {
    namespace = "com.bbbjam.feature.songdetail"
}

dependencies {
    // The only project dependencies a feature may have are :core:* (D-03). :core:ui brings the
    // presenter contracts, the theme, the shared lineup and back components; :core:data brings
    // JamsRepository and, through its api, :core:model. No navigation library: :app owns it.
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
