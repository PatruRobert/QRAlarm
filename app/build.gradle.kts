import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val secrets = Properties().also { props ->
    val file = rootProject.file("secrets.properties")
    if (file.exists()) props.load(file.inputStream())
}

android {
    namespace = "com.robert.qalarm"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.robert.qalarm"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val appKey = secrets.getProperty("DROPBOX_APP_KEY", "placeholder")
        buildConfigField("String", "DROPBOX_APP_KEY", "\"$appKey\"")
        manifestPlaceholders["dropboxAppKey"] = appKey
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.dropbox.core.sdk)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}