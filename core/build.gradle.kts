plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.voxora.core"
    compileSdk = 35
    defaultConfig { minSdk = 29 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    // api: GeminiLiveSession exposes OkHttpClient in its public constructor default
    api("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    // Real JSON implementation for JVM contract tests: android.jar only ships throwing stubs.
    testImplementation("org.json:json:20240303")
    // Local HTTP server for the Google Cloud client contract tests: nothing reaches Google.
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
