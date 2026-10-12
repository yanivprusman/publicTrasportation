package com.automatelinux.pt.journey

import com.automatelinux.pt.data.model.Itinerary
import com.automatelinux.pt.data.model.Place
import kotlinx.serialization.Serializable

/**
 * A journey the rider started, kept on the phone so it outlives the app.
 *
 * The whole itinerary is kept, not just its two ends: resuming means rejoining THIS
 * bus at THIS stop, and a fresh search an hour later would hand back a different
 * trip. The cursor is kept with it so a resumed journey picks up at the leg it had
 * reached, and does not buzz again for a stop it already called.
 */
@Serializable
data class StartedTrip(
    /** When it was first started; also its identity in the list. */
    val startedAtMs: Long,
    val itinerary: Itinerary,
    val cursor: JourneyCursor = JourneyCursor(),
    /**
     * When the rider ended it, or it ended itself on arrival. Null means it was
     * interrupted — the app swiped away, the process killed — without anyone saying
     * the trip was over, and that is the one the app rejoins by itself on launch.
     */
    val endedAtMs: Long? = null
) {
    val origin: Place? get() = itinerary.legs.firstOrNull()?.from
    val destination: Place? get() = itinerary.legs.lastOrNull()?.to

    /**
     * Whether there is still a journey to rejoin: not arrived, and the timetable has
     * not run out more than [RESUME_GRACE_MS] ago. Past that, the buses on it are
     * gone and the only useful thing left is the same two ends, searched again.
     */
    fun resumable(nowMs: Long): Boolean {
        if (cursor.arrived) return false
        val endMs = parseMs(itinerary.endTime) ?: return false
        return nowMs <= endMs + RESUME_GRACE_MS
    }

    /** Interrupted and still current: what the app resumes on its own. */
    fun interrupted(nowMs: Long): Boolean = endedAtMs == null && resumable(nowMs)

    companion object {
        /** Buses run late; a trip 30 minutes past its timetabled end may still be on. */
        const val RESUME_GRACE_MS = 30 * 60_000L
        const val MAX_KEPT = 10
    }
}

/**
 * The list after [trip] was started (or resumed): it goes to the front, any other
 * open trip is closed — only one journey runs at a time, so starting this one ended
 * that one — and older finished runs of the same two ends make way, so a daily
 * commute does not fill the list with copies of itself.
 */
fun recordStartedTrip(list: List<StartedTrip>, trip: StartedTrip, nowMs: Long): List<StartedTrip> {
    val others = list
        .filter { it.startedAtMs != trip.startedAtMs }
        .map { if (it.endedAtMs == null) it.copy(endedAtMs = nowMs) else it }
        .filterNot { !it.resumable(nowMs) && sameEnds(it, trip) }
    return (listOf(trip.copy(endedAtMs = null)) + others).take(StartedTrip.MAX_KEPT)
}

private fun sameEnds(a: StartedTrip, b: StartedTrip): Boolean {
    val ao = a.origin; val ad = a.destination; val bo = b.origin; val bd = b.destination
    if (ao == null || ad == null || bo == null || bd == null) return false
    return samePlace(ao, bo) && samePlace(ad, bd)
}

// ~10 m of slack, the same tolerance a starred route uses to recognise its ends.
private fun samePlace(a: Place, b: Place): Boolean =
    kotlin.math.abs(a.lat - b.lat) < 0.0001 && kotlin.math.abs(a.lon - b.lon) < 0.0001
