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
    /**
     * Which of [stops] is the one whose arrivals board is open, by stop code.
     *
     * Renderers draw it as the selected stop; null means none of them is.
     */
    val activeStopCode: String? = null,
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
 * Where the camera is, and how much ground it actually covers.
 *
 * Centre and zoom alone cannot answer "what is on screen" — the same zoom covers a very
 * different area on a phone and on a tablet, and converting zoom to metres in shared code
 * means reimplementing each engine's projection and getting it subtly wrong. The platform
 * already knows its own viewport, so it reports the distance rather than the caller
 * guessing it from the zoom number.
 */
data class PtMapViewport(
    val center: LatLng,
    val zoom: Double,
    /**
     * Metres from [center] to the nearest edge of the screen — the inscribed circle, not
     * the circumscribed one, so a radius taken from it never claims ground the user
     * cannot see.
     */
    val visibleRadiusMeters: Double,
    /**
     * Metres from [center] to the screen's corner — the circumscribed circle.
     *
     * The pair is not redundant: on a portrait phone the inscribed circle is half the
     * screen's *width*, so most of what the user can see lies outside it. Asking "is this
     * bus off-screen?" with the inscribed radius answers yes for a bus plainly visible
     * near the top of the map. Size searches by [visibleRadiusMeters]; judge visibility
     * by this.
     */
    val visibleCornerMeters: Double
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
    onCameraChanged: ((PtMapViewport) -> Unit)? = null,
    onStopTap: ((StopResult) -> Unit)? = null,
    onVehicleTap: ((VehicleMarker) -> Unit)? = null
)
