package com.automatelinux.pt.util

import kotlin.concurrent.Volatile

object ScreenTracker {
    // Explicitly kotlin.concurrent.Volatile: an unqualified @Volatile resolves to
    // kotlin.jvm.Volatile, which exists on Android and does not exist on iOS.
    @Volatile
    var currentScreen: String? = null
}
