package com.automatelinux.pt.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.automatelinux.pt.R

/**
 * Rubik, bundled: its Hebrew and Latin are drawn as one face, so a line such as
 * "קו 480 · Platform 3" does not change voice halfway, and it carries tabular
 * figures for the times. Static cuts rather than the variable file — the weights
 * the type scale uses and no others.
 */
val PtFontFamily = FontFamily(
    Font(R.font.rubik_400, FontWeight.Normal),
    Font(R.font.rubik_500, FontWeight.Medium),
    Font(R.font.rubik_600, FontWeight.SemiBold),
    Font(R.font.rubik_700, FontWeight.Bold),
)
