plugins {
    id("bluesjam.android.feature")
}

android {
    namespace = "com.bbbjam.feature.assistant"
}

dependencies {
    // The only project dependencies a feature may have are :core:* (D-03). :core:ui brings the
    // presenter contracts, the theme and the state components; :core:data
    // brings the repositories and the narrow Setlist* contracts and, through its api, :core:model;
    // :core:assistant brings SetlistAssistant.
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(project(":core:assistant"))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
