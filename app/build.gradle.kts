plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kinh.gmsprotect"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kinh.gmsprotect"
        minSdk = 22
        targetSdk = 22
        
        versionCode = if (project.hasProperty("APK_VERSION_CODE")) {
            project.property("APK_VERSION_CODE").toString().toInt()
        } else {
            1
        }
        
        versionName = if (project.hasProperty("APK_VERSION_NAME")) {
            project.property("APK_VERSION_NAME").toString()
        } else {
            "1.0"
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
        disable.add("ExpiredTargetSdkVersion")
    }

    signingConfigs {
        create("release") {
            storeFile = file("keystore.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: ""
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }

    // ĐÃ SỬA: Đồng bộ hóa phiên bản Java và Kotlin target lên 17 cho khớp với môi trường GitHub Actions
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
}
