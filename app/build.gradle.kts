plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.clipforge.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.clipforge.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "0.2"

        ndk { abiFilters += "arm64-v8a" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")

    // FFmpeg (fork komunitas, LGPL). Versi di-pin persis, jangan pakai + atau latest
    implementation("dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.7")
}