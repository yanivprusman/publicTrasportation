package com.automatelinux.pt.ui.map

import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.automatelinux.pt.data.model.Itinerary
import com.automatelinux.pt.data.model.TransitMode
import com.automatelinux.pt.data.model.VehicleMarker
import com.automatelinux.pt.util.PolylineDecoder
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.simplefastpoint.SimplePointTheme
import org.osmdroid.views.overlay.simplefastpoint.SimpleFastPointOverlay
import org.osmdroid.views.overlay.simplefastpoint.SimpleFastPointOverlayOptions

private const val TAG_ROUTE = "route_overlay"
private const val TAG_MARKER = "map_marker"

fun getModeColor(mode: TransitMode): Int = when (mode) {
    TransitMode.WALK -> Color.parseColor("#4A90D9")
    TransitMode.BUS -> Color.parseColor("#4CAF50")
    TransitMode.RAIL -> Color.parseColor("#2196F3")
    TransitMode.TRAM -> Color.parseColor("#FF5722")
    TransitMode.SUBWAY -> Color.parseColor("#9C27B0")
    TransitMode.FERRY -> Color.parseColor("#00ACC1")
}

fun getModeColorWithRoute(mode: TransitMode, routeColor: String?): Int {
    if (mode == TransitMode.WALK) return getModeColor(mode)
    if (routeColor != null && routeColor.isNotBlank()) {
        try {
            return Color.parseColor(if (routeColor.startsWith("#")) routeColor else "#$routeColor")
        } catch (_: Exception) {}
    }
    return getModeColor(mode)
}

@Composable
fun RouteOverlay(
    map: MapView,
    itinerary: Itinerary?
) {
    LaunchedEffect(itinerary) {
        // Clear previous route overlays
        map.overlays.removeAll { (it as? Polyline)?.id == TAG_ROUTE }
        map.overlays.removeAll { (it as? Marker)?.id == TAG_MARKER }

        if (itinerary == null) {
            map.invalidate()
            return@LaunchedEffect
        }

        val allPoints = mutableListOf<GeoPoint>()

        // Draw polylines per leg
        for (leg in itinerary.legs) {
            if (leg.polyline.isBlank()) continue
            val points = PolylineDecoder.decode(leg.polyline)
            if (points.isEmpty()) continue
            allPoints.addAll(points)

            val color = getModeColorWithRoute(leg.mode, leg.routeColor)

            val isWalk = leg.mode == TransitMode.WALK
            val polyline = Polyline(map).apply {
                id = TAG_ROUTE
                setPoints(points)
                outlinePaint.color = color
                outlinePaint.strokeWidth = if (isWalk) 14f else 8f
                outlinePaint.isAntiAlias = true
                outlinePaint.strokeCap = Paint.Cap.ROUND
                if (isWalk) {
                    outlinePaint.pathEffect = DashPathEffect(floatArrayOf(24f, 14f), 0f)
                }
            }
            map.overlays.add(polyline)

            // Draw stop markers for transit legs
            if (leg.mode != TransitMode.WALK) {
                val stops = mutableListOf<GeoPoint>()
                stops.add(GeoPoint(leg.from.lat, leg.from.lon))
                leg.intermediateStops?.forEach { stop ->
                    stops.add(GeoPoint(stop.lat, stop.lon))
                }
                stops.add(GeoPoint(leg.to.lat, leg.to.lon))

                for (stop in stops) {
                    val marker = Marker(map).apply {
                        id = TAG_MARKER
                        position = stop
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = createCircleDrawable(Color.WHITE, 5, color, 2f)
                        setInfoWindow(null)
                    }
                    map.overlays.add(marker)
                }
            }
        }

        // Draw transfer markers (larger, dark)
        for (i in 0 until itinerary.legs.size - 1) {
            val leg = itinerary.legs[i]
            val transferPoint = GeoPoint(leg.to.lat, leg.to.lon)
            val marker = Marker(map).apply {
                id = TAG_MARKER
                position = transferPoint
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = createCircleDrawable(Color.DKGRAY, 12, Color.WHITE, 2f)
                setInfoWindow(null)
            }
            map.overlays.add(marker)
        }

        // Fit bounds to show entire route
        if (allPoints.isNotEmpty()) {
            map.fitBounds(allPoints)
        }

        map.invalidate()
    }
}

@Composable
fun OriginDestinationMarkers(
    map: MapView,
    origin: GeoPoint?,
    destination: GeoPoint?
) {
    LaunchedEffect(origin, destination) {
        map.overlays.removeAll { (it as? Marker)?.id == "origin" || (it as? Marker)?.id == "dest" }

        origin?.let { pt ->
            val marker = Marker(map).apply {
                id = "origin"
                position = pt
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Origin"
                icon = createCircleDrawable(Color.parseColor("#00BCD4"), 16, Color.WHITE, 3f)
                setInfoWindow(null)
            }
            map.overlays.add(marker)
        }

        destination?.let { pt ->
            val marker = Marker(map).apply {
                id = "dest"
                position = pt
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Destination"
                icon = createCircleDrawable(Color.parseColor("#FF9800"), 16, Color.WHITE, 3f)
                setInfoWindow(null)
            }
            map.overlays.add(marker)
        }

        map.invalidate()
    }
}

@Composable
fun VehicleMarkerOverlay(
    map: MapView,
    markers: List<VehicleMarker>,
    visible: Boolean
) {
    LaunchedEffect(markers, visible) {
        map.overlays.removeAll { (it as? Marker)?.id == "vehicle" }

        if (visible) {
            for (vm in markers) {
                val marker = Marker(map).apply {
                    id = "vehicle"
                    position = GeoPoint(vm.lat, vm.lon)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = "Line ${vm.lineNumber}"
                    snippet = "Vehicle: ${vm.vehicleRef}"
                    icon = createCircleDrawable(Color.parseColor("#E91E63"), 10, Color.WHITE, 2f)
                }
                map.overlays.add(marker)
            }
        }

        map.invalidate()
    }
}

private fun createCircleDrawable(
    fillColor: Int,
    radius: Int,
    strokeColor: Int = Color.TRANSPARENT,
    strokeWidth: Float = 0f
): android.graphics.drawable.Drawable {
    val size = (radius * 2 + strokeWidth * 2).toInt()
    return object : android.graphics.drawable.Drawable() {
        override fun draw(canvas: android.graphics.Canvas) {
            val cx = bounds.centerX().toFloat()
            val cy = bounds.centerY().toFloat()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            if (strokeWidth > 0f) {
                paint.color = strokeColor
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, radius + strokeWidth, paint)
            }

            paint.color = fillColor
            paint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, radius.toFloat(), paint)
        }

        override fun getIntrinsicWidth() = size
        override fun getIntrinsicHeight() = size

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}

        @Deprecated("Deprecated")
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }
}
