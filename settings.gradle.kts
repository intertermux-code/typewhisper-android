pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "typewhisper-android"

include(":engine-api")
include(":core")
include(":capture")
include(":storage")
include(":engines:whispercpp")
include(":ime")
include(":engines:cloud")
include(":engines:mac")
include(":app")
