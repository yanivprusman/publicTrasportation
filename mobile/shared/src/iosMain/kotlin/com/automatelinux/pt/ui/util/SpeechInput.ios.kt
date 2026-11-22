package com.automatelinux.pt.ui.util

import androidx.compose.runtime.Composable

/** No recognizer wired up on iOS yet, so its fields show no mic. */
@Composable
actual fun rememberSpeechInput(onResult: (String) -> Unit): (() -> Unit)? = null
