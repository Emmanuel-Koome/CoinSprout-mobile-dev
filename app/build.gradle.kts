plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.coinsprout.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.coinsprout.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    // "demo" can read M-Pesa SMS (course demo / side-loaded APK).
    // "play" never requests SMS permissions: Google Play only allows them for default SMS handlers (and a few
    // narrow exceptions), so the Play build uses manual entry instead. See src/play/AndroidManifest.xml.
    flavorDimensions += "distribution"
    productFlavors {
        create("demo") {
            dimension = "distribution"
            buildConfigField("boolean", "SMS_AUTO_DETECT", "true")
        }
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "SMS_AUTO_DETECT", "false")
        }
    }

    buildTypes {
        debug {
            // 10.0.2.2 is the emulator's alias for the host machine. For a real phone use your PC's LAN IP
            // (and add it to src/debug/res/xml/network_security_config.xml).
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8000/\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"https://api.coinsprout.co.ke/\"")
            isMinifyEnabled = false // TODO before a public release: enable R8 and add keep rules for Gson DTOs
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.1")
    implementation("androidx.core:core-ktx:1.13.1")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Local persistence (offline-first)
    implementation("androidx.room:room-runtime:2.7.0-alpha13")
    implementation("androidx.room:room-ktx:2.7.0-alpha13")
    ksp("androidx.room:room-compiler:2.7.0-alpha13")

    // Background sync (replaces the always-on foreground service)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Coarse location, used only when the user taps "Find agents near me"
    implementation("com.google.android.gms:play-services-location:21.2.0")

    testImplementation("junit:junit:4.13.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
