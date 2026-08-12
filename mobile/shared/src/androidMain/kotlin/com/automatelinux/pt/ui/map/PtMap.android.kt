package com.automatelinux.pt.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.automatelinux.pt.data.model.StopResult
import com.automatelinux.pt.map.LatLng
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * The Android map: osmdroid, driven from the shared declarative description.
 *
 * Deliberately a wrapper, not a rewrite. The overlay code beneath this has been rendering
 * correctly for a long time, and swapping it for MapLibre would put the platform people
 * actually use at risk for benefits that only arrive once iOS exists
 * (IOS_PORT_INVENTORY §6, Option A). This adapts that working code to the seam; iOS gets a
 * MapLibre implementation of the same signature.
 */
@Composable
actual fun PtMap(
    state: PtMapState,
    overlays: PtMapOverlays,
    modifier: Modifier,
    style: PtMapStyle,
    onLongPress: ((LatLng) -> Unit)?,
    onUserPan: (() -> Unit)?,
    onCameraChanged: ((LatLng, Double) -> Unit)?,
    onStopTap: ((StopResult) -> Unit)?
) {
    var map by remember { mutableStateOf<MapView?>(null) }

    OsmMapView(
        modifier = modifier,
        center = state.camera.center.toGeoPoint(),
        zoom = state.camera.zoom,
        mapStyle = style.toOsmdroidStyle(),
        onMapReady = { map = it },
        onLongPress = onLongPress?.let { cb -> { point -> cb(point.toLatLng()) } },
        onUserPan = onUserPan,
        onMapChanged = { center, zoom ->
            // Keep the hoisted camera in step with the user's gestures, so shared code
            // can read where the map is without touching the map view.
            state.camera = PtMapCamera(center.toLatLng(), zoom)
            onCameraChanged?.invoke(center.toLatLng(), zoom)
        }
    ) { mapView ->
        RouteOverlay(mapView, overlays.itinerary)
        OriginDestinationMarkers(
            map = mapView,
            origin = overlays.origin?.toGeoPoint(),
            destination = overlays.destination?.toGeoPoint(),
            via = overlays.via?.toGeoPoint(),
            // osmdroid draws overlays in list order, so these must be re-appended
            // whenever the route redraws or the route's polylines would cover them.
            redrawKey = overlays.itinerary
        )
        VehicleMarkerOverlay(mapView, overlays.vehicles, overlays.vehiclesVisible)
        StopMarkersOverlay(mapView, overlays.stops, onStopTap)
        overlays.lineShape?.let { LineShapeOverlay(mapView, it, overlays.lineShapeFitsCamera) }
        TrackedBusOverlay(mapView, overlays.trackedBus)
    }

    // The device's own position. The overlay owns a location subscription, so it is torn
    // down when the map goes away, the icon changes, or the caller stops asking for it —
    // leaving it running would keep GPS active behind a screen that is no longer visible.
    val currentMap = map
    if (overlays.showUserLocation && currentMap != null) {
        DisposableEffect(currentMap, overlays.userLocationIcon) {
            // Clear any previous marker first: re-adding without removing would stack
            // overlays every time the icon setting changed.
            currentMap.overlays.filterIsInstance<MyLocationNewOverlay>().forEach { it.disableMyLocation() }
            currentMap.overlays.removeAll { it is MyLocationNewOverlay }

            val overlay = MyLocationNewOverlay(currentMap)
            if (overlays.userLocationIcon == PtUserLocationIcon.DOT) {
                val dot = GpsLocationOverlay.createBlueDotBitmap(currentMap.resources.displayMetrics.density)
                overlay.setPersonIcon(dot)
                overlay.setPersonHotspot(dot.width / 2f, dot.height / 2f)
                overlay.setDirectionIcon(dot)
                overlay.setDirectionAnchor(0.5f, 0.5f)
                overlay.isDrawAccuracyEnabled = false
            }
            overlay.enableMyLocation()
            currentMap.overlays.add(overlay)
            currentMap.invalidate()

            onDispose {
                overlay.disableMyLocation()
                currentMap.overlays.remove(overlay)
            }
        }
    }

    // Camera commands arrive as (serial, command) so a repeated identical request still
    // fires; acknowledging by serial keeps it from being replayed on the next recomposition.
    val pending = state.pendingCommand
    LaunchedEffect(pending, map) {
        val (serial, command) = pending ?: return@LaunchedEffect
        val target = map ?: return@LaunchedEffect
        when (command) {
            is PtMapCommand.AnimateTo -> target.animateToPoint(command.point.toGeoPoint(), command.zoom)
            is PtMapCommand.FitBounds -> target.fitBounds(command.points.toGeoPoints(), command.padding)
            PtMapCommand.ZoomIn -> target.controller.zoomIn()
            PtMapCommand.ZoomOut -> target.controller.zoomOut()
        }
        state.consume(serial)
    }
}

private fun PtMapStyle.toOsmdroidStyle(): String = when (this) {
    PtMapStyle.DARK -> MapStyles.DARK
    PtMapStyle.LIGHT -> MapStyles.LIGHT
    PtMapStyle.SATELLITE -> MapStyles.SATELLITE
}
