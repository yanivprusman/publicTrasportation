package com.automatelinux.pt.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.automatelinux.pt.data.model.Itinerary
import com.automatelinux.pt.data.model.StopResult
import com.automatelinux.pt.data.model.VehicleMarker
import com.automatelinux.pt.map.LatLng

/**
 * Everything drawn on top of the tiles, as data.
 *
 * The osmdroid version of this was imperative: each overlay was a composable that took the
 * live `MapView` and mutated `map.overlays` itself. That is untranslatable across
 * platforms — the argument was the map engine — and it also made draw order depend on the
 * order the composables happened to run in, which is why the origin/destination markers
 * needed a `redrawKey` to force themselves back on top after the route redrew.
 *
 * Describing the contents instead lets each platform decide how to realise them, and lets
 * layering be a property of the renderer rather than an accident of call order.
 */
data class PtMapOverlays(
    /** Route polylines, intermediate stop dots and transfer markers for this itinerary. */
    val itinerary: Itinerary? = null,
    val origin: LatLng? = null,
    val destination: LatLng? = null,
    val via: LatLng? = null,
    val vehicles: List<VehicleMarker> = emptyList(),
    val vehiclesVisible: Boolean = true,
    val stops: List<StopResult> = emptyList(),
    /** Per-direction shape geometry for a browsed line, keyed by direction id. */
    val lineShape: Map<String, List<List<Double>>>? = null,
    /** Whether [lineShape] should fit the camera to itself; see LineShapeOverlay. */
    val lineShapeFitsCamera: Boolean = true,
    val trackedBus: VehicleMarker? = null,
    /**
     * Whether to show the device's own position. The platform owns the location source,
     * and the caller owns the permission check — the map does not prompt.
     */
    val showUserLocation: Boolean = false,
    /** Which marker to draw for the device's position; a user-facing setting. */
    val userLocationIcon: PtUserLocationIcon = PtUserLocationIcon.DOT
)

/**
 * The two location markers the app offers.
 *
 * DOT is the app's own blue dot; PLATFORM_DEFAULT is whatever the map engine ships with
 * (osmdroid's directional person icon). Persisted as `locationIconStyle`, so it has to
 * survive the port rather than be simplified away.
 */
enum class PtUserLocationIcon { DOT, PLATFORM_DEFAULT }

/**
 * The map.
 *
 * Android renders this with osmdroid — the existing, working implementation, wrapped rather
 * than rewritten (IOS_PORT_INVENTORY §6, Option A). iOS will render it with MapLibre. The
 * two share this signature and every piece of geometry behind it, so a change to what the
 * app draws is made once.
 */
@Composable
expect fun PtMap(
    state: PtMapState,
    overlays: PtMapOverlays,
    modifier: Modifier = Modifier,
    style: PtMapStyle = PtMapStyle.DARK,
    onLongPress: ((LatLng) -> Unit)? = null,
    onUserPan: (() -> Unit)? = null,
    onCameraChanged: ((LatLng, Double) -> Unit)? = null,
    onStopTap: ((StopResult) -> Unit)? = null,
    onVehicleTap: ((VehicleMarker) -> Unit)? = null
)
