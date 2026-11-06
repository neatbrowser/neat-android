plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// GeckoView release channel from maven.mozilla.org. "+" takes the newest; pin it with
// -PgeckoviewVersion=<version> (or in gradle.properties) once you know a version that works.
val geckoviewVersion = providers.gradleProperty("geckoviewVersion").orElse("+").get()

android {
    namespace = "io.github.neatbrowser.search"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.neatbrowser.search"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // GeckoView ships its engine for every ABI; keep the phones. x86_64 (emulator) is added with the emulator tests.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // Shrinking is switched on once the blocker (step 4) has tests that exercise the release build.
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core"))

    implementation("org.mozilla.geckoview:geckoview:$geckoviewVersion")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
}
