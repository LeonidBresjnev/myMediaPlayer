

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.equalizer.mymediaplayer"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.equalizer.mymediaplayer"
        minSdk = 33
        targetSdk = 37
        versionCode = 12
        versionName = "4.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true

            // Enables resource shrinking.
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }/**
        profilable {

        }*/
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "SoundsGood")
        }

    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }/*
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }*/
    buildFeatures {
        compose = true
        resValues = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.runtime.livedata)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
// Source: https://mvnrepository.com/artifact/org.jetbrains.kotlin/kotlin-dataframe
    // Source: https://mvnrepository.com/artifact/ch.qos.logback/logback-classic
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.transformer)

    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.accompanist.permissions)

    implementation(project(":common"))
    implementation(project(":carservice"))
    implementation(libs.androidx.app)
    implementation(libs.androidx.app.projected)
    implementation(libs.mp3agic)
    implementation(libs.coil.compose)
    implementation(libs.lets.plot.compose)
    implementation(libs.lets.plot.main)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}