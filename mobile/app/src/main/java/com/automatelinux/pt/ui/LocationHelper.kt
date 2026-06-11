package com.automatelinux.pt.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

object LocationHelper {
    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun fetchLocation(
        fusedLocationClient: FusedLocationProviderClient,
        onStart: () -> Unit = {},
        onLocation: (Location) -> Unit,
        onFailure: () -> Unit = {}
    ) {
        onStart()
        fusedLocationClient.lastLocation
            .addOnSuccessListener { cached ->
                if (cached != null) {
                    onLocation(cached)
                } else {
                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        CancellationTokenSource().token
                    ).addOnSuccessListener { fresh ->
                        if (fresh != null) onLocation(fresh)
                        else onFailure()
                    }.addOnFailureListener { onFailure() }
                }
            }
            .addOnFailureListener { onFailure() }
    }

    // Continuous updates for follow-my-location mode. Returns the callback so the
    // caller can stop following via stopFollowing().
    @SuppressLint("MissingPermission")
    fun startFollowing(
        fusedLocationClient: FusedLocationProviderClient,
        intervalMs: Long = 3_000,
        onLocation: (Location) -> Unit
    ): LocationCallback {
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let(onLocation)
            }
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateDistanceMeters(2f)
            .build()
        fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        return callback
    }

    fun stopFollowing(
        fusedLocationClient: FusedLocationProviderClient,
        callback: LocationCallback
    ) {
        fusedLocationClient.removeLocationUpdates(callback)
    }

    @SuppressLint("MissingPermission")
    fun centerOnLocation(
        fusedLocationClient: FusedLocationProviderClient,
        onLocation: (Location) -> Unit
    ) {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { cached ->
                if (cached != null) {
                    onLocation(cached)
                } else {
                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        CancellationTokenSource().token
                    ).addOnSuccessListener { fresh ->
                        if (fresh != null) onLocation(fresh)
                    }
                }
            }
    }
}
