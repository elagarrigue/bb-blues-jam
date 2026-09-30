plugins {
    id("bluesjam.android.presenter")
}

android {
    namespace = "com.bbbjam.core.ui"
}

dependencies {
    api(project(":core:model"))
    // Presenter contracts expose @Composable and @Immutable, so consumers need the runtime too.
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.runtime)
    // Tokens expose Color, TextStyle, Dp and Shape; screens draw with the themed Material 3 components.
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
