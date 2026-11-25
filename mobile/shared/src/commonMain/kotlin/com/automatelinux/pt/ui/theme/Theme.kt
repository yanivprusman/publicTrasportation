package com.automatelinux.pt.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The one accent. Light enough to be read as text on the sheet (7:1 on [PtSurface])
 * and to carry near-black ink as a button ground — the previous #1565C0 was 2.6:1 as
 * link text, which is why every "tap me" label on the dark sheet looked switched off.
 */
val PtBlue = Color(0xFF6BA6FF)
val PtBlueDark = Color(0xFF16345C)
val PtBlueLight = Color(0xFFD6E6FF)
val WalkGrey = Color(0xFF888888)
val BusGreen = Color(0xFF4CAF50)
val RailBlue = Color(0xFF2196F3)
val TramOrange = Color(0xFFFF5722)
val SubwayPurple = Color(0xFF9C27B0)

/**
 * "You are here" blue. The map's location dot and the planner's origin marker only
 * read as the same thing while they are literally the same colour, so both take it
 * from here rather than each carrying its own literal.
 */
val CurrentLocationBlue = Color(0xFF4285F4)

// The grounds, darkest to lightest. One cool hue at rising lightness: depth is said by
// which step a thing sits on, never by a shadow or an outline of its own.
val PtBackground = Color(0xFF0A0C10)
val PtSurface = Color(0xFF12151A)
val PtSurfaceLow = Color(0xFF171B21)
val PtSurfaceMid = Color(0xFF1C2128)
val PtSurfaceHigh = Color(0xFF242A33)
val PtSurfaceHighest = Color(0xFF2E3541)
val PtOutline = Color(0xFF465061)
val PtOutlineSoft = Color(0xFF2A313B)
val PtInk = Color(0xFFEEF1F6)
val PtInkSoft = Color(0xFFA9B2C1)

// State lamps. Colour in this app says something happened or is about to; these are
// the only three things it says besides "selected".
/** A vehicle is reporting / on time / paid. */
val PtLive = Color(0xFF5BD68A)
/** Hurry, or a caveat worth a second look. */
val PtWarn = Color(0xFFFFB84D)
/** Another day than today — the mark a night search for tomorrow must not lose. */
val PtOtherDay = Color(0xFFB9A6F0)

val DarkColorScheme = darkColorScheme(
    primary = PtBlue,
    onPrimary = Color(0xFF051B3A),
    primaryContainer = PtBlueDark,
    onPrimaryContainer = PtBlueLight,
    inversePrimary = Color(0xFF2B62B8),
    secondary = Color(0xFFA9BEDC),
    onSecondary = Color(0xFF10233D),
    secondaryContainer = Color(0xFF223450),
    onSecondaryContainer = Color(0xFFDCE8F8),
    tertiary = PtWarn,
    onTertiary = Color(0xFF3A2500),
    tertiaryContainer = Color(0xFF4A3410),
    onTertiaryContainer = Color(0xFFFFE2B0),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF4A0A06),
    errorContainer = Color(0xFF5A1D18),
    onErrorContainer = Color(0xFFFFDAD6),
    background = PtBackground,
    onBackground = PtInk,
    surface = PtSurface,
    onSurface = PtInk,
    surfaceVariant = PtSurfaceHigh,
    onSurfaceVariant = PtInkSoft,
    // Material lays the primary over every raised surface as a "tonal elevation"; on
    // this palette that turned menus and dialogs a muddy blue-grey of their own.
    surfaceTint = Color.Transparent,
    inverseSurface = PtInk,
    inverseOnSurface = PtSurface,
    outline = PtOutline,
    outlineVariant = PtOutlineSoft,
    scrim = Color.Black,
    surfaceBright = PtSurfaceHighest,
    surfaceDim = PtBackground,
    surfaceContainerLowest = PtBackground,
    surfaceContainerLow = PtSurfaceLow,
    surfaceContainer = PtSurfaceMid,
    surfaceContainerHigh = PtSurfaceHigh,
    surfaceContainerHighest = PtSurfaceHighest,
)

/**
 * Corners, as one family: controls 12, cards 16, sheets and dialogs 24–28. The radii
 * grow with the size of the thing, so a chip inside a card inside the sheet reads as
 * three nested shapes rather than three unrelated ones.
 */
val PtShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * The type scale on [family]. Sizes stay within half a point of Material's so no
 * layout that was measured against the old text breaks; what changes is the face,
 * the weights (titles and labels carry real weight, body stays light) and two things
 * a timetable needs: tabular figures, so a column of times does not shimmer as the
 * minutes change, and no letter-spacing, which Material adds for Latin capitals and
 * which pulls Hebrew words apart.
 */
fun ptTypography(family: FontFamily): Typography {
    fun style(size: Int, line: Int, weight: FontWeight) = TextStyle(
        fontFamily = family,
        fontSize = size.sp,
        lineHeight = line.sp,
        fontWeight = weight,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum",
    )
    return Typography(
        displayLarge = style(52, 60, FontWeight.Bold),
        displayMedium = style(42, 50, FontWeight.Bold),
        displaySmall = style(34, 42, FontWeight.Bold),
        headlineLarge = style(30, 38, FontWeight.Bold),
        headlineMedium = style(26, 34, FontWeight.Bold),
        headlineSmall = style(23, 30, FontWeight.SemiBold),
        titleLarge = style(21, 28, FontWeight.SemiBold),
        titleMedium = style(17, 24, FontWeight.SemiBold),
        titleSmall = style(15, 21, FontWeight.SemiBold),
        bodyLarge = style(16, 24, FontWeight.Normal),
        bodyMedium = style(14, 20, FontWeight.Normal),
        bodySmall = style(12, 17, FontWeight.Normal),
        labelLarge = style(14, 20, FontWeight.Medium),
        labelMedium = style(12, 16, FontWeight.Medium),
        labelSmall = style(11, 15, FontWeight.Medium),
    )
}

/**
 * [fontFamily] is handed in by the platform rather than loaded here: a bundled font
 * is an Android resource on one side and a bundle file on the other, and this module
 * is common to both. Android passes Rubik (Hebrew and Latin cut by the same hand).
 */
@Composable
fun PTTheme(
    fontFamily: FontFamily = FontFamily.Default,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = ptTypography(fontFamily),
        shapes = PtShapes,
        content = content
    )
}
