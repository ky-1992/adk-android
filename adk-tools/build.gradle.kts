plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}
android {
    namespace = "com.google.adk.tools"
    compileSdk = 35
    defaultConfig { minSdk = 34 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
}
dependencies {
    implementation(project(":adk-core"))
    implementation(libs.okhttp)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.android)
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.api)
    testImplementation(libs.junit.engine)
    testImplementation(libs.coroutines.test)
}
