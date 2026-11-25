package com.automatelinux.pt.journey

import com.automatelinux.pt.data.model.Itinerary
import com.automatelinux.pt.data.model.Place
import com.automatelinux.pt.data.model.RouteLeg
import kotlinx.datetime.Instant

/**
 * A bus the rider missed, and the next one of the same line put in its place.
 *
 * Until 2026-11-25 a rider still at the pole after their bus had gone was told
 * "Board 370 now" for as long as they stood there: at 10:39 for a 10:00 bus, with
 * nothing anywhere saying it had left or when the next one comes (owner, at Tel Aviv
 * central station). The engine decides that the bus was missed
 * ([JourneyProgress.boardingMissed]); this file holds the two pure halves of
 * recovering: picking the next trip of the line from a fresh search, and splicing it
 * into the journey without disturbing the legs already travelled.
 */

/**
 * How long past its due time a bus nobody saw leave is still "about to come".
 *
 * A late bus without a live feed looks exactly like a missed one; ten minutes covers
 * the ordinary late bus without leaving the rider staring at "Board now" for long.
 * A live sighting of the line still on its way overrides this outright.
 */
const val MISSED_AFTER_MS = 10 * 60_000L

/**
 * The next trip of [missed]'s line, from the same pole to the same stop, among
 * [options] — a search from the boarding stop to the alight stop. Null when the search
 * has none (the line has finished for the day, or the planner preferred others).
 *
 * Only an itinerary that is that one ride (walks of a few metres at either end
 * allowed) qualifies: anything else is a different journey, and replacing a ride with
 * it would make the rider's leg list lie about what they do.
 */
fun laterRideFor(missed: RouteLeg, options: List<Itinerary>): RouteLeg? {
    val missedStart = parseMs(missed.startTime) ?: return null
    return options.mapNotNull { option ->
        option.legs.filter { it.isRide }.singleOrNull()?.takeIf { ride ->
            sameLine(missed, ride) &&
                near(ride.from, missed.from) && near(ride.to, missed.to) &&
                (parseMs(ride.startTime)?.let { it > missedStart } == true)
        }
    }.minByOrNull { parseMs(it.startTime)!! }
}

private fun sameLine(a: RouteLeg, b: RouteLeg): Boolean =
    if (a.routeId != null && b.routeId != null) {
        a.routeId == b.routeId
    } else {
        a.routeShortName != null && a.routeShortName.equals(b.routeShortName, ignoreCase = true)
    }

private fun near(a: Place, b: Place): Boolean =
    haversineMeters(a.lat, a.lon, b.lat, b.lon) <= STOP_HIT_METERS

/**
 * This itinerary with leg [index] ridden on [ride] instead, and everything after it
 * moved by as much as the new ride arrives later — so the walk after it and the
 * journey's end are timed from the bus actually taken, and the clock does not close
 * them while the rider is still waiting. Legs before [index] are history and keep
 * their times. The planned stops' names stay: they are the same poles.
 */
fun Itinerary.withRideReplaced(index: Int, ride: RouteLeg): Itinerary {
    val old = legs.getOrNull(index) ?: return this
    val shiftMs = (parseMs(ride.endTime) ?: return this) - (parseMs(old.endTime) ?: return this)
    val placed = ride.copy(
        from = ride.from.copy(name = old.from.name.ifBlank { ride.from.name }),
        to = ride.to.copy(name = old.to.name.ifBlank { ride.to.name })
    )
    return copy(
        legs = legs.mapIndexed { i, leg ->
            when {
                i < index -> leg
                i == index -> placed
                else -> leg.shiftedBy(shiftMs)
            }
        },
        endTime = shiftIso(endTime, shiftMs),
        duration = duration + shiftMs / 1000
    )
}

private fun RouteLeg.shiftedBy(ms: Long): RouteLeg = copy(
    startTime = shiftIso(startTime, ms),
    endTime = shiftIso(endTime, ms),
    intermediateStops = intermediateStops?.map { it.copy(arrival = it.arrival?.let { a -> shiftIso(a, ms) }) }
)

private fun shiftIso(iso: String, ms: Long): String =
    parseMs(iso)?.let { Instant.fromEpochMilliseconds(it + ms).toString() } ?: iso

/**
 * The cursor for a journey whose leg [index] was just replaced by a later trip: the
 * board call and the early-bus warning were about the bus that left, so both are
 * armed again for the one coming.
 */
fun JourneyCursor.rearmedFor(index: Int): JourneyCursor = copy(
    boardAlerted = boardAlerted - index,
    earlyAlerted = earlyAlerted - index
)
