plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

// feedback-lib was authored for Kotlin 1.x (composeOptions block).
// Kotlin 2.x requires the Compose Compiler Gradle plugin instead.
// Also add lifecycle-runtime-compose for collectAsStateWithLifecycle.
project(":feedback-lib") {
    afterEvaluate {
        plugins.apply("org.jetbrains.kotlin.plugin.compose")
        dependencies {
            add("implementation", "androidx.lifecycle:lifecycle-runtime-compose:${libs.versions.lifecycle.get()}")
        }
    }
}
