plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.truckrouter"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.truckrouter"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Lifecycle / coroutine scope
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // HTTP client for the OpenRouteService API call
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // OpenStreetMap map view: online raster tiles (no API key) + offline tile archives
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // On-device OFFLINE truck routing: Valhalla engine (prebuilt native libs) + generated models
    implementation("io.github.rallista:valhalla-mobile:0.1.0")
    implementation("io.github.rallista:valhalla-models:0.2.0")
    implementation("io.github.rallista:valhalla-models-config:0.2.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
}
