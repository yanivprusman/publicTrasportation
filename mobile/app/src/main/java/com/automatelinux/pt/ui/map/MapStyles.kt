package com.automatelinux.pt.ui.map

import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay

object MapStyles {
    const val DARK = "dark"
    const val LIGHT = "light"
    const val SATELLITE = "satellite"

    // Esri serves world imagery tiles as z/y/x, unlike osmdroid's default z/x/y.
    private val esriWorldImagery = object : OnlineTileSourceBase(
        "EsriWorldImagery", 0, 19, 256, "",
        arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
        "Esri, Maxar, Earthstar Geographics"
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String =
            baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" +
                MapTileIndex.getY(pMapTileIndex) + "/" +
                MapTileIndex.getX(pMapTileIndex)
    }

    // Same recipe as the web app's dark map (globals.css):
    // invert(1) hue-rotate(180deg) brightness(0.91) contrast(0.9) saturate(0.7).
    // invert+hue-rotate(180) collapses to a single luminance-flip matrix that keeps hues.
    private val darkTileFilter = ColorMatrixColorFilter(
        ColorMatrix(floatArrayOf(
            1f / 3, -2f / 3, -2f / 3, 0f, 255f,
            -2f / 3, 1f / 3, -2f / 3, 0f, 255f,
            -2f / 3, -2f / 3, 1f / 3, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        )).apply {
            val b = 0.91f
            postConcat(ColorMatrix(floatArrayOf(
                b, 0f, 0f, 0f, 0f,
                0f, b, 0f, 0f, 0f,
                0f, 0f, b, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )))
            val c = 0.9f
            val t = 255f * (1f - c) / 2f
            postConcat(ColorMatrix(floatArrayOf(
                c, 0f, 0f, 0f, t,
                0f, c, 0f, 0f, t,
                0f, 0f, c, 0f, t,
                0f, 0f, 0f, 1f, 0f
            )))
            postConcat(ColorMatrix().apply { setSaturation(0.7f) })
        }
    )

    /** Restyle the map's base tiles. Idempotent per style so it can run on every recomposition. */
    fun apply(map: MapView, style: String) {
        if (map.tag == style) return
        map.tag = style

        val tiles = map.overlayManager.tilesOverlay
        when (style) {
            SATELLITE -> {
                map.setTileSource(esriWorldImagery)
                tiles.setColorFilter(null)
                tiles.loadingBackgroundColor = Color.BLACK
                tiles.loadingLineColor = Color.parseColor("#1F1F1F")
            }
            LIGHT -> {
                map.setTileSource(TileSourceFactory.MAPNIK)
                tiles.setColorFilter(null)
                tiles.loadingBackgroundColor = Color.parseColor("#E6E6E6")
                tiles.loadingLineColor = Color.parseColor("#D4D4D4")
            }
            else -> {
                map.setTileSource(TileSourceFactory.MAPNIK)
                tiles.setColorFilter(darkTileFilter)
                tiles.loadingBackgroundColor = Color.parseColor("#10141B")
                tiles.loadingLineColor = Color.parseColor("#1B2230")
            }
        }

        val copyrightColor =
            if (style == LIGHT) Color.parseColor("#99333333")
            else Color.parseColor("#99FFFFFF")
        map.overlays.filterIsInstance<CopyrightOverlay>().forEach { it.setTextColor(copyrightColor) }

        map.invalidate()
    }
}
