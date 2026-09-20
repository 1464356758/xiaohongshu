plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.photosimulator"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.photosimulator"
        minSdk = 29
        targetSdk = 35
        versionCode = 4
        versionName = "4.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
