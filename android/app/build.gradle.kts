plugins {
    id("com.android.application")
    id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.example.domain_block"
    compileSdk = 35
    ndkVersion = "27.0.12077973"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    defaultConfig {
        applicationId = "com.example.domain_block"
        // Set the minSdk and targetSdk to values from the Flutter project (ensure they exist in `flutter.gradle`).
        minSdk =  21  // Default to 21 if not found
        targetSdk = 34
        versionCode = flutter.versionCode  // Default to 1 if not found
        versionName = flutter.versionName  // Default to "1.0.0" if not found
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

flutter {
    source = "../.."
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:1.2.2")

    implementation("androidx.activity:activity-ktx:1.5.0")
    implementation("androidx.core:core-ktx:1.7.0")
}
