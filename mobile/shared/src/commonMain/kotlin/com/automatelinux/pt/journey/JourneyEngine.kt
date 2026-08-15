package com.automatelinux.pt.journey

import com.automatelinux.pt.data.model.Itinerary
import com.automatelinux.pt.data.model.Place
import com.automatelinux.pt.data.model.RouteLeg
import com.automatelinux.pt.data.model.TransitMode
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.datetime.Instant

/**
 * Where a journey actually is, decided from the rider's position and the clock.
 *
 * All of it is pure: no Android, no location client, no notifications. The service
 * feeds it fixes and acts on what comes back, which is what makes the interesting
 * part — "am I one stop away yet" — testable without a bus.
 */

/** One position report. [atMs] is when it was taken, not when it was handed over. */
data class GeoFix(
    val lat: Double,
    val lon: Double,
    val accuracyMeters: Double? = null,
    val atMs: Long
)

enum class JourneyPhase {
    /** On foot, heading for the next stop or the destination. */
    WALKING,
    /** Standing at the boarding stop, waiting for a ride that has not left yet. */
    WAITING,
    /** On board. */
    RIDING,
    /** Nothing left to do. */
    ARRIVED
}

/**
 * Something the rider has to be TOLD, not merely shown — each fires once per journey
 * leg, and the service turns it into a notification with sound and vibration.
 */
enum class JourneyAlert {
    /** The next stop is yours: stand up, press the button. */
    PREPARE_TO_ALIGHT,
    /** The ride is here / it is time to get on. */
    BOARD_NOW,
    /** Destination reached. */
    ARRIVED
}

/**
 * How far along the journey is. Monotonic by construction — a journey never walks
 * itself backwards because a GPS fix wobbled, so the cursor only ever moves forward.
 */
data class JourneyCursor(
    val legIndex: Int = 0,
    /** Index into the current ride leg's stop list; 0 = the boarding stop. */
    val stopIndex: Int = 0,
    /** Legs whose alight warning has already been given. */
    val alightAlerted: Set<Int> = emptySet(),
    /** Legs whose boarding call has already been given. */
    val boardAlerted: Set<Int> = emptySet(),
    val arrived: Boolean = false
)

data class JourneyProgress(
    val phase: JourneyPhase,
    val legIndex: Int,
    val totalLegs: Int,
    /** The leg being travelled, or null once the journey is over. */
    val leg: RouteLeg?,
    /** The leg after it — what the rider is about to have to do. */
    val nextLeg: RouteLeg?,
    /** Stops still to pass before the rider's own stop, the alight stop included. */
    val stopsRemaining: Int?,
    /** The next stop the vehicle calls at, by name. */
    val nextStopName: String?,
    /** Where this leg ends: the alight stop, the boarding stop, or the destination. */
    val targetName: String?,
    /** Straight-line metres to the end of this leg — null when no position is known. */
    val metersToTarget: Double?,
    /** Seconds until this leg is scheduled to end; negative when it is running late. */
    val secondsToTarget: Long?,
    /** Seconds until the ride is scheduled to leave, while waiting for it. */
    val secondsToDeparture: Long?,
    /** True from the moment the rider should be getting ready to step off. */
    val alightImminent: Boolean,
    /** False when no usable fix has arrived: the UI must say so rather than guess. */
    val positionKnown: Boolean
)

/**
 * How far through the leg being travelled the rider is, 0..1.
 *
 * Measured, in the order the measurements deserve to be trusted: metres left of a
 * walk, then stops left of a ride, and only then the clock — which is the one that
 * lies when a bus runs late. A leg still being waited for is at zero however close
 * its departure is: nothing has been travelled yet.
 *
 * Returns 0 rather than guessing when nothing measures it, so a bar drawn from this
 * never claims ground the rider has not covered.
 */
fun JourneyProgress.legFraction(): Float {
    if (phase == JourneyPhase.ARRIVED) return 1f
    if (phase == JourneyPhase.WAITING) return 0f
    val leg = leg ?: return 0f

    // Measured against the leg's own straight line, not the length of the path walked:
    // metersToTarget is a straight line too, and dividing one by the other would start
    // the bar a third of the way along a leg with any bend in it.
    val byMeters = metersToTarget?.let { left ->
        haversineMeters(leg.from.lat, leg.from.lon, leg.to.lat, leg.to.lon)
            .takeIf { it > 1.0 }
            ?.let { span -> 1f - (left / span).toFloat() }
    }
    val byStops = stopsRemaining?.let { left ->
        leg.rideStops().lastIndex.takeIf { it > 0 }?.let { total -> 1f - left.toFloat() / total }
    }
    val byClock = secondsToTarget?.let { left ->
        leg.duration.takeIf { it > 0 }?.let { total -> 1f - left.toFloat() / total }
    }

    return (byMeters ?: byStops ?: byClock ?: 0f).coerceIn(0f, 1f)
}

data class JourneyUpdate(
    val cursor: JourneyCursor,
    val progress: JourneyProgress,
    val alerts: List<JourneyAlert>
)

/** Close enough to a stop to count as having called there. */
const val STOP_HIT_METERS = 120.0

/** Close enough to the end of a leg to call the leg done. */
const val LEG_END_METERS = 70.0

/** Warn this far out even when the stop count is unknown (no intermediate stops). */
const val ALIGHT_WARN_METERS = 500.0

/** A fix older than this says nothing about where the rider is now. */
const val FIX_STALE_MS = 90_000L

/** Without GPS, a leg is called done this long after it was timetabled to end. */
private const val SCHEDULE_GRACE_MS = 30_000L

/** Board call this long before a ride is due to leave. */
private const val BOARD_CALL_LEAD_MS = 120_000L

fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = (lat2 - lat1).toRadians()
    val dLon = (lon2 - lon1).toRadians()
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(lat1.toRadians()) * cos(lat2.toRadians()) * sin(dLon / 2) * sin(dLon / 2)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}

private fun Double.toRadians(): Double = this * 0.017453292519943295

val RouteLeg.isRide: Boolean
    get() = mode != TransitMode.WALK && mode != TransitMode.BIKE

/**
 * Every stop this leg calls at, boarding and alighting included, in travel order.
 * The feed omits the intermediate list on some services — the ends are always there,
 * which is why a distance warning backs up the stop count rather than replacing it.
 */
fun RouteLeg.rideStops(): List<Place> = buildList {
    add(from)
    intermediateStops?.let { addAll(it) }
    add(to)
}

private fun parseMs(iso: String?): Long? =
    iso?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }

/**
 * Move [cursor] forward on the evidence of one [fix] and the clock, and describe where
 * that leaves the rider.
 *
 * A null (or stale) fix is not an error and not a guess: the cursor then advances on
 * the timetable alone and the result says `positionKnown = false`, so the panel can
 * admit it is following the schedule rather than the rider.
 */
fun stepJourney(
    itinerary: Itinerary,
    cursor: JourneyCursor,
    fix: GeoFix?,
    nowMs: Long
): JourneyUpdate {
    val legs = itinerary.legs
    val usable = fix?.takeIf { nowMs - it.atMs <= FIX_STALE_MS }
    val alerts = mutableListOf<JourneyAlert>()
    var c = cursor

    if (legs.isEmpty()) {
        return JourneyUpdate(
            cursor = c.copy(arrived = true),
            progress = arrivedProgress(legs.size, usable != null),
            alerts = if (!cursor.arrived) listOf(JourneyAlert.ARRIVED) else emptyList()
        )
    }

    // Walk the cursor forward as far as the evidence carries it. A single fix can
    // close more than one leg — a rider who looked at the phone again after twenty
    // minutes underground should land on the leg they are actually on.
    while (!c.arrived && c.legIndex <= legs.lastIndex) {
        val leg = legs[c.legIndex]
        if (leg.isRide) c = c.copy(stopIndex = advanceStops(leg, c.stopIndex, usable))
        if (!legComplete(leg, c, usable, nowMs)) break
        c = if (c.legIndex == legs.lastIndex) {
            c.copy(legIndex = legs.size, stopIndex = 0, arrived = true)
        } else {
            c.copy(legIndex = c.legIndex + 1, stopIndex = 0)
        }
    }

    if (c.arrived || c.legIndex > legs.lastIndex) {
        if (!cursor.arrived) alerts += JourneyAlert.ARRIVED
        return JourneyUpdate(c.copy(arrived = true), arrivedProgress(legs.size, usable != null), alerts)
    }

    val leg = legs[c.legIndex]
    val nextLeg = legs.getOrNull(c.legIndex + 1)
    val stops = if (leg.isRide) leg.rideStops() else emptyList()
    val stopsRemaining = if (leg.isRide) max(0, stops.lastIndex - c.stopIndex) else null
    val metersToTarget = usable?.let { haversineMeters(it.lat, it.lon, leg.to.lat, leg.to.lon) }
    val legEndMs = parseMs(leg.endTime)
    val legStartMs = parseMs(leg.startTime)

    // Waiting is its own state: standing at a pole with the bus not yet due is not
    // "walking", and the only number that matters there is when it leaves. Carried as
    // the departure instant rather than a flag, so the countdown cannot be read from
    // a different leg than the one that set it.
    val waitingUntilMs = legStartMs?.takeIf {
        leg.isRide && nowMs < it &&
            (usable == null || haversineMeters(usable.lat, usable.lon, leg.from.lat, leg.from.lon) <= STOP_HIT_METERS)
    }
    val waiting = waitingUntilMs != null

    val phase = when {
        waiting -> JourneyPhase.WAITING
        leg.isRide -> JourneyPhase.RIDING
        else -> JourneyPhase.WALKING
    }

    val alightImminent = leg.isRide && !waiting && (
        (stopsRemaining != null && stopsRemaining <= 1) ||
            (metersToTarget != null && metersToTarget <= ALIGHT_WARN_METERS)
        )

    if (alightImminent && c.legIndex !in c.alightAlerted) {
        alerts += JourneyAlert.PREPARE_TO_ALIGHT
        c = c.copy(alightAlerted = c.alightAlerted + c.legIndex)
    }

    if (waitingUntilMs != null && waitingUntilMs - nowMs <= BOARD_CALL_LEAD_MS &&
        c.legIndex !in c.boardAlerted
    ) {
        alerts += JourneyAlert.BOARD_NOW
        c = c.copy(boardAlerted = c.boardAlerted + c.legIndex)
    }

    return JourneyUpdate(
        cursor = c,
        progress = JourneyProgress(
            phase = phase,
            legIndex = c.legIndex,
            totalLegs = legs.size,
            leg = leg,
            nextLeg = nextLeg,
            stopsRemaining = stopsRemaining,
            nextStopName = if (leg.isRide) stops.getOrNull(c.stopIndex + 1)?.name else null,
            targetName = leg.to.name.ifBlank { null },
            metersToTarget = metersToTarget,
            secondsToTarget = legEndMs?.let { (it - nowMs) / 1000 },
            secondsToDeparture = waitingUntilMs?.let { (it - nowMs) / 1000 },
            alightImminent = alightImminent,
            positionKnown = usable != null
        ),
        alerts = alerts
    )
}

/**
 * The furthest stop on this leg the rider can be shown to have reached. Never goes
 * back: a fix that lands between two poles must not un-pass the one already behind.
 */
private fun advanceStops(leg: RouteLeg, current: Int, fix: GeoFix?): Int {
    if (fix == null) return current
    val stops = leg.rideStops()
    var best = current
    for (i in (current + 1)..stops.lastIndex) {
        val stop = stops[i]
        if (haversineMeters(fix.lat, fix.lon, stop.lat, stop.lon) <= STOP_HIT_METERS) best = i
    }
    return min(best, stops.lastIndex)
}

private fun legComplete(leg: RouteLeg, cursor: JourneyCursor, fix: GeoFix?, nowMs: Long): Boolean {
    if (fix != null) {
        if (haversineMeters(fix.lat, fix.lon, leg.to.lat, leg.to.lon) <= LEG_END_METERS) return true
        if (leg.isRide && cursor.stopIndex >= leg.rideStops().lastIndex) return true
        // With a live position the clock does not get to end a leg: a late bus is
        // still carrying the rider, and skipping ahead there is how a navigator
        // starts lying.
        return false
    }
    val endMs = parseMs(leg.endTime) ?: return false
    return nowMs >= endMs + SCHEDULE_GRACE_MS
}

private fun arrivedProgress(totalLegs: Int, positionKnown: Boolean) = JourneyProgress(
    phase = JourneyPhase.ARRIVED,
    legIndex = totalLegs,
    totalLegs = totalLegs,
    leg = null,
    nextLeg = null,
    stopsRemaining = null,
    nextStopName = null,
    targetName = null,
    metersToTarget = null,
    secondsToTarget = null,
    secondsToDeparture = null,
    alightImminent = false,
    positionKnown = positionKnown
)
