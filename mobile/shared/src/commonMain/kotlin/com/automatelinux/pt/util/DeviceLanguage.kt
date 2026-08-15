package com.automatelinux.pt.util

/** The device's own language, as the platform reports it ("he", "iw", "en-US", …). */
expect fun platformLanguageTag(): String

/**
 * Which of the app's two languages this device should open in.
 *
 * Everything that is not Hebrew is served English, because those are the only two
 * string sets that exist. Hebrew answers to two codes: Java froze "iw" into
 * `Locale.getDefault().language` decades ago and still returns it on plenty of
 * Android builds, so a check for "he" alone silently sends every Hebrew phone to
 * the English UI — which is the bug this function exists to end.
 */
fun deviceLanguage(): String {
    val tag = platformLanguageTag().lowercase()
    return if (tag.startsWith("he") || tag.startsWith("iw")) "he" else "en"
}
