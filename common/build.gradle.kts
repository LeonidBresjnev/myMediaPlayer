
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

android {
    namespace = "com.equalizer.common"
    compileSdk = 37
    
    // Find NDK dynamically from local.properties or common SDK locations
    val localProperties = Properties()
    val localPropertiesFile = project.rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localProperties.load(localPropertiesFile.inputStream())
    }
    val sdkDir = localProperties.getProperty("sdk.dir") ?: System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    val ndkEnv = System.getenv("ANDROID_NDK_HOME")
    val ndkBaseDir = if (sdkDir != null) file("$sdkDir/ndk") else null
    val latestNdk = if (ndkEnv != null && file(ndkEnv).exists()) file(ndkEnv) 
                    else ndkBaseDir?.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.name }?.firstOrNull()

    // Set ndkVersion to the latest one found if not already specified
    if (latestNdk != null) {
        ndkVersion = latestNdk.name
    }

    defaultConfig {
        minSdk = 33

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++2a"
                arguments += "-DANDROID_STL=c++_shared"
                arguments += "-DANDROID_NATIVE_API_LEVEL=33"
                arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"

                val vcpkgPath = file("src/main/cpp/vcpkg/scripts/buildsystems/vcpkg.cmake")
                if (vcpkgPath.exists()) {
                    val vcpkgPathStr = vcpkgPath.absolutePath.replace("\\", "/")
                    arguments += "-DCMAKE_TOOLCHAIN_FILE=$vcpkgPathStr"
                    arguments += "-DVCPKG_TARGET_TRIPLET=arm64-android"
                    
                    if (latestNdk != null) {
                        val ndkPathStr = latestNdk.absolutePath.replace("\\", "/")
                        arguments += "-DANDROID_NDK_HOME=$ndkPathStr"
                        arguments += "-DANDROID_NDK=$ndkPathStr"
                        arguments += "-DCMAKE_ANDROID_NDK=$ndkPathStr"
                        
                        val chainloadToolchain = "$ndkPathStr/build/cmake/android.toolchain.cmake"
                        arguments += "-DVCPKG_CHAINLOAD_TOOLCHAIN_FILE=$chainloadToolchain"
                    }
                }
            }
        }

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true

            // Enables resource shrinking.
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }/*
    kotlinOptions {
        jvmTarget = "11"
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }*/
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    buildFeatures {
        prefab = true
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}



dependencies {


  //  implementation(libs.androidx.media3.ui)
  //  implementation(libs.androidx.media3.common)

    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.oboe)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.mp3agic)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
   // implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
