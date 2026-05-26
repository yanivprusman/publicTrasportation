package com.automatelinux.pt.ui.map

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay

@SuppressLint("MissingPermission")
class GpsLocationOverlay(
    private val mapView: MapView,
    private val fusedClient: FusedLocationProviderClient
) : Overlay() {

    private var location: Location? = null
    private val drawPixel = Point()

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4285F4")
        style = Paint.Style.FILL
    }

    private val density = mapView.resources.displayMetrics.density
    private val dotRadius = 6f * density
    private val borderWidth = 2.5f * density

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            location = loc
            mapView.postInvalidate()
        }
    }

    fun startUpdates() {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
            .setMinUpdateIntervalMillis(1500L)
            .build()
        fusedClient.requestLocationUpdates(req, callback, null)
        fusedClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                location = loc
                mapView.postInvalidate()
            }
        }
    }

    fun stopUpdates() {
        fusedClient.removeLocationUpdates(callback)
    }

    override fun draw(canvas: Canvas, projection: Projection) {
        val loc = location ?: return
        projection.toPixels(GeoPoint(loc.latitude, loc.longitude), drawPixel)
        val x = drawPixel.x.toFloat()
        val y = drawPixel.y.toFloat()
        canvas.drawCircle(x, y, dotRadius + borderWidth, borderPaint)
        canvas.drawCircle(x, y, dotRadius, dotPaint)
    }
}
