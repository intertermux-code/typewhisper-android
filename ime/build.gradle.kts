plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.typewhisper.android.ime"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    jvmToolchain(libs.versions.javaTarget.get().toInt())
}

dependencies {
    api(project(":engine-api"))
    implementation(project(":capture"))
    implementation(project(":core"))
    implementation(project(":storage"))

    implementation(libs.coroutines.android)
    // Room's RoomDatabase supertype must be visible here to close the database in onDestroy.
    implementation(libs.room.runtime)

    testImplementation(libs.junit)
}
