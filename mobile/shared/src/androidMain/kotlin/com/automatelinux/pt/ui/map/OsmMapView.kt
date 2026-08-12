package com.automatelinux.pt.ui.map

import android.view.MotionEvent
import java.lang.ref.WeakReference
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay

/**
 * Metres from the centre of the map to the nearest screen edge.
 *
 * osmdroid already holds the real projection, so this asks it rather than converting the
 * zoom number by hand. The nearest edge (rather than the corner) makes it the largest
 * circle wholly on screen, which is what a "what is around here" query should cover.
 */
private fun MapView.visibleRadiusMeters(): Double {
    val box = boundingBox
    val centerLat = box.centerLatitude
    val centerLon = box.centerLongitude
    val center = GeoPoint(centerLat, centerLon)
    val halfHeight = center.distanceToAsDouble(GeoPoint(box.latNorth, centerLon))
    val halfWidth = center.distanceToAsDouble(GeoPoint(centerLat, box.lonEast))
    return minOf(halfHeight, halfWidth)
}

object MapZoomHandler {
    private var mapRef: WeakReference<MapView>? = null

    fun register(map: MapView) { mapRef = WeakReference(map) }
    fun clear() { mapRef = null }

    fun zoomIn() { mapRef?.get()?.controller?.zoomIn() }
    fun zoomOut() { mapRef?.get()?.controller?.zoomOut() }
}

@Composable
fun OsmMapView(
    modifier: Modifier = Modifier,
    center: GeoPoint = GeoPoint(31.77, 35.21),
    zoom: Double = 13.0,
    mapStyle: String = MapStyles.DARK,
    onMapReady: (MapView) -> Unit = {},
    onLongPress: ((GeoPoint) -> Unit)? = null,
    onMapMoved: ((GeoPoint) -> Unit)? = null,
    onUserPan: (() -> Unit)? = null,
    onMapChanged: ((center: GeoPoint, zoom: Double, visibleRadiusMeters: Double) -> Unit)? = null,
    overlayContent: @Composable (MapView) -> Unit = {}
) {
    val context = LocalContext.current
    var mapView by remember { mutableStateOf<MapView?>(null) }

    DisposableEffect(Unit) {
        Configuration.getInstance().userAgentValue = "com.automatelinux.pt"
        onDispose {
            mapView?.onDetach()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).apply {
                setMultiTouchControls(true)
                zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                controller.setZoom(zoom)
                controller.setCenter(center)
                minZoomLevel = 5.0
                maxZoomLevel = 19.0

                overlays.add(CopyrightOverlay(ctx))
                MapStyles.apply(this, mapStyle)

                if (onLongPress != null) {
                    overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint) = false
                        override fun longPressHelper(p: GeoPoint): Boolean {
                            onLongPress(p)
                            return true
                        }
                    }))
                }

                addOnFirstLayoutListener { _, _, _, _, _ ->
                    setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_UP) {
                            onMapMoved?.invoke(GeoPoint(mapCenter.latitude, mapCenter.longitude))
                            onUserPan?.invoke()
                        }
                        false
                    }
                }

                addMapListener(object : MapListener {
                    override fun onScroll(event: ScrollEvent?): Boolean {
                        onMapChanged?.invoke(
                            GeoPoint(mapCenter.latitude, mapCenter.longitude),
                            zoomLevelDouble,
                            visibleRadiusMeters()
                        )
                        return false
                    }
                    override fun onZoom(event: ZoomEvent?): Boolean {
                        onMapChanged?.invoke(
                            GeoPoint(mapCenter.latitude, mapCenter.longitude),
                            zoomLevelDouble,
                            visibleRadiusMeters()
                        )
                        return false
                    }
                })

                mapView = this
                MapZoomHandler.register(this)
                onMapReady(this)
            }
        },
        update = { map -> MapStyles.apply(map, mapStyle) }
    )

    mapView?.let { map ->
        overlayContent(map)
    }
}

fun MapView.animateToPoint(point: GeoPoint, zoom: Double? = null) {
    controller.animateTo(point)
    if (zoom != null) {
        controller.setZoom(zoom)
    }
}

fun MapView.fitBounds(points: List<GeoPoint>, padding: Int = 50) {
    if (points.isEmpty()) return
    val box = BoundingBox.fromGeoPoints(points)
    post {
        zoomToBoundingBox(box, true, padding)
    }
}
