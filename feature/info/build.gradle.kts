plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.bbbjam.feature.info"
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
    // The only project dependency a feature may have is :core:* (D-03); :core:ui brings the
    // presenter contracts, the theme, the Compose runtime, ui and material3.
    implementation(project(":core:ui"))
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.molecule.runtime)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}
