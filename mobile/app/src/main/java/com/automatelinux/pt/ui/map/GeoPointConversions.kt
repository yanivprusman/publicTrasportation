package com.automatelinux.pt.ui.map

import com.automatelinux.pt.map.LatLng
import org.osmdroid.util.GeoPoint

/**
 * The Android edge of the map seam.
 *
 * Shared code speaks [LatLng]; osmdroid speaks [GeoPoint]. Converting here — rather than
 * letting osmdroid types leak into commonMain — is what allows geometry like the MOTIS
 * polyline decoder to be shared, and what will let iOS bind the same geometry to MapLibre
 * without touching any of it.
 */
fun LatLng.toGeoPoint(): GeoPoint = GeoPoint(latitude, longitude)

fun List<LatLng>.toGeoPoints(): List<GeoPoint> = map { it.toGeoPoint() }
