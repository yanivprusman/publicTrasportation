package com.automatelinux.pt.ui.map

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.android.gms.location.FusedLocationProviderClient
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@SuppressLint("MissingPermission")
class GpsLocationOverlay(
    mapView: MapView,
    fusedClient: FusedLocationProviderClient
) {
    val overlay: MyLocationNewOverlay

    init {
        val provider = FusedLocationOverlayProvider(fusedClient)
        overlay = MyLocationNewOverlay(provider, mapView).apply {
            val dot = createBlueDotBitmap(mapView.resources.displayMetrics.density)
            setPersonIcon(dot)
            setPersonHotspot(dot.width / 2f, dot.height / 2f)
            setDirectionIcon(dot)
            setDirectionAnchor(0.5f, 0.5f)
            isDrawAccuracyEnabled = false
        }
    }

    fun startUpdates() {
        overlay.enableMyLocation()
    }

    fun stopUpdates() {
        overlay.disableMyLocation()
    }

    companion object {
        fun createBlueDotBitmap(density: Float): Bitmap {
            val dotRadius = 10f * density
            val borderWidth = 3f * density
            val size = ((dotRadius + borderWidth) * 2).toInt() + 2
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val cx = size / 2f
            val cy = size / 2f
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#4285F4")
                style = Paint.Style.FILL
            }
            canvas.drawCircle(cx, cy, dotRadius + borderWidth, borderPaint)
            canvas.drawCircle(cx, cy, dotRadius, dotPaint)
            return bitmap
        }
    }
}
