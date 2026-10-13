package com.automatelinux.pt.journey

import com.automatelinux.pt.data.model.Itinerary
import com.automatelinux.pt.data.model.RouteLeg
import com.automatelinux.pt.data.model.VehicleMarker
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * The live half of a journey: where the actual bus is, against the timetable the
 * rest of the engine runs on.
 *
 * Every competitor leads with this — "your line arrives in 16 min", counted from
 * the SIRI feed, shown while you walk to the stop and while you wait at it. The
 * selection logic is pure so it can be tested without a feed; the polling loop
 * that calls it lives in [JourneySession].
 */

/** What the live feed knows about the ride being waited for. */
data class JourneyLiveInfo(
    /** The ride leg this describes — an index into the itinerary's legs. */
    val legIndex: Int,
    /** When the next bus of this line reaches the boarding stop, epoch ms. */
    val arrivalMs: Long,
    /** The bus after it, for "then in 41 min" — absent when only one is reported. */
    val nextArrivalMs: Long? = null,
    /** Where that next bus is right now, for showing it on the map. */
    val vehicle: VehicleMarker? = null,
    /** When this was fetched; the panel hides a report that has gone stale. */
    val fetchedAtMs: Long,
    /**
     * When the rider's OWN trip — the one the itinerary was planned on — reaches the
     * boarding stop, epoch ms. Null when the feed is not reporting that trip.
     *
     * Not the same as [arrivalMs]: the first bus of the line may be the one before
     * ours running late, and calling that "your bus is early" would send the rider
     * running for a bus that was never theirs. Only this one may say the bus is early.
     */
    val ownTripArrivalMs: Long? = null
)

object JourneyLive {

    /** A report older than this says nothing about where the bus is now. */
    const val STALE_MS = 75_000L

    /**
     * The ride leg whose boarding stop is worth watching, or null when none is.
     *
     * The bus the rider can still miss is the one being walked to or waited for.
     * Once on board — or once the trip is over — the boarding stop's feed is
     * yesterday's question, and polling it would spend battery on nothing.
     */
    fun watchLegIndex(itinerary: Itinerary, progress: JourneyProgress?): Int? {
        val p = progress ?: return itinerary.legs.indexOfFirst { it.isRide }.takeIf { it >= 0 }
        return when (p.phase) {
            JourneyPhase.WAITING -> p.legIndex
            JourneyPhase.WALKING -> (p.legIndex + 1).takeIf {
                itinerary.legs.getOrNull(it)?.isRide == true
            }
            else -> null
        }
    }

    /**
     * This line's next arrivals at the boarding stop, from one SIRI snapshot.
     *
     * Matched by route id when the leg carries one — SIRI's LineRef is the GTFS
     * route_id, and it is what separates the 64 going our way from the 64 going
     * back. A leg without a route id matches by published name, which is all the
     * feed offers then. Sorted by arrival, with buses already past dropped.
     */
    fun arrivalsFor(
        leg: RouteLeg,
        markers: List<VehicleMarker>,
        nowMs: Long
    ): List<Pair<Long, VehicleMarker>> = markers
        .filter { marker -> serves(leg, marker) }
        .mapNotNull { marker ->
            parseMsOrNull(marker.expectedArrival)?.let { it to marker }
        }
        .filter { (arrival, _) -> arrival >= nowMs - PAST_GRACE_MS }
        .sortedBy { (arrival, _) -> arrival }

    /** Builds the info the panel renders, or null when the feed has no such bus. */
    fun infoFrom(
        legIndex: Int,
        leg: RouteLeg,
        markers: List<VehicleMarker>,
        nowMs: Long
    ): JourneyLiveInfo? {
        val arrivals = arrivalsFor(leg, markers, nowMs)
        val first = arrivals.firstOrNull() ?: return null
        return JourneyLiveInfo(
            legIndex = legIndex,
            arrivalMs = first.first,
            nextArrivalMs = arrivals.getOrNull(1)?.first,
            vehicle = first.second,
            fetchedAtMs = nowMs,
            ownTripArrivalMs = arrivals.firstOrNull { (_, marker) -> isOwnTrip(leg, marker) }?.first
        )
    }

    /**
     * Whether [marker] is the very trip [leg] was planned on.
     *
     * The feed's DatedVehicleJourneyRef is not the GTFS trip_id (2026-10-13: none of
     * a sample of them appear in trips.txt), so the trip is named the way both sides
     * can: the route, plus the wall-clock time it was timetabled to leave its first
     * stop. MOTIS writes exactly that into its trip id
     * ("20261013_06:00_israel_22818921_131026"); SIRI reports it as
     * OriginAimedDepartureTime ("2026-10-13T06:00:00+03:00"). Both are local time.
     */
    fun isOwnTrip(leg: RouteLeg, marker: VehicleMarker): Boolean {
        if (leg.routeId == null || marker.lineRef != leg.routeId) return false
        val planned = leg.tripId?.let { tripOrigin(it) } ?: return false
        val aimed = marker.originAimedDeparture ?: return false
        // "2026-10-13T06:00:00+03:00" -> "2026-10-13T06:00"
        return aimed.length >= 16 && aimed.substring(0, 16) == planned
    }

    /**
     * The local "yyyy-MM-ddTHH:mm" a MOTIS trip id says its trip leaves its first
     * stop, or null for an id not in that shape. A GTFS time past midnight (25:10 on
     * the service day) is rolled onto the next calendar day, which is how SIRI
     * prints it.
     */
    fun tripOrigin(tripId: String): String? {
        val m = TRIP_ID_ORIGIN.find(tripId) ?: return null
        val (y, mo, d, hh, mm) = m.destructured
        val hour = hh.toInt()
        val date = runCatching { LocalDate(y.toInt(), mo.toInt(), d.toInt()) }.getOrNull()
            ?: return null
        val day = if (hour >= 24) date.plus(DatePeriod(days = 1)) else date
        return "${day}T${(hour % 24).toString().padStart(2, '0')}:$mm"
    }

    private val TRIP_ID_ORIGIN = Regex("""^(\d{4})(\d{2})(\d{2})_(\d{2}):(\d{2})_""")

    /**
     * Whether [marker] is a bus of [leg]'s line: by route id when both carry one (it
     * tells the two directions of a line apart), else by published name.
     */
    fun serves(leg: RouteLeg, marker: VehicleMarker): Boolean =
        if (leg.routeId != null && marker.lineRef != null) {
            marker.lineRef == leg.routeId
        } else {
            // A leg with no name (a walk) serves no bus — not the ones with a blank name.
            leg.routeShortName?.let { marker.lineNumber.equals(it, ignoreCase = true) } ?: false
        }

    fun JourneyLiveInfo.isStale(nowMs: Long): Boolean = nowMs - fetchedAtMs > STALE_MS

    /** A bus reported this recently past its arrival is treated as "due now". */
    private const val PAST_GRACE_MS = 60_000L

    private fun parseMsOrNull(iso: String): Long? =
        runCatching { Instant.parse(iso).toEpochMilliseconds() }.getOrNull()
}
