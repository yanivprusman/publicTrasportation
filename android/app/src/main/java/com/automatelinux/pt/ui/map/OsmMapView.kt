package com.automatelinux.pt.ui.map

import android.view.MotionEvent
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
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay

@Composable
fun OsmMapView(
    modifier: Modifier = Modifier,
    center: GeoPoint = GeoPoint(31.77, 35.21),
    zoom: Double = 13.0,
    onMapReady: (MapView) -> Unit = {},
    onLongPress: ((GeoPoint) -> Unit)? = null,
    onMapMoved: ((GeoPoint) -> Unit)? = null,
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
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                controller.setZoom(zoom)
                controller.setCenter(center)
                minZoomLevel = 5.0
                maxZoomLevel = 19.0

                if (onLongPress != null) {
                    overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint) = false
                        override fun longPressHelper(p: GeoPoint): Boolean {
                            onLongPress(p)
                            return true
                        }
                    }))
                }

                if (onMapMoved != null) {
                    addOnFirstLayoutListener { _, _, _, _, _ ->
                        setOnTouchListener { _, event ->
                            if (event.action == MotionEvent.ACTION_UP) {
                                onMapMoved(GeoPoint(mapCenter.latitude, mapCenter.longitude))
                            }
                            false
                        }
                    }
                }

                mapView = this
                onMapReady(this)
            }
        },
        update = { view ->
            mapView = view
        }
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
