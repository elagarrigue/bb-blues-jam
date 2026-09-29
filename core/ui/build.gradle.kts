plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.bbbjam.core.ui"
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt())
    }

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    // The Compose runtime calls android.os.Trace while composing; Molecule tests run on the JVM
    // against the stub android.jar, whose methods throw unless they return defaults.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
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
    testImplementation(libs.junit)
    testImplementation(libs.molecule.runtime)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}
