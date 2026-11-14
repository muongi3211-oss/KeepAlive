plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'com.kinh.gmsprotect'
    compileSdk 34

    defaultConfig {
        applicationId "com.kinh.gmsprotect"
        minSdk 24
        targetSdk 22
        
        // Tự động nhận phiên bản từ GitHub Actions hoặc dùng giá trị mặc định nếu build local
        versionCode project.hasProperty('APK_VERSION_CODE') ? project.property('APK_VERSION_CODE').toInteger() : 1
        versionName project.hasProperty('APK_VERSION_NAME') ? project.property('APK_VERSION_NAME') : "1.0"

        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }
    lint {
        checkReleaseBuilds false
        abortOnError false
        disable 'ExpiredTargetSdkVersion'
    }

    buildTypes {
        release {
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = '1.8'
    }
}

dependencies {
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
}
