package com.automatelinux.pt.journey

import com.automatelinux.pt.data.model.Itinerary
import com.automatelinux.pt.data.model.TransitMode
import com.automatelinux.pt.util.AppStrings
import com.automatelinux.pt.util.formatDistance
import com.automatelinux.pt.util.formatTime

/**
 * One voice for the journey.
 *
 * The panel and the lock-screen notification are read by the same person about the
 * same trip, often seconds apart — when they word it differently the rider has to
 * translate between two descriptions of one bus. So both ask this.
 */
object JourneyText {

    /** The single most useful line: what the rider has to do next. */
    fun headline(progress: JourneyProgress?, strings: AppStrings): String {
        val p = progress ?: return strings.journeyStarting
        return when (p.phase) {
            JourneyPhase.ARRIVED -> strings.journeyYouArrived
            JourneyPhase.WAITING -> {
                val line = p.leg?.let { rideName(it.routeShortName, it.mode, strings) }
                    ?: strings.journeyUpNext
                p.secondsToDeparture?.takeIf { it > 0 }
                    ?.let { strings.journeyLeavesIn(line, strings.formatDuration(it)) }
                    ?: strings.journeyBoardNow(line)
            }
            JourneyPhase.RIDING -> when {
                p.alightImminent -> strings.journeyGetOffNext
                p.stopsRemaining != null -> strings.journeyStopsToGo(p.stopsRemaining)
                else -> strings.journeyStayOn(
                    p.leg?.let { rideName(it.routeShortName, it.mode, strings) } ?: ""
                )
            }
            // A named target goes in the sentence; a nameless one gets its own
            // sentence. Putting "your destination" through the {place} slot is how
            // you get "Walk to Walk to your destination".
            JourneyPhase.WALKING ->
                p.targetName?.let { strings.journeyWalkTo(it) } ?: strings.journeyWalkToDest
        }
    }

    /**
     * The line under it: where this leg ends, and when.
     *
     * Every phase names a clock time now. The panel used to hold exactly one — the
     * trip's final ETA, in the header — so a rider mid-journey could see they were
     * getting off at ת.מרכזית באר שבע without the panel ever saying when, and had to
     * open the step list to find a number the card had room for.
     */
    fun detail(progress: JourneyProgress?, strings: AppStrings): String? {
        val p = progress ?: return null
        return when (p.phase) {
            JourneyPhase.ARRIVED -> null
            JourneyPhase.RIDING -> buildList {
                // The alight time rides with the stop name rather than trailing the
                // line, so the one number here cannot be read as belonging to the
                // next stop mentioned after it.
                p.targetName?.let { add("${strings.journeyGetOffAt} $it${legEndAt(p, strings)}") }
                p.nextStopName?.takeIf { !p.alightImminent }
                    ?.let { add("${strings.journeyNextStop} $it") }
            }.joinToString(" · ").ifBlank { null }
            // The pole's printed code rides along — it is how a rider standing
            // between two stops confirms which one the app means, and what every
            // other app shows for the same reason.
            JourneyPhase.WAITING -> p.leg?.from?.name?.takeIf { it.isNotBlank() }
                ?.let { "${strings.journeyBoardAt} $it${legStartAt(p, strings)}${stopCodeAt(p, strings)}" }
            JourneyPhase.WALKING -> buildList {
                walkDistance(p, strings)?.let { add(it) }
                walkTiming(p, strings)?.let { add(it) }
                walkDeadline(p, strings)?.let { add(it) }
            }.joinToString(" · ").ifBlank { null }
        }
    }

    /** " · 12:20" — the leg's scheduled end, or nothing when it has no readable one. */
    private fun legEndAt(p: JourneyProgress, strings: AppStrings): String =
        p.leg?.endTime?.takeIf { it.isNotBlank() }?.let { " · ${formatTime(it)}" } ?: ""

    /** " · 11:35" — the leg's scheduled start, or nothing when it has no readable one. */
    private fun legStartAt(p: JourneyProgress, strings: AppStrings): String =
        p.leg?.startTime?.takeIf { it.isNotBlank() }?.let { " · ${formatTime(it)}" } ?: ""

    /** " · stop 13868" — the boarding pole's printed code, when the feed carries one. */
    private fun stopCodeAt(p: JourneyProgress, strings: AppStrings): String =
        p.leg?.fromStopCode?.takeIf { it.isNotBlank() }
            ?.let { " · ${strings.journeyStopCode(it)}" } ?: ""

    /**
     * Metres still to WALK — pavement, not the crow's flight.
     *
     * `metersToTarget` is a straight line, and printing it next to a duration that
     * covers the real path is what made "150 m · 7 min on foot": 150 m to the pole
     * through the buildings, 385 m of pavement around them, and a rider left to
     * conclude the app thinks they crawl. The leg carries the path length; live
     * progress shortens it by the same fraction the minutes and the progress bar use,
     * so all three move together and all three mean the same walk.
     *
     * A leg the server sent no distance for has only the straight line to offer, and
     * says so at whatever length it is rather than printing nothing.
     */
    private fun walkDistance(p: JourneyProgress, strings: AppStrings): String? {
        val path = p.leg?.distanceMeters
        if (path != null) {
            val left = (path * (1.0 - p.legFraction())).toInt().coerceIn(0, path)
            return formatDistance(left, strings)
        }
        return p.metersToTarget?.let { formatDistance(it.toInt(), strings) }
    }

    /**
     * How much walk is left — the same walk the metres beside it are measuring.
     *
     * The two halves of this line have to describe one thing, and twice now they have
     * not. Reading the countdown to the leg's scheduled end gave "151 m · 1h 10min on
     * foot", because a walk to a stop is timetabled to end when the bus leaves and
     * that gap is the wait, not the walk. Reading the leg's whole duration instead
     * gave "150 m · 7 min on foot" — true of the 385 m leg, absurd of the 150 m still
     * to go, and a rider does not read one number as live and the next as planned.
     *
     * So the duration is scaled by the same measured fraction the progress bar fills
     * by: metres against metres while a fix is coming in, the clock only when nothing
     * better exists. A walk with any distance left takes at least a minute to say, so
     * the tail of one reads "1 min" rather than "0 min".
     */
    private fun walkTiming(p: JourneyProgress, strings: AppStrings): String? {
        val total = p.leg?.duration?.takeIf { it > 0 } ?: return null
        // The minute floor may EXCEED a short leg's total: a 35-second hop across the
        // interchange still reads "1 min", never "0 min" — and coerceIn(60, 35) is an
        // empty range, which threw on exactly those legs.
        val left = (total * (1.0 - p.legFraction())).toLong().coerceIn(60L, maxOf(total, 60L))
        return strings.journeyOnFoot(strings.formatDuration(left))
    }

    /**
     * The instant the walk is FOR: when the ride at the end of it leaves.
     *
     * This is the deadline "3 min on foot" is measured against, and the only reason a
     * rider looks at a walk card twice. It names the RIDE's departure rather than the
     * walk's own scheduled end, because a router that grants slack ends the walk
     * before the bus goes and the earlier of the two is not the one you can miss.
     *
     * The last walk of a trip gets none: it ends at the rider's own pin, at the time
     * the header has been showing as the ETA all along.
     */
    private fun walkDeadline(p: JourneyProgress, strings: AppStrings): String? {
        val next = p.nextLeg?.takeIf { it.isRide } ?: return null
        return next.startTime.takeIf { it.isNotBlank() }?.let { strings.journeyBy(formatTime(it)) }
    }

    /**
     * The live banner: "64 arrives in 6 min · then 41 min".
     *
     * This is the line every other app leads with and this panel used to lack —
     * the timetable countdown answers when the bus was *supposed* to come, and a
     * rider standing at a pole wants the bus that actually exists. Null when the
     * feed has nothing (no sighting is shown as silence, never as a guess).
     */
    fun liveBanner(
        live: JourneyLiveInfo?,
        itinerary: Itinerary?,
        nowMs: Long,
        strings: AppStrings
    ): String? {
        val info = live ?: return null
        val ride = itinerary?.legs?.getOrNull(info.legIndex) ?: return null
        with(JourneyLive) { if (info.isStale(nowMs)) return null }
        val line = rideName(ride.routeShortName, ride.mode, strings)
        val minutesAway = (info.arrivalMs - nowMs) / 60_000L
        val head = if (minutesAway < 1) {
            strings.journeyBusDueNow(line)
        } else {
            strings.journeyBusArrivesIn(line, strings.formatDuration(minutesAway * 60))
        }
        val next = info.nextArrivalMs?.let { it - nowMs }?.takeIf { it >= 60_000L }
            ?.let { strings.journeyThenIn(strings.formatDuration(it / 60_000L * 60)) }
        return if (next != null) "$head · $next" else head
    }

    fun notificationTitle(progress: JourneyProgress?, strings: AppStrings): String =
        headline(progress, strings)

    fun notificationBody(progress: JourneyProgress?, strings: AppStrings): String {
        val p = progress ?: return strings.journeyStarting
        val parts = buildList {
            detail(p, strings)?.let { add(it) }
            if (!p.positionKnown) add(strings.journeyScheduleOnly)
        }
        return parts.joinToString(" · ").ifBlank { strings.journeyLabel }
    }

    /** Title and body for an alert, or null when there is nothing worth interrupting for. */
    fun alertText(
        alert: JourneyAlert,
        progress: JourneyProgress?,
        strings: AppStrings
    ): Pair<String, String>? = when (alert) {
        JourneyAlert.PREPARE_TO_ALIGHT -> strings.journeyGetOffNext to
            (progress?.targetName?.let { "${strings.journeyGetOffAt} $it" } ?: strings.journeyLabel)
        JourneyAlert.BOARD_NOW -> {
            val line = progress?.leg?.let { rideName(it.routeShortName, it.mode, strings) }
            strings.journeyBoardNow(line ?: "") to
                (progress?.leg?.from?.name?.let { "${strings.journeyBoardAt} $it" } ?: strings.journeyLabel)
        }
        JourneyAlert.ARRIVED -> strings.journeyYouArrived to strings.journeyLabel
    }

    /** "64", or the mode's name when the service has no short name worth printing. */
    fun rideName(routeShortName: String?, mode: TransitMode, strings: AppStrings): String =
        routeShortName?.takeIf { it.isNotBlank() && it.length <= 10 }
            ?: when (mode) {
                TransitMode.BUS -> strings.busMode
                TransitMode.RAIL -> strings.trainMode
                TransitMode.TRAM -> strings.tramMode
                TransitMode.SUBWAY -> strings.subwayMode
                TransitMode.FERRY -> strings.ferryMode
                else -> strings.journeyUpNext
            }
}
