package com.automatelinux.pt.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.automatelinux.pt.data.model.StopResult
import com.automatelinux.pt.data.model.VehicleMarker
import com.automatelinux.pt.map.LatLng

/**
 * iOS map — NOT YET IMPLEMENTED. Deliberately a stub, not a guess.
 *
 * The real implementation is MapLibre iOS behind Compose Multiplatform's `UIKitView`
 * interop, drawing the same [PtMapOverlays] the Android side draws. That cannot be written
 * honestly from here: it needs cinterop bindings generated against MapLibre's iOS SDK, and
 * generating or checking those requires a Mac. Code written blind against an SDK whose
 * symbols cannot be inspected would compile in nobody's head but mine.
 *
 * Stubbing it instead buys something real: **everything else in the app can run in the iOS
 * Simulator on day one.** Route planning, arrivals, the lines browser, settings and the
 * whole network layer are already shared and do not need a map to work. That turns the
 * MapLibre work into one isolated task against a fixed contract, rather than the thing
 * standing between here and any running iOS build at all.
 *
 * What implementing it involves, in order:
 *  1. Add MapLibre to the Xcode project (SPM), and a cinterop def so Kotlin sees `MLNMapView`.
 *  2. Replace this body with `UIKitView(factory = { MLNMapView(...) }, update = { ... })`.
 *  3. Map each [PtMapOverlays] field to MapLibre annotations / line+symbol layers — the
 *     seven overlay kinds the Android side renders in `MapOverlays.kt`.
 *  4. Point it at the same MAPNIK raster tiles Android uses, so the two look alike.
 *  5. Honour [PtMapState.pendingCommand] and call [PtMapState.consume] — see the Android
 *     actual for the serial-acknowledgement pattern.
 *  6. Report real [PtMapViewport.visibleRadiusMeters] and [PtMapViewport.visibleCornerMeters]
 *     from `MLNMapView.visibleCoordinateBounds` — the live-buses layer sizes its search from
 *     the first and judges whether a bus is off-screen with the second, so returning made-up
 *     numbers would make the feature query the wrong ground rather than fail visibly.
 */
@Composable
actual fun PtMap(
    state: PtMapState,
    overlays: PtMapOverlays,
    modifier: Modifier,
    style: PtMapStyle,
    onLongPress: ((LatLng) -> Unit)?,
    onUserPan: (() -> Unit)?,
    onCameraChanged: ((PtMapViewport) -> Unit)?,
    onStopTap: ((StopResult) -> Unit)?,
    onVehicleTap: ((VehicleMarker) -> Unit)?
) {
    // Acknowledge camera commands even though nothing moves. Without this, every
    // animateTo/fitBounds would sit in pendingCommand forever, and the first real
    // implementation would inherit a queue of stale requests from the whole session.
    val pending = state.pendingCommand
    LaunchedEffect(pending) {
        pending?.let { (serial, _) -> state.consume(serial) }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Map: iOS implementation pending",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
