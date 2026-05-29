plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ─── Android Signing Configuration (Codemagic + Local) ────────────────────
// Codemagic injects these env vars automatically when you add a keystore
// in the Codemagic dashboard → Distribution → Android code signing.
// For local builds, create a ~/.gradle/gradle.properties entry or pass
//   -PKEYSTORE_FILE=... -PKEYSTORE_PASSWORD=... etc.
val KEYSTORE_FILE: String? by project
val KEYSTORE_PASSWORD: String? by project
val KEY_ALIAS: String? by project
val KEY_PASSWORD: String? by project

// Codemagic also exposes these standard variables:
val CM_KEYSTORE_PATH: String? = System.getenv("CM_KEYSTORE_PATH")
val CM_KEYSTORE_PASSWORD: String? = System.getenv("CM_KEYSTORE_PASSWORD")
val CM_KEY_ALIAS: String? = System.getenv("CM_KEY_ALIAS")
val CM_KEY_PASSWORD: String? = System.getenv("CM_KEY_PASSWORD")

val resolvedKeystoreFile = KEYSTORE_FILE
    ?: CM_KEYSTORE_PATH
    ?: System.getenv("KEYSTORE_FILE")
val resolvedKeystorePassword = KEYSTORE_PASSWORD
    ?: CM_KEYSTORE_PASSWORD
    ?: System.getenv("KEYSTORE_PASSWORD")
val resolvedKeyAlias = KEY_ALIAS
    ?: CM_KEY_ALIAS
    ?: System.getenv("KEY_ALIAS")
val resolvedKeyPassword = KEY_PASSWORD
    ?: CM_KEY_PASSWORD
    ?: System.getenv("KEY_PASSWORD")

android {
    namespace = "com.keyboardmasterpiece"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.keyboardmasterpiece"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("CM_BUILD_NUMBER")?.toIntOrNull() ?: 2)
        versionName = "1.1.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64") }
        externalNativeBuild { cmake { cppFlags += listOf("-std=c++17", "-O3", "-fvisibility=hidden") } }
    }

    signingConfigs {
        create("release") {
            // Only populate if all four variables are present → avoids build failure on debug
            if (resolvedKeystoreFile != null && resolvedKeystorePassword != null &&
                resolvedKeyAlias != null && resolvedKeyPassword != null) {
                storeFile = file(resolvedKeystoreFile!!)
                storePassword = resolvedKeystorePassword!!
                keyAlias = resolvedKeyAlias!!
                keyPassword = resolvedKeyPassword!!
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk { debugSymbolLevel = "SYMBOL_TABLE" }
            // Use the release signing config only when keystore details are available
            if (resolvedKeystoreFile != null && resolvedKeystorePassword != null &&
                resolvedKeyAlias != null && resolvedKeyPassword != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // Build features
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    // EncryptedSharedPreferences for clipboard + personal words
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    // InputConnectionCompat.commitContent() for file sending from keyboard
    implementation("androidx.core:core:1.15.0")
}
