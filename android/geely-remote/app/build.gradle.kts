plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.pashakagan.geelyremote"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pashakagan.geelyremote"
        minSdk = 26
        targetSdk = 34
        versionCode = 20
        versionName = "0.20"
    }

    // Stable signing key (committed) so every CI build is signed identically ->
    // the app updates IN PLACE instead of forcing an uninstall/reinstall.
    signingConfigs {
        create("stable") {
            storeFile = file("geely.keystore")
            storePassword = "d3fec00b2d18568dd200b52419b6b11faa2d"
            keyAlias = "geely"
            keyPassword = "d3fec00b2d18568dd200b52419b6b11faa2d"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("stable")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("stable")
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
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("com.squareup.okio:okio:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // dadb is vendored under app/src/main/java/dadb (patched for Android 5.x adbd)
}
