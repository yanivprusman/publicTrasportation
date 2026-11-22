package com.automatelinux.pt.ui.util

import androidx.compose.runtime.Composable

/**
 * Speech-to-text for a search field: returns a launcher that opens the platform's
 * recognizer and hands the first transcript to [onResult], or null where the
 * platform has no recognizer wired up — the caller then shows no mic at all.
 *
 * Android uses the same Google dialog as the AI screen, always in Hebrew: riders say
 * Israeli places in Hebrew, as they do to Waze (see AiScreen for what English
 * recognition did to "הירקון 122").
 */
@Composable
expect fun rememberSpeechInput(onResult: (String) -> Unit): (() -> Unit)?
