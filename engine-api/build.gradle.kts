plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(libs.versions.javaTarget.get().toInt())
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.core)
}
