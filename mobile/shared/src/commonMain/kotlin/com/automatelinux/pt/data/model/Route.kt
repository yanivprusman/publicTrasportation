package com.automatelinux.pt.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class Place(
    val name: String,
    val lat: Double,
    val lon: Double
)

// Preserves the lenient mapping the old Gson TypeAdapter did (METRO->SUBWAY, *RAIL*->RAIL,
// unknown->WALK), and serializes back to the enum name.
object TransitModeSerializer : KSerializer<TransitMode> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("TransitMode", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: TransitMode) = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): TransitMode = TransitMode.fromString(decoder.decodeString())
}

@Serializable(with = TransitModeSerializer::class)
enum class TransitMode {
    WALK, BUS, RAIL, TRAM, SUBWAY, FERRY, BIKE, CAR;

    companion object {
        fun fromString(s: String): TransitMode = when {
            s.equals("WALK", ignoreCase = true) -> WALK
            s.equals("BUS", ignoreCase = true) -> BUS
            s.equals("TRAM", ignoreCase = true) -> TRAM
            s.equals("SUBWAY", ignoreCase = true) -> SUBWAY
            s.equals("METRO", ignoreCase = true) -> SUBWAY
            s.equals("FERRY", ignoreCase = true) -> FERRY
            s.equals("BIKE", ignoreCase = true) -> BIKE
            s.equals("CAR", ignoreCase = true) -> CAR
            s.contains("RAIL", ignoreCase = true) -> RAIL
            else -> WALK
        }
    }
}

@Serializable
data class RouteLeg(
    val mode: TransitMode,
    val from: Place,
    val to: Place,
    val startTime: String,
    val endTime: String,
    val duration: Long,
    val routeShortName: String? = null,
    val routeColor: String? = null,
    val agencyName: String? = null,
    val polyline: String = "",
    val intermediateStops: List<Place>? = null,
    /**
     * Wheelchair access for the scheduled service, from GTFS trips.txt via the
     * backend: "accessible", "not_accessible" or "unknown". SIRI reports nothing
     * about access, so this describes the timetabled trip, not the vehicle that
     * actually arrives. Absent on walk legs.
     */
    val wheelchairAccess: String? = null,
    /** MOTIS trip id; the key /api/trip-shape needs to draw this leg's real line. */
    val tripId: String? = null,
    /**
     * GTFS route_id — and SIRI's LineRef is the same number.
     *
     * The published name is not enough to match a live sighting: "64" leaves this
     * stop in both directions under two different route ids, so a name match would
     * cheerfully attach the bus going the other way.
     */
    val routeId: String? = null,
    /**
     * What the service is signed for. A line number alone does not say which of its
     * two directions this is, and on a two-direction line that is the difference
     * between the right bus and an hour lost.
     */
    val headsign: String? = null,
    /** Street length in metres; MOTIS sets it on walk/bike legs only. */
    val distanceMeters: Int? = null,
    /**
     * True when the times came from a realtime feed rather than the timetable.
     * Every competitor marks this — in colour, or in words — and until we did, our
     * countdown implied a confidence the timetable cannot give.
     */
    val realTime: Boolean = false,
    /** The timetabled time, when realtime has moved [startTime] away from it. */
    val scheduledStartTime: String? = null,
    /** Stop code as printed on the pole; the number other apps and SIRI use. */
    val fromStopCode: String? = null,
    val toStopCode: String? = null,
    /** Single-ride price for this leg from the operators' fare table, in ILS. */
    val fare: Double? = null
)

@Serializable
data class TripShapeResponse(
    val shapeId: String = "",
    /** [lat, lon] pairs in shape_pt_sequence order. */
    val points: List<List<Double>> = emptyList()
)

enum class WheelchairAccess { ACCESSIBLE, NOT_ACCESSIBLE, UNKNOWN }

/** Typed view of [RouteLeg.wheelchairAccess]; anything unrecognised is UNKNOWN. */
val RouteLeg.access: WheelchairAccess
    get() = when (wheelchairAccess) {
        "accessible" -> WheelchairAccess.ACCESSIBLE
        "not_accessible" -> WheelchairAccess.NOT_ACCESSIBLE
        else -> WheelchairAccess.UNKNOWN
    }

@Serializable
data class Itinerary(
    val duration: Long,
    val startTime: String,
    val endTime: String,
    val transfers: Int,
    val legs: List<RouteLeg>,
    /**
     * The journey's price from the operators' own fare table, or null when any ride
     * on it has no rule in that table.
     *
     * This replaced a flat ₪5.50-per-bus estimate that priced Midreshet Ben-Gurion →
     * Be'er Sheva at ~₪11 where both Moovit and Bus Nearby say ₪19. Null is shown as
     * no price at all: a wrong number is worse than an absent one, and the server
     * only omits this when it genuinely cannot price a leg.
     */
    val fareTotal: Double? = null
) {
    val walkDuration: Long
        get() = legs.filter { it.mode == TransitMode.WALK }.sumOf { it.duration }

    /** The first ride of the journey — the one you can miss. */
    val firstRide: RouteLeg?
        get() = legs.firstOrNull { it.mode != TransitMode.WALK }
}

enum class RouteSortMode {
    FASTEST, FEWER_TRANSFERS, LESS_WALKING
}

// One direct street route (the fastest per mode) returned alongside the transit
// itineraries for the bike/car comparison strip. `distance` is street meters.
@Serializable
data class DirectAlternative(
    val mode: TransitMode,
    val distance: Int,
    val itinerary: Itinerary
)

@Serializable
data class RouteResult(
    val itineraries: List<Itinerary>,
    val alternatives: List<DirectAlternative> = emptyList()
)
