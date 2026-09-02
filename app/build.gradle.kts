plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.callruleblocker"
    compileSdk = 37


    defaultConfig {
        applicationId = "com.example.callruleblocker"
        minSdk = 26          // Required for CallScreeningService + dual-SIM APIs
        targetSdk = 37
        versionCode = 80
        versionName = "4.14.7-FINAL"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
    buildToolsVersion = "36.0.0"
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")

    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.9.8")

    // Room (local database of rules)
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Security & Biometrics
    implementation("androidx.biometric:biometric:1.4.0-alpha02")
    implementation("androidx.fragment:fragment-ktx:1.8.6")
    implementation(platform("com.google.firebase:firebase-bom:34.17.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-messaging")
    // implementation("com.google.firebase:firebase-storage-ktx:21.0.2") // DISABLED - BILLING REQUIRED
    implementation("com.google.android.gms:play-services-auth:21.3.0")

    // Image Loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Google Maps & Places
    implementation("com.google.maps.android:maps-compose:8.4.0")
    implementation("com.google.android.gms:play-services-maps:20.0.0")
    implementation("com.google.android.gms:play-services-location:21.4.0")
    implementation("com.google.android.libraries.places:places:5.3.0")

    // LiveKit
    implementation("io.livekit:livekit-android:2.28.1")
    
    // WorkManager for Backend Tasks
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Cloudinary for Media Uploads
    implementation("com.cloudinary:cloudinary-android:3.1.2")

    // Media3 ExoPlayer
    val media3_version = "1.5.0"
    implementation("androidx.media3:media3-exoplayer:${media3_version}")
    implementation("androidx.media3:media3-ui:${media3_version}")
    implementation("androidx.media3:media3-common:${media3_version}")

    // CameraX
    val camerax_version = "1.4.1"
    implementation("androidx.camera:camera-core:${camerax_version}")
    implementation("androidx.camera:camera-camera2:${camerax_version}")
    implementation("androidx.camera:camera-lifecycle:${camerax_version}")
    implementation("androidx.camera:camera-video:${camerax_version}")
    implementation("androidx.camera:camera-view:${camerax_version}")
    implementation("androidx.camera:camera-extensions:${camerax_version}")

    // Gemini / Generative AI
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
}
