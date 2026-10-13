package com.automatelinux.pt.ui.util

import androidx.compose.runtime.Composable

/**
 * The system back gesture (an edge swipe, or the back button) steps out of whatever
 * is open, one level at a time. Without a handler Android finishes the activity, so
 * a swipe meant to close a card closed the whole app.
 *
 * Like Android's own BackHandler, the handler composed LAST wins — an inner screen
 * registers its own and outranks the screens beneath it. Compose Multiplatform 1.7
 * has no common back API, hence the seam; iOS has no back gesture to answer.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
