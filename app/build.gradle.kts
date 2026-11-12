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
        targetSdk = 34
        
        versionCode = (project.findProperty("APK_VERSION_CODE") as? String)?.toInt() ?: 658
        versionName = (project.findProperty("APK_VERSION_NAME") as? String) ?: "2.0.658"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
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
}
