plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    id("android-flavors")
}

val gitCommitCount = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
}.standardOutput.asText.get().trim().toIntOrNull() ?: 1

val gitShortHash = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
}.standardOutput.asText.get().trim()

android {
    namespace = "com.automatelinux.pt"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.automatelinux.pt"
        minSdk = 26
        targetSdk = 35
        versionCode = gitCommitCount
        versionName = "v${gitCommitCount} (${gitShortHash})"
    }

    productFlavors {
        getByName("dev") {
            buildConfigField("int", "SERVER_PORT", "3003")
        }
        getByName("prod") {
            buildConfigField("int", "SERVER_PORT", "3002")
        }
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Shared KMP module (commonMain code shared with iOS)
    implementation(project(":shared"))
    implementation(libs.kotlinx.datetime)
    implementation(libs.multiplatform.settings)

    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    // Lifecycle
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.converter.gson) // still used by dev-flavor FeedbackModule
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Core
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)

    // Map
    implementation(libs.osmdroid)

    // Location
    implementation(libs.play.services.location)

    // Feedback lib (dev flavor only)
    "devImplementation"(project(":feedback-lib"))
}
