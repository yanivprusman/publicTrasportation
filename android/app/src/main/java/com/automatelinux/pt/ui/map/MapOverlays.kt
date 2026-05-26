package com.automatelinux.pt.ui.map

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.animation.LinearInterpolator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.automatelinux.pt.data.model.Itinerary
import com.automatelinux.pt.data.model.TransitMode
import com.automatelinux.pt.data.model.VehicleMarker
import com.automatelinux.pt.util.PolylineDecoder
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline

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
        map.overlays.filterIsInstance<AnimatedOriginOverlay>().forEach { it.stopAnimation() }
        map.overlays.removeAll { it is AnimatedOriginOverlay }
        map.overlays.removeAll { (it as? Marker)?.id == "dest" }

        origin?.let { pt ->
            map.overlays.add(AnimatedOriginOverlay(pt, map))
        }

        destination?.let { pt ->
            val density = map.resources.displayMetrics.density
            val marker = Marker(map).apply {
                id = "dest"
                position = pt
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = createDiamondPinDrawable(density)
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

private fun createDiamondPinDrawable(density: Float): android.graphics.drawable.Drawable {
    val width = (30 * density).toInt()
    val height = (40 * density).toInt()

    return object : android.graphics.drawable.Drawable() {
        override fun draw(canvas: Canvas) {
            val cx = bounds.centerX().toFloat()
            val top = bounds.top.toFloat()
            val bottom = bounds.bottom.toFloat()
            val d = density
            val diamondCy = top + 11f * d

            // Stem
            val stemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    cx, top + 20f * d, cx, bottom,
                    Color.parseColor("#E65100"), Color.parseColor("#BF360C"),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(
                cx - 1.5f * d, top + 20f * d,
                cx + 1.5f * d, bottom,
                1.5f * d, 1.5f * d, stemPaint
            )

            // Diamond (rotated square)
            canvas.save()
            canvas.rotate(45f, cx, diamondCy)

            val dSize = 15f * d
            val diamondPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    cx - dSize / 2f, diamondCy - dSize / 2f,
                    cx + dSize / 2f, diamondCy + dSize / 2f,
                    intArrayOf(
                        Color.parseColor("#FFB74D"),
                        Color.parseColor("#E65100"),
                        Color.parseColor("#FF8A65")
                    ),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(
                cx - dSize / 2f, diamondCy - dSize / 2f,
                cx + dSize / 2f, diamondCy + dSize / 2f,
                3f * d, 3f * d, diamondPaint
            )

            // Inner white diamond
            val innerSize = 5.5f * d
            val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(216, 255, 255, 255)
            }
            canvas.drawRoundRect(
                cx - innerSize / 2f, diamondCy - innerSize / 2f,
                cx + innerSize / 2f, diamondCy + innerSize / 2f,
                1f * d, 1f * d, innerPaint
            )

            canvas.restore()
        }

        override fun getIntrinsicWidth() = width
        override fun getIntrinsicHeight() = height
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
        @Deprecated("Deprecated")
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }
}

@Composable
fun StopMarkersOverlay(
    map: MapView,
    stops: List<com.automatelinux.pt.data.model.StopResult>,
    onStopTap: ((com.automatelinux.pt.data.model.StopResult) -> Unit)? = null
) {
    LaunchedEffect(stops) {
        map.overlays.removeAll { (it as? Marker)?.id == "stop_marker" }

        for (stop in stops) {
            val marker = Marker(map).apply {
                id = "stop_marker"
                position = GeoPoint(stop.lat, stop.lon)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = stop.stopName
                snippet = stop.stopCode
                icon = createCircleDrawable(Color.parseColor("#1976D2"), 16, Color.WHITE, 3f)
                setInfoWindow(null)
                if (onStopTap != null) {
                    setOnMarkerClickListener { _, _ ->
                        onStopTap(stop)
                        true
                    }
                }
            }
            map.overlays.add(marker)
        }

        map.invalidate()
    }
}

@Composable
fun LineShapeOverlay(
    map: MapView,
    directions: Map<String, List<List<Double>>>
) {
    LaunchedEffect(directions) {
        map.overlays.removeAll { (it as? Polyline)?.id == "line_shape" }

        if (directions.isNotEmpty()) {
            val allPoints = mutableListOf<GeoPoint>()
            val colors = listOf(
                Color.parseColor("#2196F3"),
                Color.parseColor("#FF5722")
            )
            var colorIdx = 0
            for ((_, points) in directions) {
                val geoPoints = points.mapNotNull { coord ->
                    if (coord.size >= 2) GeoPoint(coord[0], coord[1]) else null
                }
                if (geoPoints.isEmpty()) continue
                allPoints.addAll(geoPoints)

                val polyline = Polyline(map).apply {
                    id = "line_shape"
                    setPoints(geoPoints)
                    outlinePaint.color = colors[colorIdx % colors.size]
                    outlinePaint.strokeWidth = 6f
                    outlinePaint.isAntiAlias = true
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                }
                map.overlays.add(polyline)
                colorIdx++
            }

            if (allPoints.isNotEmpty()) {
                map.fitBounds(allPoints)
            }
        }

        map.invalidate()
    }
}

@Composable
fun TrackedBusOverlay(
    map: MapView,
    marker: VehicleMarker?
) {
    LaunchedEffect(marker) {
        map.overlays.removeAll { (it as? Marker)?.id == "tracked_bus" }

        if (marker != null) {
            val m = Marker(map).apply {
                id = "tracked_bus"
                position = GeoPoint(marker.lat, marker.lon)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = "Line ${marker.lineNumber}"
                snippet = "Tracking"
                icon = createCircleDrawable(Color.parseColor("#FF6D00"), 14, Color.WHITE, 3f)
                setInfoWindow(null)
            }
            map.overlays.add(m)
        }

        map.invalidate()
    }
}

class AnimatedOriginOverlay(
    private val position: GeoPoint,
    mapView: MapView
) : Overlay() {

    private var progress = 0f
    private val density = mapView.resources.displayMetrics.density

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2400L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { anim ->
            progress = anim.animatedFraction
            mapView.postInvalidate()
        }
        start()
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return

        val screenPoint = Point()
        mapView.projection.toPixels(position, screenPoint)
        val cx = screenPoint.x.toFloat()
        val cy = screenPoint.y.toFloat()
        val coreRadius = 7f * density

        // Glow
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = RadialGradient(
                cx, cy, coreRadius * 2.5f,
                Color.argb(70, 0, 188, 212), Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, coreRadius * 2.5f, glowPaint)

        // 3 staggered ripple rings
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
        }
        for (i in 0..2) {
            val ringProgress = (progress + i / 3f) % 1f
            val scale = 0.5f + ringProgress * 1.7f
            ringPaint.color = Color.parseColor("#00BCD4")
            ringPaint.alpha = ((1f - ringProgress) * 178).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, coreRadius * scale, ringPaint)
        }

        // White border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        canvas.drawCircle(cx, cy, coreRadius + 2f * density, borderPaint)

        // Core with radial gradient
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = RadialGradient(
                cx - coreRadius * 0.15f, cy - coreRadius * 0.15f, coreRadius,
                Color.parseColor("#4DD0E1"), Color.parseColor("#00838F"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, coreRadius, corePaint)
    }

    fun stopAnimation() {
        animator.cancel()
    }

    override fun onDetach(mapView: MapView) {
        animator.cancel()
        super.onDetach(mapView)
    }
}
