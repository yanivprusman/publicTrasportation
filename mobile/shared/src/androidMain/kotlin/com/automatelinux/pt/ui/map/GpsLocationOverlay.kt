package com.automatelinux.pt.ui.map

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.compose.ui.graphics.toArgb
import com.automatelinux.pt.ui.theme.CurrentLocationBlue
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
            val density = mapView.resources.displayMetrics.density
            val dot = createBlueDotBitmap(density)
            setPersonIcon(dot)
            setPersonHotspot(dot.width / 2f, dot.height / 2f)
            // The direction icon was the same plain dot, so a fix that knew the
            // rider's bearing drew a circle — on a walking leg, "which way do I
            // turn" is the question the map is being asked, and it was the one
            // thing the map declined to answer. osmdroid rotates this bitmap by
            // the bearing about the anchor, so the cone is drawn pointing up and
            // the anchor stays the dot's own centre.
            setDirectionIcon(createHeadingBitmap(density))
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
            val dotRadius = 7f * density
            val borderWidth = 2.5f * density
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
                color = CurrentLocationBlue.toArgb()
                style = Paint.Style.FILL
            }
            canvas.drawCircle(cx, cy, dotRadius + borderWidth, borderPaint)
            canvas.drawCircle(cx, cy, dotRadius, dotPaint)
            return bitmap
        }

        /**
         * The same dot with a heading cone above it, pointing at 0° so osmdroid's
         * rotation lands it on the true bearing.
         *
         * The dot sits at the exact centre of a square bitmap: the anchor is the
         * centre, and anything drawn off-centre would swing around the rider
         * instead of turning under them.
         */
        fun createHeadingBitmap(density: Float): Bitmap {
            val dotRadius = 7f * density
            val borderWidth = 2.5f * density
            val coneLength = 26f * density
            val coneHalfWidth = 13f * density
            val size = (coneLength * 2).toInt()
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val cx = size / 2f
            val cy = size / 2f

            val conePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    cx, cy - coneLength, cx, cy,
                    Color.TRANSPARENT, CurrentLocationBlue.copy(alpha = 0.5f).toArgb(),
                    Shader.TileMode.CLAMP
                )
            }
            val cone = Path().apply {
                moveTo(cx, cy)
                lineTo(cx - coneHalfWidth, cy - coneLength)
                lineTo(cx + coneHalfWidth, cy - coneLength)
                close()
            }
            canvas.drawPath(cone, conePaint)

            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = CurrentLocationBlue.toArgb()
                style = Paint.Style.FILL
            }
            canvas.drawCircle(cx, cy, dotRadius + borderWidth, borderPaint)
            canvas.drawCircle(cx, cy, dotRadius, dotPaint)
            return bitmap
        }
    }
}
