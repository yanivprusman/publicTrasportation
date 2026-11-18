package com.automatelinux.pt.ui.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Point
import android.graphics.Typeface
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Writes street names along the walking line, on top of it.
 *
 * Why it exists: the base map is raster tiles with the street names painted into the
 * image, and the route line is drawn over the tiles, so it covers the name of the very
 * street the rider is walking on (2026-11-18, Sheinkin / Nahalat Binyamin, Tel Aviv).
 * A raster label cannot be lifted above an overlay, so the names come from the router's
 * own steps ([com.automatelinux.pt.data.model.StreetStretch]) and are drawn here.
 *
 * A name is drawn only where the stretch is long enough on screen to hold it, and
 * repeated along long stretches so one is visible however the map is panned.
 * Text always runs left-to-right on screen, never upside down.
 */
class StreetNameOverlay(
    private val stretches: List<Pair<String, List<GeoPoint>>>,
    density: Float
) : Overlay() {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12f * density
        typeface = Typeface.DEFAULT_BOLD
    }
    private val halo = Paint(fill).apply {
        color = Color.parseColor("#E6101828")
        style = Paint.Style.STROKE
        strokeWidth = 3.5f * density
        strokeJoin = Paint.Join.ROUND
    }
    private val margin = 16f * density
    private val repeatGap = 220f * density

    private val point = Point()
    private val path = Path()
    private val measure = PathMeasure()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || !isEnabled) return
        val projection = mapView.projection
        // Centre the glyphs on the line: baseline below the path by half the x-height.
        val vOffset = -(fill.ascent() + fill.descent()) / 2f

        for ((name, points) in stretches) {
            if (points.size < 2) continue
            val textWidth = fill.measureText(name)

            val screen = points.map { gp ->
                projection.toPixels(gp, point)
                point.x.toFloat() to point.y.toFloat()
            }
            // Text follows the path's direction; run it left-to-right so it is never upside down.
            val ordered = if (screen.first().first > screen.last().first) screen.asReversed() else screen

            path.reset()
            path.moveTo(ordered[0].first, ordered[0].second)
            for (i in 1 until ordered.size) path.lineTo(ordered[i].first, ordered[i].second)
            measure.setPath(path, false)
            val length = measure.length
            if (length < textWidth + 2 * margin) continue

            // One label per [repeatGap] of line, evenly spread, the first centred when only one fits.
            val slot = textWidth + repeatGap
            val count = ((length - 2 * margin + repeatGap) / slot).toInt().coerceAtLeast(1)
            val used = count * textWidth + (count - 1) * repeatGap
            var offset = (length - used) / 2f
            repeat(count) {
                canvas.drawTextOnPath(name, path, offset, vOffset, halo)
                canvas.drawTextOnPath(name, path, offset, vOffset, fill)
                offset += slot
            }
        }
    }
}
