plugins {
    id("bluesjam.android.library")
}

android {
    namespace = "com.bbbjam.core.data"
}

dependencies {
    api(project(":core:model"))
}
