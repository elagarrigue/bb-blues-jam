plugins {
    `kotlin-dsl`
}

// AGP and KGP are compileOnly: at runtime the conventions use the copies the root build loads
// through its `apply false` block, so the build has one AGP and one KGP.
dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("jvmLibrary") {
            id = "bluesjam.jvm.library"
            implementationClass = "com.bbbjam.buildlogic.JvmLibraryConventionPlugin"
        }
        register("androidLibrary") {
            id = "bluesjam.android.library"
            implementationClass = "com.bbbjam.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = "bluesjam.android.application"
            implementationClass = "com.bbbjam.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidCompose") {
            id = "bluesjam.android.compose"
            implementationClass = "com.bbbjam.buildlogic.AndroidComposeConventionPlugin"
        }
        register("androidPresenter") {
            id = "bluesjam.android.presenter"
            implementationClass = "com.bbbjam.buildlogic.AndroidPresenterConventionPlugin"
        }
        register("androidData") {
            id = "bluesjam.android.data"
            implementationClass = "com.bbbjam.buildlogic.AndroidDataConventionPlugin"
        }
        register("androidFeature") {
            id = "bluesjam.android.feature"
            implementationClass = "com.bbbjam.buildlogic.AndroidFeatureConventionPlugin"
        }
    }
}
