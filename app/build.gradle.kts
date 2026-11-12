android {
    compileSdk = 34 // Bắt buộc dùng SDK cao để biên dịch ổn định với Gradle/AGP mới

    defaultConfig {
        applicationId = "com.kinh.gmsprotect"
        minSdk = 22     // Cho phép thiết bị từ Android 5.1 (API 22) cài đặt và chạy ứng dụng
        targetSdk = 34
        
        // Nhận phiên bản động được truyền từ Gradle Project Properties (-PAPK_VERSION_CODE)
        val apkVersionCode = project.hasProperty("APK_VERSION_CODE") ?: 658
        val apkVersionName = project.hasProperty("APK_VERSION_NAME") ?: "2.0.658"
        
        versionCode = (project.findProperty("APK_VERSION_CODE") as? String)?.toInt() ?: 658
        versionName = (project.findProperty("APK_VERSION_NAME") as? String) ?: "2.0.658"
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
            minifyEnabled = false
        }
    }
}
