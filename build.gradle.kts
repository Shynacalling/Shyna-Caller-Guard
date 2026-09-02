// Top-level build file
plugins {
    id("com.android.application") version "9.3.2" apply false
    id("org.jetbrains.kotlin.android") version "2.4.10" apply false
    id("com.google.devtools.ksp") version "2.3.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false

    // Add the dependency for the Google services Gradle plugin
    id("com.google.gms.google-services") version "4.5.0" apply false
}

