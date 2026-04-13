package com.automatelinux.pt.util

import org.osmdroid.util.GeoPoint

/**
 * Decodes encoded polyline strings from MOTIS.
 * MOTIS v2 uses precision 7 (NOT the standard Google precision 5).
 */
object PolylineDecoder {

    fun decode(encoded: String, precision: Int = 7): List<GeoPoint> {
        val factor = Math.pow(10.0, precision.toDouble())
        val points = mutableListOf<GeoPoint>()
        var index = 0
        var lat = 0
        var lng = 0

        while (index < encoded.length) {
            var result = 0
            var shift = 0
            var b: Int
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            result = 0
            shift = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            points.add(GeoPoint(lat / factor, lng / factor))
        }

        return points
    }
}
