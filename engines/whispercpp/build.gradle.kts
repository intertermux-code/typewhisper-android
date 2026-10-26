plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.typewhisper.android.engines.whispercpp"
    compileSdk = libs.versions.compileSdk.get().toInt()
    ndkVersion = "26.3.11579264"

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        ndk {
            // arm64-v8a is the deliverable; x86_64 is included so the engine also
            // runs on the emulator. Both build from the CPU-only ggml sources.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_static",
                    "-DANDROID_ARM_NEON=ON",
                )
                cppFlags += "-std=c++17"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.5"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Native unit tests are not part of this milestone; the JNI path is verified
    // on-device. Only the pure Kotlin logic runs under :test.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    jvmToolchain(libs.versions.javaTarget.get().toInt())
}

dependencies {
    api(project(":engine-api"))
    implementation(libs.coroutines.android)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.json)
}
