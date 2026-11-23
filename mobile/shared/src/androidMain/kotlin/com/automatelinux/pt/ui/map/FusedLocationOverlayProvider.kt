package com.automatelinux.pt.ui.map

import android.annotation.SuppressLint
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import org.osmdroid.views.overlay.mylocation.IMyLocationConsumer
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider
import android.os.SystemClock
import com.automatelinux.pt.journey.MAX_FIX_ACCURACY_METERS

/** How long a good fix keeps rough ones off the dot. */
private const val ROUGH_HOLD_MS = 60_000L

@SuppressLint("MissingPermission")
class FusedLocationOverlayProvider(
    private val fusedClient: FusedLocationProviderClient
) : IMyLocationProvider {

    private var consumer: IMyLocationConsumer? = null
    private var lastLoc: Location? = null

    /** When the last fix within [MAX_FIX_ACCURACY_METERS] arrived, elapsed-realtime ms. */
    private var lastAccurateAtMs: Long? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            val rough = loc.hasAccuracy() && loc.accuracy > MAX_FIX_ACCURACY_METERS
            val now = SystemClock.elapsedRealtime()
            // A rough fix (cell/Wi-Fi, hundreds of metres off) must not drag the dot
            // away from a good one: inside a bus GPS drops in and out, and the dot
            // jumped between the two (owner, 2026-11-23). With no good fix for a while
            // the rough one is all there is, and showing it beats showing nothing.
            if (rough) {
                val good = lastAccurateAtMs
                if (good != null && now - good <= ROUGH_HOLD_MS) return
            } else {
                lastAccurateAtMs = now
            }
            lastLoc = loc
            consumer?.onLocationChanged(loc, this@FusedLocationOverlayProvider)
        }
    }

    override fun startLocationProvider(c: IMyLocationConsumer?): Boolean {
        consumer = c
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
            .setMinUpdateIntervalMillis(1500L)
            .build()
        fusedClient.requestLocationUpdates(req, callback, null)
        fusedClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                lastLoc = loc
                consumer?.onLocationChanged(loc, this)
            }
        }
        return true
    }

    override fun stopLocationProvider() {
        fusedClient.removeLocationUpdates(callback)
        consumer = null
    }

    override fun getLastKnownLocation(): Location? = lastLoc

    override fun destroy() {
        stopLocationProvider()
    }
}
