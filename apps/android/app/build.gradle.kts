plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.homedashboard.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.homedashboard.app"
        // API 33 (Android 13) - Supports Boox Air4, Air5, Max
        // Enables native stylus handwriting APIs
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/groovy/**"
        }
        // Resolve duplicate native lib from Boox SDK and mmkv
        jniLibs {
            pickFirsts += "**/libc++_shared.so"
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Jetpack Compose (2024.12.01 is latest stable BOM)
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Room Database (for local calendar storage)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore (for preferences/settings)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // WorkManager (for background sync)
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Google ML Kit - Digital Ink Recognition (handwriting)
    // Note: Can also use native InputMethodManager.startStylusHandwriting() on API 33+
    implementation("com.google.mlkit:digital-ink-recognition:18.1.0")

    // Google ML Kit - Entity Extraction (time, address, etc. from text)
    implementation("com.google.mlkit:entity-extraction:16.0.0-beta5")

    // Motion prediction for stylus latency reduction
    implementation("androidx.input:input-motionprediction:1.0.0-beta01")

    // androidx.ink — Low-latency stylus rendering (front-buffer OpenGL)
    val inkVersion = "1.0.0"
    implementation("androidx.ink:ink-nativeloader:$inkVersion")
    implementation("androidx.ink:ink-strokes:$inkVersion")
    implementation("androidx.ink:ink-brush:$inkVersion")
    implementation("androidx.ink:ink-authoring:$inkVersion")
    implementation("androidx.ink:ink-rendering:$inkVersion")

    // Boox Onyx Pen SDK - low-latency stylus input on e-ink devices
    implementation("com.onyx.android.sdk:onyxsdk-pen:1.4.11") {
        exclude(group = "com.android.support")
    }
    implementation("com.onyx.android.sdk:onyxsdk-device:1.2.30") {
        exclude(group = "com.android.support")
    }

    // Networking (for CalDAV and future API integrations)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // =======================================================================
    // CALENDAR SYNC DEPENDENCIES
    // =======================================================================

    // Google Sign-In and Calendar API
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // Encrypted storage for OAuth tokens
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // JSON parsing for REST API responses
    implementation("com.google.code.gson:gson:2.11.0")

    // Phase 5: Microsoft Calendar Integration (future)
    // implementation("com.microsoft.identity.client:msal:5.4.0")

    // Phase 5: iCloud/CalDAV Integration — ical4j 3.x (Android-compatible, used by DAVx5)
    implementation("org.mnode.ical4j:ical4j:3.0.29") {
        exclude(group = "org.codehaus.groovy")
        exclude(group = "org.apache.commons")
        exclude(group = "org.slf4j")
    }

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
