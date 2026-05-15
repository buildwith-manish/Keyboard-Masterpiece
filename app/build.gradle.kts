plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.keyboardmasterpiece"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.keyboardmasterpiece"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64") }
        externalNativeBuild { cmake { cppFlags += listOf("-std=c++17", "-O3", "-fvisibility=hidden") } }
    }

    buildTypes {
        debug { isDebuggable = true }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            ndk { debugSymbolLevel = "SYMBOL_TABLE" }
        }
    }

    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
