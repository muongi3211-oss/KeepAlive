android {
    ...
    signingConfigs {
        create("release") {
            storeFile = file("ten_chung_chi_goc.jks") // Đường dẫn tới file keystore của bạn
            storePassword = "mat_khau_store"
            keyAlias = "alias_cua_ban"
            keyPassword = "mat_khau_key"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            minifyEnabled = false // hoặc giữ nguyên cấu hình cũ của bạn
            ...
        }
    }
}
