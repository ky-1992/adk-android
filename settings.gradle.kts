pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "adk-android"
include(":adk-core")
include(":adk-ark")
include(":adk-session-sqlite")
include(":adk-tools")
include(":adk-appfunction")
include(":demo")
