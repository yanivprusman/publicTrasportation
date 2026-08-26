package com.automatelinux.pt.ui.map

import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.toArgb
import com.automatelinux.pt.data.model.Itinerary
import com.automatelinux.pt.data.model.TransitMode
import com.automatelinux.pt.data.model.VehicleMarker
import com.automatelinux.pt.util.PolylineDecoder
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

private const val TAG_ROUTE = "route_overlay"
private const val TAG_MARKER = "map_marker"

@Composable
fun RouteOverlay(
    map: MapView,
    itinerary: Itinerary?
) {
    LaunchedEffect(itinerary) {
        map.overlays.removeAll { (it as? Polyline)?.id == TAG_ROUTE }
        map.overlays.removeAll { (it as? Marker)?.id == TAG_MARKER }

        if (itinerary == null) {
            map.invalidate()
            return@LaunchedEffect
        }

        val allPoints = mutableListOf<GeoPoint>()

        for (leg in itinerary.legs) {
            if (leg.polyline.isBlank()) continue
            val points = PolylineDecoder.decode(leg.polyline).toGeoPoints()
            if (points.isEmpty()) continue
            allPoints.addAll(points)

            // The shared helper returns a Compose colour; osmdroid's Paint wants ARGB.
            val color = getModeColorWithRoute(leg.mode, leg.routeColor).toArgb()

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
                // Same reason every marker in this file does it, and easier to miss here:
                // Polyline(MapView) attaches osmdroid's default bubble by itself, so a tap
                // anywhere along the route popped an empty grey balloon.
                setInfoWindow(null)
            }
            map.overlays.add(polyline)

            // Stop dots only make sense on transit legs; street legs (walk/bike/car)
            // have no boarding points to mark.
            val isStreet = leg.mode == TransitMode.WALK ||
                leg.mode == TransitMode.BIKE || leg.mode == TransitMode.CAR
            if (!isStreet) {
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
    destination: GeoPoint?,
    via: GeoPoint? = null,
    // Re-append the markers when the drawn route changes, so they stay above
    // RouteOverlay's polylines and transfer dots (osmdroid draws in list order).
    redrawKey: Any? = null
) {
    LaunchedEffect(origin, destination, via, redrawKey) {
        (map.overlays.firstOrNull { (it as? Marker)?.id == "origin" }
            as? Marker)?.let { m ->
            (m.icon as? AnimatedOriginDrawable)?.stopAnimation()
        }
        map.overlays.removeAll { (it as? Marker)?.id == "origin" }
        map.overlays.removeAll { (it as? Marker)?.id == "dest" }
        map.overlays.removeAll { (it as? Marker)?.id == "via" }

        via?.let { pt ->
            val marker = Marker(map).apply {
                id = "via"
                position = pt
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = createCircleDrawable(Color.parseColor("#FF9800"), 11, Color.WHITE, 3f)
                setInfoWindow(null)
            }
            map.overlays.add(marker)
        }

        origin?.let { pt ->
            val density = map.resources.displayMetrics.density
            val marker = Marker(map).apply {
                id = "origin"
                position = pt
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = AnimatedOriginDrawable(density, map)
                setInfoWindow(null)
            }
            map.overlays.add(marker)
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
    visible: Boolean,
    onVehicleTap: ((VehicleMarker) -> Unit)? = null
) {
    LaunchedEffect(markers, visible, onVehicleTap != null) {
        map.overlays.removeAll { (it as? Marker)?.id == "vehicle" }

        if (visible) {
            for (vm in markers) {
                val marker = Marker(map).apply {
                    id = "vehicle"
                    position = GeoPoint(vm.lat, vm.lon)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = "Line ${vm.lineNumber}"
                    snippet = "Vehicle: ${vm.vehicleRef}"
                    icon = createBusMarkerDrawable(
                        Color.parseColor("#E91E63"),
                        vm.lineNumber,
                        map.resources.displayMetrics.density,
                        vm.bearingDegrees
                    )
                    if (onVehicleTap != null) {
                        // A dot you cannot act on is decoration; tapping one is how
                        // "that is my bus" turns into tracking it.
                        setInfoWindow(null)
                        setOnMarkerClickListener { _, _ -> onVehicleTap(vm); true }
                    }
                }
                map.overlays.add(marker)
            }
        }

        map.invalidate()
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
    directions: Map<String, List<List<Double>>>,
    /**
     * Whether the shape should claim the camera. True when the line is what the
     * user asked to see (Lines tab). False when it is context under a tracked bus:
     * fitting a 60 km line buries the bus in it, which is the opposite of what
     * tracking is for.
     */
    fitCamera: Boolean = true
) {
    LaunchedEffect(directions, fitCamera) {
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
                    // As above: suppress osmdroid's automatic empty bubble. This one is
                    // the line a tracked bus is driving, so it is the easiest to hit.
                    setInfoWindow(null)
                }
                map.overlays.add(polyline)
                colorIdx++
            }

            if (fitCamera && allPoints.isNotEmpty()) {
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
                // Same marker, the tracked colour. Keeping the shape identical is the point:
                // it is the same bus you tapped, and only its state changed.
                icon = createBusMarkerDrawable(
                    Color.parseColor("#FF6D00"),
                    marker.lineNumber,
                    map.resources.displayMetrics.density,
                    marker.bearingDegrees
                )
                setInfoWindow(null)
            }
            map.overlays.add(m)
        }

        map.invalidate()
    }
}
