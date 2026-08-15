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
            JourneyPhase.WALKING -> {
                val target = p.targetName ?: strings.journeyWalkToDest
                strings.journeyWalkTo(target)
            }
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
            }.joinToString(" · ").ifBlank { null }
        }
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
