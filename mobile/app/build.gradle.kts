import java.util.Properties

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

// Signing material is read from a gitignored keystore.properties that points at
// a keystore stored outside the repo. When it is absent (CI, a fresh clone, or
// anyone but the release machine) the release build is left unsigned rather
// than silently falling back to the debug key — an app signed with the debug
// key is rejected by Play, and finding that out at upload time wastes a cycle.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val hasSigningConfig = keystoreProps.getProperty("storeFile") != null

// Google Sign-In web client id, supplied via a gitignored oauth.properties.
// Absent, GOOGLE_SIGN_IN_ENABLED is false and the app keeps the manual
// registration path — a missing client id must not fail the build, and must
// not produce a sign-in button that cannot work.
val oauthPropsFile = rootProject.file("oauth.properties")
val oauthProps = Properties().apply {
    if (oauthPropsFile.exists()) oauthPropsFile.inputStream().use { load(it) }
}
val webClientId: String = oauthProps.getProperty("webClientId") ?: ""

android {
    namespace = "com.automatelinux.pt"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.automatelinux.pt"
        minSdk = 26
        targetSdk = 35
        versionCode = gitCommitCount
        versionName = "v${gitCommitCount} (${gitShortHash})"
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$webClientId\"")
        buildConfigField("boolean", "GOOGLE_SIGN_IN_ENABLED", (webClientId.isNotEmpty()).toString())
    }

    productFlavors {
        getByName("dev") {
            buildConfigField("int", "SERVER_PORT", "3003")
            // Probe the WireGuard peers — local development only.
            buildConfigField("boolean", "PEER_SERVERS_ENABLED", "true")
        }
        getByName("prod") {
            buildConfigField("int", "SERVER_PORT", "3002")
            // Public builds talk only to the public URL: the private peer
            // addresses are unreachable from a user's phone, so probing them
            // would stall startup behind connect timeouts on every launch.
            buildConfigField("boolean", "PEER_SERVERS_ENABLED", "false")
        }
    }

    signingConfigs {
        if (hasSigningConfig) {
            create("upload") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("upload")
            }
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

    // QR code for trip sharing
    implementation(libs.zxing.core)

    // Play Install Referrer — referral attribution, readable only once per install
    implementation(libs.install.referrer)

    // Phone Number Hint — one-tap picker of the numbers already on the device.
    // Needs no runtime permission and no OAuth setup.
    implementation(libs.play.services.auth)

    // Feedback lib (dev flavor only)
    "devImplementation"(project(":feedback-lib"))
}
