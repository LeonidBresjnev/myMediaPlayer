plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.equalizer.suzuki"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.equalizer.suzuki"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(project(":common"))
    implementation(project(":carservice"))
    implementation(libs.androidx.car.app.automotive)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}