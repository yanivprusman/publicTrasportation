package com.automatelinux.pt.ui.map

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import com.automatelinux.pt.data.model.TransitMode

/**
 * The colour a transit mode is drawn in, on the map and everywhere else.
 *
 * These used to return an Android ARGB `Int` and live beside the osmdroid overlays, which
 * meant six screens depended on the map package purely to colour a line — and every one of
 * them wrapped the result in a Compose `Color` at the call site anyway. Returning a Compose
 * colour removes that coupling: only the map, which must hand an `Int` to osmdroid, now
 * converts, and it does so at its own edge.
 */
fun getModeColor(mode: TransitMode): Color = when (mode) {
    TransitMode.WALK -> Color(0xFF4A90D9)
    TransitMode.BUS -> Color(0xFF4CAF50)
    TransitMode.RAIL -> Color(0xFF2196F3)
    TransitMode.TRAM -> Color(0xFFFF5722)
    TransitMode.SUBWAY -> Color(0xFF9C27B0)
    TransitMode.FERRY -> Color(0xFF00ACC1)
    TransitMode.BIKE -> Color(0xFF00ACC1)
    TransitMode.CAR -> Color(0xFF546E7A)
}

/**
 * The operator's own colour for a line, falling back to the mode colour.
 *
 * Walking legs always use the mode colour: a walk has no operator, and GTFS feeds
 * sometimes attach one anyway.
 */
fun getModeColorWithRoute(mode: TransitMode, routeColor: String?): Color {
    if (mode == TransitMode.WALK) return getModeColor(mode)
    if (!routeColor.isNullOrBlank()) {
        parseHexColor(routeColor)?.let { return it }
    }
    return getModeColor(mode)
}

/**
 * Parses `RRGGBB` / `#RRGGBB` / `#AARRGGBB` as GTFS feeds supply it — some include the
 * leading `#`, some don't.
 *
 * Returns null rather than throwing on anything malformed, because a bad colour in one
 * operator's feed should tint that line wrong at worst, not fail the whole itinerary.
 * This replaces Android's `Color.parseColor`, which is not available in common code.
 */
private fun parseHexColor(raw: String): Color? {
    val hex = raw.removePrefix("#")
    if (hex.length != 6 && hex.length != 8) return null
    val value = hex.toULongOrNull(radix = 16) ?: return null
    // A six-digit value carries no alpha; GTFS colours are always fully opaque.
    val argb = if (hex.length == 6) value or 0xFF000000uL else value
    return Color(argb.toLong())
}

/**
 * The text colour to print ON [background] — whichever of near-black and white the
 * eye can actually read there.
 *
 * Line badges used to be white on the mode colour unconditionally, which is 2.78:1
 * against this suite's bus green and fails WCAG AA outright; the same mistake is in
 * Moovit's competitors and in Google Maps (2.4:1 white on their orange). Route
 * colours also come from the operator's own GTFS feed, so no fixed pair can be
 * right for all of them — the choice has to follow the background's luminance.
 *
 * Uses the WCAG relative-luminance formula rather than a naive average: mid greens
 * and oranges are far brighter to the eye than their RGB mean suggests, which is
 * exactly why they defeat white text.
 */
fun onColorFor(background: Color): Color =
    if (relativeLuminance(background) > 0.35) Color(0xFF10130F) else Color.White

private fun relativeLuminance(c: Color): Double {
    fun channel(v: Float): Double {
        val d = v.toDouble()
        return if (d <= 0.03928) d / 12.92 else ((d + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
}
