package com.automatelinux.pt.journey

import com.automatelinux.pt.data.model.TransitMode
import com.automatelinux.pt.util.AppStrings
import com.automatelinux.pt.util.formatDistance

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

    /** The line under it: where this leg ends, and how far off that is. */
    fun detail(progress: JourneyProgress?, strings: AppStrings): String? {
        val p = progress ?: return null
        return when (p.phase) {
            JourneyPhase.ARRIVED -> null
            JourneyPhase.RIDING -> buildList {
                p.targetName?.let { add("${strings.journeyGetOffAt} $it") }
                p.nextStopName?.takeIf { !p.alightImminent }
                    ?.let { add("${strings.journeyNextStop} $it") }
            }.joinToString(" · ").ifBlank { null }
            JourneyPhase.WAITING -> p.leg?.from?.name?.takeIf { it.isNotBlank() }
                ?.let { "${strings.journeyBoardAt} $it" }
            JourneyPhase.WALKING -> buildList {
                p.metersToTarget?.let { add(formatDistance(it.toInt(), strings)) }
                p.leg?.distanceMeters?.takeIf { p.metersToTarget == null }
                    ?.let { add(formatDistance(it, strings)) }
                walkTiming(p, strings)?.let { add(it) }
            }.joinToString(" · ").ifBlank { null }
        }
    }

    /**
     * The half of a walk that metres cannot say: how long it takes.
     *
     * Every other phase of the panel carries a clock — a ride being waited for leaves
     * in so many minutes, a ride underway has so many stops left — and the walk card
     * carried none, so "378 m" was the whole answer to the one question a rider on
     * foot is actually asking.
     *
     * It is the leg's OWN length, deliberately, and not the countdown to its end. A
     * walk to a stop is timetabled to end when the bus leaves, so a rider who starts
     * the journey early is not late for anything — but `secondsToTarget` there is the
     * wait, not the walk, and reading it out loud produced "151 m · 1h 10min on foot"
     * for a two-minute stroll. The wait is the WAITING phase's line to say, once the
     * rider is standing at the pole.
     */
    private fun walkTiming(p: JourneyProgress, strings: AppStrings): String? =
        p.leg?.duration?.takeIf { it > 0 }?.let { strings.journeyOnFoot(strings.formatDuration(it)) }

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
