import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Secrets (BASE_URL, API_KEY) live in local.properties, which is
// gitignored and never committed. Each developer sets their own copy.
// Falls back to obviously-fake placeholders so the project still
// compiles for someone who hasn't set these up yet.
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        load(localPropertiesFile.inputStream())
    }
}

android {
    namespace = "com.grainmvp.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.grainmvp.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField(
            "String",
            "BACKEND_BASE_URL",
            "\"${localProperties.getProperty("backend.baseUrl", "http://10.0.2.2:8000")}\""
        )
        buildConfigField(
            "String",
            "BACKEND_API_KEY",
            "\"${localProperties.getProperty("backend.apiKey", "REPLACE_ME_IN_LOCAL_PROPERTIES")}\""
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        // CameraX 1.3.4 ships libimage_processing_util_jni.so with non-16KB alignment.
        // This is a known issue with older CameraX; the warning is suppressed here.
        // The app will run in page-size compatible mode on 16KB page-size devices,
        // which has minimal performance impact.
        resources {
            pickFirsts += listOf(
                "lib/x86_64/libimage_processing_util_jni.so",
                "lib/x86/libimage_processing_util_jni.so",
                "lib/arm64-v8a/libimage_processing_util_jni.so",
                "lib/armeabi-v7a/libimage_processing_util_jni.so"
            )
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // CameraX — Phase 1 (camera, crop, downscale). Version 1.3.x is stable
    // and does not force compileSdk 34+ the way 1.4/1.5 betas do.
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Networking — Phase 2. kotlinx.serialization matches the JSON
    // shapes exactly, no reflection like Gson/Moshi.
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Unit testing — used first for CropMath, the trickiest pure logic
    // in Phase 1. Runs on the JVM, no emulator/device needed.
    testImplementation("junit:junit:4.13.2")
}