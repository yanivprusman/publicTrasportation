package com.automatelinux.pt.map

/**
 * A geographic point, independent of any map engine.
 *
 * The app's geometry — decoded MOTIS polylines, marker positions, camera targets — is
 * expressed in this type so it can live in commonMain. Each platform converts to its own
 * engine's point type at the edge: osmdroid's `GeoPoint` on Android, MapLibre's
 * coordinate type on iOS.
 */
data class LatLng(
    val latitude: Double,
    val longitude: Double
)
