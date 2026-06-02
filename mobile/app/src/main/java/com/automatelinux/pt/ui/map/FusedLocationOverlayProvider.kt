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

@SuppressLint("MissingPermission")
class FusedLocationOverlayProvider(
    private val fusedClient: FusedLocationProviderClient
) : IMyLocationProvider {

    private var consumer: IMyLocationConsumer? = null
    private var lastLoc: Location? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
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
