import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
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
            // Shared deps (kotlinx-serialization, Ktor, Koin, kotlinx-datetime,
            // multiplatform-settings, Compose MP) are introduced as code migrates.
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
