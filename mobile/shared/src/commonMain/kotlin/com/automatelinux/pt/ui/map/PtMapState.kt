package com.automatelinux.pt.ui.map

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.automatelinux.pt.map.LatLng

/** Where the map is looking. */
data class PtMapCamera(
    val center: LatLng,
    val zoom: Double
)

/**
 * A camera move requested by shared code.
 *
 * Camera control has to be a *message* rather than a direct call, because the object that
 * can actually move the camera is the platform's map view, which common code cannot hold.
 */
sealed interface PtMapCommand {
    data class AnimateTo(val point: LatLng, val zoom: Double? = null) : PtMapCommand
    data class FitBounds(val points: List<LatLng>, val padding: Int = 50) : PtMapCommand
    data object ZoomIn : PtMapCommand
    data object ZoomOut : PtMapCommand
}

/**
 * The map's camera, hoisted into common code.
 *
 * Replaces the old arrangement where MainScreen held an osmdroid `MapView?` and called
 * `animateToPoint` / `fitBounds` on it directly. That worked, but it meant the screen
 * could only ever be Android: the type it was holding was the map engine itself.
 */
@Stable
class PtMapState(
    initialCenter: LatLng = DEFAULT_CENTER,
    initialZoom: Double = DEFAULT_ZOOM
) {
    /** Updated by the platform as the user pans and zooms. */
    var camera: PtMapCamera by mutableStateOf(PtMapCamera(initialCenter, initialZoom))
        internal set

    /**
     * The latest requested move, paired with a serial number.
     *
     * The serial is what makes a *repeated* identical command work: tapping "my location"
     * twice must recentre twice, and without it the second request would be an equal value
     * and recompose nothing.
     */
    var pendingCommand: Pair<Long, PtMapCommand>? by mutableStateOf(null)
        private set

    private var serial = 0L

    private fun issue(command: PtMapCommand) {
        serial += 1
        pendingCommand = serial to command
    }

    fun animateTo(point: LatLng, zoom: Double? = null) = issue(PtMapCommand.AnimateTo(point, zoom))
    fun fitBounds(points: List<LatLng>, padding: Int = 50) = issue(PtMapCommand.FitBounds(points, padding))
    fun zoomIn() = issue(PtMapCommand.ZoomIn)
    fun zoomOut() = issue(PtMapCommand.ZoomOut)

    /** Called by the platform once a command has been carried out. */
    fun consume(serialNumber: Long) {
        if (pendingCommand?.first == serialNumber) pendingCommand = null
    }

    companion object {
        val DEFAULT_CENTER = LatLng(31.77, 35.21)
        const val DEFAULT_ZOOM = 13.0
    }
}

/**
 * Tile styles the app offers.
 *
 * [stored] is the value persisted in settings, and matches the string constants this
 * replaced — so existing installs keep the style they had, with no migration.
 */
enum class PtMapStyle(val stored: String) {
    DARK("dark"),
    LIGHT("light"),
    SATELLITE("satellite");

    companion object {
        /** Unrecognised values fall back to DARK, the app's default. */
        fun fromStored(value: String?): PtMapStyle =
            entries.firstOrNull { it.stored == value } ?: DARK
    }
}
