plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.voxora.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.voxora.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 27
        versionName = "0.6.7-dev"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        resourceConfigurations += listOf(
            "en", "fa", "ar", "es", "fr", "de", "tr"
        )
    }

    buildTypes {
        debug { isMinifyEnabled = false }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    testOptions {
        // The Android unit-test runtime substitutes a mockable `android.jar` whose methods throw
        // `RuntimeException("Stub!")`. `VoxoraLog` — which the Reader's error paths call — writes
        // through `android.util.Log`, so without this a test that exercises a handled failure dies
        // on the logging call rather than on the behaviour it is testing. Returning defaults makes
        // those calls no-ops. It does **not** replace the real `org.json` on the test classpath:
        // defaulted JSON accessors return null and would break the cache, not fix it.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    testImplementation("junit:junit:4.13.2")
    // The Android unit-test runtime ships a stubbed `org.json` whose methods throw, so any test
    // that reaches app code using `JSONObject`/`JSONArray` fails with `RuntimeException("Stub!")`.
    // The real implementation on the test classpath is what lets those tests exercise the code
    // they are testing; it is test-only and never shipped. Same pin as the `:core` module.
    testImplementation("org.json:json:20240303")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    // Google Identity Services authorization: the OAuth access token that Cloud reads need.
    // Credential Manager is deliberately not used — it can only return an ID token, which
    // identifies the account and authorises no Cloud API.
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
