import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// Shared Kotlin Multiplatform module for the PT app.
// commonMain holds code that runs on BOTH Android and iOS; androidMain / iosMain
// hold the thin platform-specific actuals. See:
//   /opt/automateLinux/.claude/docs/kmp-migration-playbook.md
kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    // iosArm64() / iosSimulatorArm64() are added in a later slice (needs the Mac to build).

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.multiplatform.settings)
            // Ktor is `api`, not `implementation`: :app injects the PtApi instance
            // through Hilt, so its consumers see Ktor types on the boundary.
            api(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            api(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            api(libs.jb.lifecycle.viewmodel)
            implementation(libs.jb.lifecycle.viewmodel.compose)
        }
        // The engine is the one part of the HTTP stack that cannot be common:
        // OkHttp here, Darwin on iOS. Everything else (timeouts, JSON, logging,
        // peer failover) is configured once in commonMain.
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
    }
}

android {
    namespace = "com.automatelinux.pt.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
