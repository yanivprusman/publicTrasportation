package com.automatelinux.pt.util

import androidx.compose.runtime.compositionLocalOf

/**
 * Every user-facing string, in each language the app speaks.
 *
 * An interface rather than a data class on purpose: a dex method invocation may use
 * at most 255 argument registers, and at 256 properties the generated constructor
 * call could no longer be verified — the app died at launch with VerifyError before
 * it drew anything. Assigning each property in its own statement has no such ceiling,
 * and leaves every `strings.foo` call site untouched. Do not turn this back into a
 * data class to add "just one more string".
 */
interface AppStrings {
    val routePlanner: String
    val stationArrivals: String
    val settings: String
    val opacitySettings: String
    val hideOpacitySettings: String
    val sheet: String
    val cards: String
    val from: String
    val to: String
    val swapOriginDestination: String
    val addStop: String
    val stopAlongTheWay: String
    val removeStop: String
    val searchRoutes: String
    val editSearch: String
    val nothingFound: String
    /** The search never ran — the server could not be reached or did not answer with results. */
    val searchUnavailable: String
    val showAllDepartures: (Int) -> String
    val searchingRoutes: String
    val retry: String
    val noRoutesFound: String
    val now: String
    val departAt: String
    val arriveBy: String
    val next: String
    val cancel: String
    val ok: String
    val direct: String
    val transferCount: (Int) -> String
    val walkDescription: (duration: String, destination: String) -> String
    val transitDescription: (mode: String, route: String, destination: String, duration: String) -> String
    val showStops: (Int) -> String
    val hideStops: (Int) -> String
    val waitFor: (String) -> String
    val walkMode: String
    val busMode: String
    val trainMode: String
    val tramMode: String
    val subwayMode: String
    val ferryMode: String
    val bikeMode: String
    val carMode: String
    val compareTransit: String
    val directBikeTitle: String
    val directCarTitle: String
    val directKm: (String) -> String
    val directArrive: (String) -> String
    val directBikeNote: String
    val directCarNote: String
    val formatDuration: (Long) -> String
    val station: String
    val updatedAgo: (String) -> String
    val justNow: String
    val secondsAgo: (Long) -> String
    val minutesAgo: (Long) -> String
    val filterByLine: String
    val vehicles: String
    val monitoredVehicles: (Int) -> String
    val noVehiclesFound: String
    val headerLine: String
    val headerDir: String
    val headerDest: String
    val headerArrival: String
    val headerDist: String
    val vehicleRef: (String) -> String
    val distanceMeters: (Int) -> String
    val tripDistanceTravelled: (String) -> String
    val fullArrival: (String) -> String
    val showOnMap: String
    val arrivalNow: String
    val arrivalInMin: (Long) -> String
    /** Boarding time of the first ride on a route card: "departs 11:09". */
    val departsAt: (String) -> String
    /** Live countdown to that boarding time: "in 7 min", "in 1h 20min". */
    val departsIn: (String) -> String
    /** The first ride is leaving this minute. */
    val departsNow: String
    /** The card's first ride is already gone — the result is stale, say so. */
    val departureGone: String
    /** Where the boarding time came from: the timetable, or a live feed. */
    val departureTimetable: String
    val departureLive: String
    /** "from Midreshet Ben-Gurion" — the stop this ride is caught at. */
    val boardingFrom: (String) -> String
    /** "8 min late · timetabled 15:39" — the feed against the timetable. */
    val runningLate: (String, String) -> String
    val runningEarly: (String, String) -> String
    /** "then 14:39 · 15:09" — the departures after the one on this card. */
    val thenDepartures: (String) -> String
    /**
     * Which day a result leaves on. Only ever drawn when it is NOT today: an
     * "HH:mm" with no date is read as today by everyone, and the Fastest sort
     * puts tomorrow's trips among tonight's.
     */
    val dayTomorrow: String
    val dayYesterday: String
    /** Past tomorrow, name the date: (day of month, month 1-12, ISO weekday 1=Mon). */
    val formatShortDate: (Int, Int, Int) -> String
    /** The number printed on the pole, which is what other apps and signs use. */
    val stopCodeLabel: (String) -> String
    /** A walk leg's length, which "4 min" does not tell you. */
    val walkDistance: (Int) -> String
    val connectingToServer: String
    val connectionFailed: String
    val serversTried: (String) -> String
    val noServerReachable: String
    val useCurrentLocation: String
    val clear: String
    val locationIconDot: String
    val locationIconPerson: String
    val debugSettings: String
    val debugDescription: String
    val autoSearchRoutes: String
    val expandBottomSheet: String
    val save: String
    val debugFill: String
    val showPanel: String
    val issueClarifier: String
    val notAvailable: String
    val nextArrival: String
    val followMyLocation: String
    val mapStyle: String
    val mapStyleDark: String
    val mapStyleLight: String
    val mapStyleSatellite: String
    val earlier: String
    val later: String
    val fastest: String
    val fewerTransfers: String
    val lessWalking: String
    val routeOptions: String
    val maxWalkLabel: String
    val noWalkLimit: String
    val walkMinutesChip: (Int) -> String
    val filteredModesHint: String
    val fareEstimate: (String) -> String
    val nearbyStops: String
    val noNearbyStops: String
    val walkingDistance: (Int) -> String
    val serviceAlerts: String
    val noAlerts: String
    val favorites: String
    val favoriteLines: String
    val favoriteStations: String
    val noFavorites: String
    val addedToFavorites: String
    val removedFromFavorites: String
    val frequentRoute: String
    val quickRoute: (String, String) -> String
    val trackBus: String
    val trackingBus: String
    val stopTracking: String
    val busLocationUpdated: String
    val trackingPositionUpdated: (String) -> String
    val trackingVehicleOf: (Int, Int) -> String
    val trackingOtherVehicle: String
    val trackingSearching: String
    val trackingNoMonitoredStop: String
    val trackingNoVehicle: String
    val trackingNotStartedYet: (String) -> String
    val trackingError: String
    val trackingDistanceAway: (String) -> String
    val trackingFrameBus: String
    val liveBusesNearby: String
    val liveBusesSearching: String
    val liveBusesNone: (String) -> String
    val liveBusesZoomIn: (String) -> String
    val liveBusesOffscreen: (String) -> String
    val accessAccessible: String
    val accessNotAccessible: String
    val distanceKm: (String) -> String
    val distanceM: (Int) -> String
    val departureReminder: String
    val reminderSet: (String) -> String
    val reminderCancelled: String
    val cancelReminder: String
    val reminderNotification: (String, String) -> String
    val minutesBefore: (Int) -> String
    val linesBrowser: String
    val searchLine: String
    val viewLine: String
    val direction: (String) -> String
    val lineShape: String
    val noShapeData: String
    val timetable: String
    val commonLines: String
    val saveAs: String
    val home: String
    val work: String
    val setHome: String
    val selectedLocation: String
    val startJourney: String
    val journeyLabel: String
    val journeyExit: String
    val journeyWalkTo: (String) -> String
    val journeyWalkToDest: String
    val journeyOnFoot: (String) -> String
    val journeyTake: (String) -> String
    val journeyBoardAt: String
    val journeyGetOffAt: String
    val journeyStopsCount: (Int) -> String
    val journeyUpNext: String
    val journeyArriveDest: String
    val journeyStepOf: (Int, Int) -> String
    val journeyBack: String
    val journeyArrive: String
    val journeyDone: String
    val journeyArrived: String
    val journeyYouArrived: String
    val journeyArrivedSummary: (String, String) -> String
    val journeyLeave: String
    val journeyDeparts: String
    val journeyScheduled: (String) -> String
    val dayOverview: String
    val dayLoading: String
    val dayFailed: String
    val dayNone: String
    val dayFirst: String
    val dayLast: String
    val dayFastest: String
    val dayDepartures: String
    val dayShowTrip: String
    val dayCaption: String
    val dayTruncated: (Int) -> String
    val departureBoard: String
    val pinWidget: String
    val widgetPinUnsupported: String
    val widgetNoDepartures: String
    val widgetLoadFailed: String
    val boardLive: String
    val boardNow: String
    val boardMinUnit: String
    val boardLoading: String
    val boardNone: String
    val boardClose: String
    val boardFilterNote: (String) -> String
    val boardStop: (String) -> String
    val timetableCaption: String
    val allLinesChip: String
    val timetableTomorrow: String
    val timetableNone: String
    val timetableUnavailable: String
    val departuresTitle: String
    val liveTag: String
    val scheduledTag: String
    val toDestination: (String) -> String
    val addFavorite: String
    val removeFavorite: String
    val arrivalsFetchError: String
    val arrivalsRefreshFailed: String
    val shareTrip: String
    val scanToOpenTrip: String
    val shareLink: String
    val copyLink: String
    val linkCopied: String
    val close: String
    // Up-front pricing disclosure — shown on first launch and kept in settings.
    val pricingNoticeTitle: String
    val pricingNoticeFree: String
    val pricingNoticeFuture: String
    val pricingNoticeWarning: String
    val pricingNoticeFounder: String
    val pricingNoticeAcknowledge: String
    val pricingSectionTitle: String
    val pricingSectionBody: String
    // Registration
    val registerTitle: String
    val registerSubtitle: String
    val registerEmail: String
    val registerPhone: String
    val registerSubmit: String
    val registerSubmitting: String
    val registerInvalidEmail: String
    val registerInvalidPhone: String
    val registerFailed: String
    val registerPrivacy: String
    val registerPickNumber: String
}

val EnStrings: AppStrings = object : AppStrings {
    override val routePlanner: String = "Route Planner"
    override val stationArrivals: String = "Station Arrivals"
    override val settings: String = "Settings"
    override val opacitySettings: String = "Opacity Settings"
    override val hideOpacitySettings: String = "Hide Opacity Settings"
    override val sheet: String = "Sheet"
    override val cards: String = "Cards"
    override val from: String = "From"
    override val to: String = "To"
    override val swapOriginDestination: String = "Swap origin and destination"
    override val addStop: String = "Add stop"
    override val stopAlongTheWay: String = "Stop along the way"
    override val removeStop: String = "Remove stop"
    override val searchRoutes: String = "Search Routes"
    override val editSearch: String = "Edit search"
    override val nothingFound: String = "Nothing found — try a different spelling or Hebrew"
    override val searchUnavailable: String = "Can't reach the PT server — search unavailable"
    override val showAllDepartures: (Int) -> String = { n -> "Show all $n departures" }
    override val searchingRoutes: String = "Searching routes..."
    override val retry: String = "Retry"
    override val noRoutesFound: String = "No routes found for this trip. Try a different time or destination."
    override val now: String = "Now"
    override val departAt: String = "Depart At"
    override val arriveBy: String = "Arrive By"
    override val next: String = "Next"
    override val cancel: String = "Cancel"
    override val ok: String = "OK"
    override val direct: String = "Direct"
    override val transferCount: (Int) -> String = { n -> "$n transfer${if (n > 1) "s" else ""}" }
    override val walkDescription: (duration: String, destination: String) -> String = { dur, dest -> "Walk $dur to $dest" }
    override val transitDescription: (mode: String, route: String, destination: String, duration: String) -> String = { mode, route, dest, dur -> "$mode$route toward $dest — $dur" }
    override val showStops: (Int) -> String = { n -> "Show $n stops" }
    override val hideStops: (Int) -> String = { n -> "Hide $n stops" }
    override val waitFor: (String) -> String = { d -> "Wait $d" }
    override val walkMode: String = "Walk"
    override val busMode: String = "Bus"
    override val trainMode: String = "Train"
    override val tramMode: String = "Tram"
    override val subwayMode: String = "Subway"
    override val ferryMode: String = "Ferry"
    override val bikeMode: String = "Bike"
    override val carMode: String = "Car"
    override val compareTransit: String = "Transit"
    override val directBikeTitle: String = "Bike route"
    override val directCarTitle: String = "Car route"
    override val directKm: (String) -> String = { km -> "$km km" }
    override val directArrive: (String) -> String = { time -> "Arrive at $time" }
    override val directBikeNote: String = "Via streets and roads open to bikes"
    override val directCarNote: String = "Estimate — traffic not included"
    override val formatDuration: (Long) -> String = { seconds ->
        val mins = seconds / 60
        when {
            mins < 60 -> "$mins min"
            mins % 60 == 0L -> "${mins / 60}h"
            else -> "${mins / 60}h ${mins % 60}min"
        }
    }
    override val station: String = "Station"
    override val updatedAgo: (String) -> String = { ago -> "Updated $ago" }
    override val justNow: String = "just now"
    override val secondsAgo: (Long) -> String = { s -> "${s}s ago" }
    override val minutesAgo: (Long) -> String = { m -> "${m}m ago" }
    override val filterByLine: String = "Filter by line"
    override val vehicles: String = "Vehicles"
    override val monitoredVehicles: (Int) -> String = { n -> "Monitored Vehicles: $n" }
    override val noVehiclesFound: String = "No vehicles found"
    override val headerLine: String = "Line"
    override val headerDir: String = "Dir"
    override val headerDest: String = "Dest"
    override val headerArrival: String = "Arrival"
    override val headerDist: String = "Dist"
    override val vehicleRef: (String) -> String = { ref -> "Vehicle: $ref" }
    override val distanceMeters: (Int) -> String = { m -> "Distance: ${m}m" }
    override val tripDistanceTravelled: (String) -> String = { d -> "Travelled $d on this trip" }
    override val fullArrival: (String) -> String = { t -> "Full arrival: $t" }
    override val showOnMap: String = "Show on map"
    override val arrivalNow: String = "now"
    override val arrivalInMin: (Long) -> String = { m -> "in ${m}min" }
    override val departsAt: (String) -> String = { t -> "departs $t" }
    override val departsIn: (String) -> String = { d -> "in $d" }
    override val departsNow: String = "departing now"
    override val departureGone: String = "already left"
    override val departureTimetable: String = "timetable"
    override val departureLive: String = "live"
    override val boardingFrom: (String) -> String = { stop -> "from $stop" }
    override val thenDepartures: (String) -> String = { times -> "then $times" }
    override val dayTomorrow: String = "Tomorrow"
    override val dayYesterday: String = "Yesterday"
    override val formatShortDate: (Int, Int, Int) -> String = { day, month, isoWeekday ->
        val weekday = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            .getOrNull(isoWeekday - 1)
        val monthName = listOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        ).getOrNull(month - 1)
        if (weekday != null && monthName != null) "$weekday $day $monthName" else "$day/$month"
    }
    override val runningLate: (String, String) -> String = { late, scheduled -> "$late late · timetabled $scheduled" }
    override val runningEarly: (String, String) -> String = { early, scheduled -> "$early early · timetabled $scheduled" }
    override val stopCodeLabel: (String) -> String = { code -> "stop $code" }
    override val walkDistance: (Int) -> String = { m -> if (m >= 1000) "${m / 1000}.${(m % 1000) / 100} km" else "$m m" }
    override val connectingToServer: String = "Connecting to PT server..."
    override val connectionFailed: String = "Connection failed"
    override val serversTried: (String) -> String = { s -> "Servers tried: $s" }
    override val noServerReachable: String = "No PT server reachable"
    override val useCurrentLocation: String = "Use current location"
    override val clear: String = "Clear"
    override val locationIconDot: String = "Location: Blue Dot"
    override val locationIconPerson: String = "Location: Person"
    override val debugSettings: String = "Debug Settings"
    override val debugDescription: String = "Configure what happens when the debug button is tapped."
    override val autoSearchRoutes: String = "Auto-search routes"
    override val expandBottomSheet: String = "Expand bottom sheet"
    override val save: String = "Save"
    override val debugFill: String = "Debug fill"
    override val showPanel: String = "Show panel"
    override val issueClarifier: String = "Issue Clarifier"
    override val notAvailable: String = "N/A"
    override val nextArrival: String = "Next Arrival"
    override val followMyLocation: String = "Follow my location"
    override val mapStyle: String = "Map style"
    override val mapStyleDark: String = "Dark"
    override val mapStyleLight: String = "Light"
    override val mapStyleSatellite: String = "Satellite"
    override val earlier: String = "Earlier"
    override val later: String = "Later"
    override val fastest: String = "Fastest"
    override val fewerTransfers: String = "Fewer transfers"
    override val lessWalking: String = "Less walking"
    override val routeOptions: String = "Route options"
    override val maxWalkLabel: String = "Max walk"
    override val noWalkLimit: String = "No limit"
    override val walkMinutesChip: (Int) -> String = { m -> "$m min" }
    override val filteredModesHint: String = "Mode filter is on — some routes are hidden"
    override val fareEstimate: (String) -> String = { amount -> "~$amount" }
    override val nearbyStops: String = "Nearby Stops"
    override val noNearbyStops: String = "No stops found nearby"
    override val walkingDistance: (Int) -> String = { m -> "${m}m walk" }
    override val serviceAlerts: String = "Service Alerts"
    override val noAlerts: String = "No active alerts"
    override val favorites: String = "Favorites"
    override val favoriteLines: String = "Favorite Lines"
    override val favoriteStations: String = "Favorite Stations"
    override val noFavorites: String = "No favorites yet"
    override val addedToFavorites: String = "Added to favorites"
    override val removedFromFavorites: String = "Removed from favorites"
    override val frequentRoute: String = "Quick Route"
    override val quickRoute: (String, String) -> String = { from, to -> "$from → $to" }
    override val trackBus: String = "Track Bus"
    override val trackingBus: String = "Tracking..."
    override val stopTracking: String = "Stop Tracking"
    override val busLocationUpdated: String = "Bus location updated"
    override val trackingPositionUpdated: (String) -> String = { ago -> "Position updated $ago" }
    override val trackingVehicleOf: (Int, Int) -> String = { i, n -> "$i / $n" }
    override val trackingOtherVehicle: String = "Show another bus on this line"
    override val trackingSearching: String = "Looking for the bus…"
    override val trackingNoMonitoredStop: String = "No live-tracked stop near the boarding point"
    override val trackingNoVehicle: String = "No bus on this line is reporting right now"
    override val trackingNotStartedYet: (String) -> String = { t -> "Your bus hasn't started its run yet — scheduled for $t" }
    override val trackingError: String = "Live position unavailable"
    override val trackingDistanceAway: (String) -> String = { distance -> "$distance away" }
    override val trackingFrameBus: String = "Show the bus"
    override val liveBusesNearby: String = "Live buses nearby"
    override val liveBusesSearching: String = "Looking for live buses…"
    override val liveBusesNone: (String) -> String = { radius -> "No live buses within $radius of the map centre" }
    override val liveBusesZoomIn: (String) -> String = { radius -> "Only searching $radius around the centre — zoom in to cover what you see" }
    override val liveBusesOffscreen: (String) -> String = { distance -> "Nearest live bus is $distance away — zoom out to see it" }
    override val accessAccessible: String = "Wheelchair accessible"
    override val accessNotAccessible: String = "Not wheelchair accessible"
    override val distanceKm: (String) -> String = { km -> "$km km" }
    override val distanceM: (Int) -> String = { m -> "$m m" }
    override val departureReminder: String = "Departure Reminder"
    override val reminderSet: (String) -> String = { time -> "Reminder set for $time" }
    override val reminderCancelled: String = "Reminder cancelled"
    override val cancelReminder: String = "Cancel reminder"
    override val reminderNotification: (String, String) -> String = { line, time -> "Bus $line departs at $time" }
    override val minutesBefore: (Int) -> String = { m -> "${m}min before" }
    override val linesBrowser: String = "Lines"
    override val searchLine: String = "Search line number"
    override val viewLine: String = "View"
    override val direction: (String) -> String = { d -> "Direction $d" }
    override val lineShape: String = "Line Route"
    override val noShapeData: String = "No route data available"
    override val timetable: String = "Timetable"
    override val commonLines: String = "Common Lines"
    override val saveAs: String = "Save as"
    override val home: String = "Home"
    override val work: String = "Work"
    override val setHome: String = "Set home"
    override val selectedLocation: String = "Selected location"
    override val startJourney: String = "Start Journey"
    override val journeyLabel: String = "Journey"
    override val journeyExit: String = "Exit journey"
    override val journeyWalkTo: (String) -> String = { place -> "Walk to $place" }
    override val journeyWalkToDest: String = "Walk to your destination"
    override val journeyOnFoot: (String) -> String = { d -> "$d on foot" }
    override val journeyTake: (String) -> String = { ride -> "Take $ride" }
    override val journeyBoardAt: String = "Board at"
    override val journeyGetOffAt: String = "Get off at"
    override val journeyStopsCount: (Int) -> String = { n -> if (n == 1) "1 stop" else "$n stops" }
    override val journeyUpNext: String = "Up next"
    override val journeyArriveDest: String = "Arrive at your destination"
    override val journeyStepOf: (Int, Int) -> String = { n, m -> "Step $n of $m" }
    override val journeyBack: String = "Back"
    override val journeyArrive: String = "Arrive"
    override val journeyDone: String = "Done"
    override val journeyArrived: String = "Arrived"
    override val journeyYouArrived: String = "You've arrived!"
    override val journeyArrivedSummary: (String, String) -> String = { time, d -> "Arrived $time · $d total" }
    override val journeyLeave: String = "Leave"
    override val journeyDeparts: String = "Departs"
    override val journeyScheduled: (String) -> String = { time -> "Scheduled $time" }
    override val dayOverview: String = "Day overview"
    override val dayLoading: String = "Scanning the whole day..."
    override val dayFailed: String = "Day overview failed"
    override val dayNone: String = "No departures found for this day"
    override val dayFirst: String = "First"
    override val dayLast: String = "Last"
    override val dayFastest: String = "Fastest"
    override val dayDepartures: String = "Departures"
    override val dayShowTrip: String = "Show trip"
    override val dayCaption: String = "Every departure today · bar height = trip duration (min) · tap a bar"
    override val dayTruncated: (Int) -> String = { n -> "Showing the first $n departures of the day" }
    override val departureBoard: String = "Departure Board"
    override val pinWidget: String = "Add Widget to Home Screen"
    override val widgetPinUnsupported: String = "Your launcher does not support pinning widgets"
    override val widgetNoDepartures: String = "No departures right now"
    override val widgetLoadFailed: String = "Couldn't update — tap the arrow to retry"
    override val boardLive: String = "LIVE"
    override val boardNow: String = "NOW"
    override val boardMinUnit: String = "min"
    override val boardLoading: String = "Loading departures..."
    override val boardNone: String = "No departures right now"
    override val boardClose: String = "Close board"
    override val boardFilterNote: (String) -> String = { line -> "Line $line only" }
    override val boardStop: (String) -> String = { code -> "Stop $code" }
    override val timetableCaption: String = "Scheduled departures"
    override val allLinesChip: String = "All"
    override val timetableTomorrow: String = "Tomorrow"
    override val timetableNone: String = "No scheduled departures"
    override val timetableUnavailable: String = "Timetable is unavailable for this stop"
    override val departuresTitle: String = "Next departures"
    override val liveTag: String = "live"
    override val scheduledTag: String = "scheduled"
    override val toDestination: (String) -> String = { dest -> "to $dest" }
    override val addFavorite: String = "Add favorite"
    override val removeFavorite: String = "Remove favorite"
    override val arrivalsFetchError: String = "Couldn't load arrivals"
    override val arrivalsRefreshFailed: String = "Couldn't refresh — showing last known arrivals"
    override val shareTrip: String = "Share trip"
    override val scanToOpenTrip: String = "Scan to open this trip"
    override val shareLink: String = "Share link"
    override val copyLink: String = "Copy"
    override val linkCopied: String = "Link copied"
    override val close: String = "Close"
    override val pricingNoticeTitle: String = "Free today — paid one day"
    override val pricingNoticeFree: String = "Every feature is open to everyone, free. We never ask for a credit card."
    override val pricingNoticeFuture: String = "We're telling you up front so you're never surprised: one day this app will " +
        "become paid. The price will be small — no more than a single ride's fare per year — and " +
        "you'll choose between paying yearly or once for ten years."
    override val pricingNoticeWarning: String = "We'll let you know in advance before anything changes. " +
        "You will never be charged without warning."
    override val pricingNoticeFounder: String = "Users who were with us from the start will get the best price."
    override val pricingNoticeAcknowledge: String = "Got it"
    override val pricingSectionTitle: String = "Pricing"
    override val pricingSectionBody: String = "The app is free for everyone right now. It will become paid later — no more " +
        "than one ride's fare per year, or a single payment covering ten years, your choice. " +
        "We'll let you know in advance."
    override val registerTitle: String = "Create your account"
    override val registerSubtitle: String = "Free to use. We need a way to reach you so we can give you notice " +
        "before the app ever becomes paid — and so your early-user price follows you " +
        "to your next phone."
    override val registerEmail: String = "Email"
    override val registerPhone: String = "Phone"
    override val registerSubmit: String = "Continue"
    override val registerSubmitting: String = "Registering…"
    override val registerInvalidEmail: String = "Please enter a valid email address"
    override val registerInvalidPhone: String = "Please enter a valid phone number"
    override val registerFailed: String = "Registration failed. Please check your connection and try again."
    override val registerPrivacy: String = "We use these only to contact you about the app. Nothing else, no ads."
    override val registerPickNumber: String = "Use my number"
}

val HeStrings: AppStrings = object : AppStrings {
    override val routePlanner: String = "תכנון מסלול"
    override val stationArrivals: String = "הגעות לתחנה"
    override val settings: String = "הגדרות"
    override val opacitySettings: String = "הגדרות שקיפות"
    override val hideOpacitySettings: String = "הסתר הגדרות שקיפות"
    override val sheet: String = "גיליון"
    override val cards: String = "כרטיסים"
    override val from: String = "מוצא"
    override val to: String = "יעד"
    override val swapOriginDestination: String = "החלף מוצא ויעד"
    override val addStop: String = "הוסף עצירה"
    override val stopAlongTheWay: String = "עצירה בדרך"
    override val removeStop: String = "הסר עצירה"
    override val searchRoutes: String = "חפש מסלולים"
    override val editSearch: String = "עריכת החיפוש"
    override val nothingFound: String = "לא נמצאו תוצאות — נסו איות אחר"
    override val searchUnavailable: String = "אין קשר לשרת — החיפוש אינו זמין"
    override val showAllDepartures: (Int) -> String = { n -> "הצגת כל $n היציאות" }
    override val searchingRoutes: String = "מחפש מסלולים..."
    override val retry: String = "נסה שוב"
    override val noRoutesFound: String = "לא נמצאו מסלולים לנסיעה הזו. נסו זמן או יעד אחר."
    override val now: String = "עכשיו"
    override val departAt: String = "יציאה ב"
    override val arriveBy: String = "הגעה עד"
    override val next: String = "הבא"
    override val cancel: String = "ביטול"
    override val ok: String = "אישור"
    override val direct: String = "ישיר"
    override val transferCount: (Int) -> String = { n -> "$n ${if (n > 1) "החלפות" else "החלפה"}" }
    override val walkDescription: (duration: String, destination: String) -> String = { dur, dest -> "הליכה $dur אל $dest" }
    override val transitDescription: (mode: String, route: String, destination: String, duration: String) -> String = { mode, route, dest, dur -> "$mode$route לכיוון $dest — $dur" }
    override val showStops: (Int) -> String = { n -> "הצג $n תחנות" }
    override val hideStops: (Int) -> String = { n -> "הסתר $n תחנות" }
    override val waitFor: (String) -> String = { d -> "המתנה $d" }
    override val walkMode: String = "הליכה"
    override val busMode: String = "אוטובוס"
    override val trainMode: String = "רכבת"
    override val tramMode: String = "רכבת קלה"
    override val subwayMode: String = "מטרו"
    override val ferryMode: String = "מעבורת"
    override val bikeMode: String = "אופניים"
    override val carMode: String = "רכב"
    override val compareTransit: String = "תחב\"צ"
    override val directBikeTitle: String = "מסלול אופניים"
    override val directCarTitle: String = "מסלול ברכב"
    override val directKm: (String) -> String = { km -> "$km ק\"מ" }
    override val directArrive: (String) -> String = { time -> "הגעה ב-$time" }
    override val directBikeNote: String = "דרך רחובות ודרכים הפתוחים לאופניים"
    override val directCarNote: String = "הערכה — ללא עומסי תנועה"
    override val formatDuration: (Long) -> String = { seconds ->
        val mins = seconds / 60
        when {
            mins < 60 -> "$mins דק׳"
            mins % 60 == 0L -> "${mins / 60} שע׳"
            else -> "${mins / 60} שע׳ ${mins % 60} דק׳"
        }
    }
    override val station: String = "תחנה"
    override val updatedAgo: (String) -> String = { ago -> "עודכן $ago" }
    override val justNow: String = "הרגע"
    override val secondsAgo: (Long) -> String = { s -> "לפני $s שנ׳" }
    override val minutesAgo: (Long) -> String = { m -> "לפני $m דק׳" }
    override val filterByLine: String = "סנן לפי קו"
    override val vehicles: String = "כלי רכב"
    override val monitoredVehicles: (Int) -> String = { n -> "כלי רכב במעקב: $n" }
    override val noVehiclesFound: String = "לא נמצאו כלי רכב"
    override val headerLine: String = "קו"
    override val headerDir: String = "כיוון"
    override val headerDest: String = "יעד"
    override val headerArrival: String = "הגעה"
    override val headerDist: String = "מרחק"
    override val vehicleRef: (String) -> String = { ref -> "רכב: $ref" }
    override val distanceMeters: (Int) -> String = { m -> "מרחק: $m מ׳" }
    override val tripDistanceTravelled: (String) -> String = { d -> "נסע $d במסלול הזה" }
    override val fullArrival: (String) -> String = { t -> "הגעה מלאה: $t" }
    override val showOnMap: String = "הצג במפה"
    override val arrivalNow: String = "עכשיו"
    override val arrivalInMin: (Long) -> String = { m -> "בעוד $m דק׳" }
    override val departsAt: (String) -> String = { t -> "יוצא ב-$t" }
    override val departsIn: (String) -> String = { d -> "בעוד $d" }
    override val departsNow: String = "יוצא עכשיו"
    override val departureGone: String = "כבר יצא"
    override val departureTimetable: String = "לפי לוח זמנים"
    override val departureLive: String = "בזמן אמת"
    override val boardingFrom: (String) -> String = { stop -> "מתחנת $stop" }
    override val thenDepartures: (String) -> String = { times -> "אח״כ $times" }
    override val dayTomorrow: String = "מחר"
    override val dayYesterday: String = "אתמול"
    override val formatShortDate: (Int, Int, Int) -> String = { day, month, isoWeekday ->
        // ISO 1=שני … 7=ראשון
        val weekday = listOf("ב׳", "ג׳", "ד׳", "ה׳", "ו׳", "שבת", "א׳")
            .getOrNull(isoWeekday - 1)
        if (weekday != null) "יום $weekday $day.$month" else "$day.$month"
    }
    override val runningLate: (String, String) -> String = { late, scheduled -> "$late באיחור · בלוח $scheduled" }
    override val runningEarly: (String, String) -> String = { early, scheduled -> "$early מוקדם · בלוח $scheduled" }
    override val stopCodeLabel: (String) -> String = { code -> "תחנה $code" }
    override val walkDistance: (Int) -> String = { m -> if (m >= 1000) "${m / 1000}.${(m % 1000) / 100} ק\"מ" else "$m מ׳" }
    override val connectingToServer: String = "...מתחבר לשרת PT"
    override val connectionFailed: String = "החיבור נכשל"
    override val serversTried: (String) -> String = { s -> "שרתים שנוסו: $s" }
    override val noServerReachable: String = "לא ניתן להתחבר לשרת PT"
    override val useCurrentLocation: String = "השתמש במיקום נוכחי"
    override val clear: String = "נקה"
    override val locationIconDot: String = "מיקום: נקודה כחולה"
    override val locationIconPerson: String = "מיקום: דמות"
    override val debugSettings: String = "הגדרות דיבאג"
    override val debugDescription: String = "הגדר מה קורה כאשר כפתור הדיבאג נלחץ."
    override val autoSearchRoutes: String = "חיפוש מסלולים אוטומטי"
    override val expandBottomSheet: String = "הרחב חלונית תחתונה"
    override val save: String = "שמור"
    override val debugFill: String = "מילוי דיבאג"
    override val showPanel: String = "הצג חלונית"
    override val issueClarifier: String = "דיווח על בעיה"
    override val notAvailable: String = "לא זמין"
    override val nextArrival: String = "הגעה הבאה"
    override val followMyLocation: String = "עקוב אחרי המיקום שלי"
    override val mapStyle: String = "סגנון מפה"
    override val mapStyleDark: String = "כהה"
    override val mapStyleLight: String = "בהיר"
    override val mapStyleSatellite: String = "לוויין"
    override val earlier: String = "מוקדם יותר"
    override val later: String = "מאוחר יותר"
    override val fastest: String = "המהיר ביותר"
    override val fewerTransfers: String = "פחות החלפות"
    override val lessWalking: String = "פחות הליכה"
    override val routeOptions: String = "אפשרויות מסלול"
    override val maxWalkLabel: String = "הליכה מקסימלית"
    override val noWalkLimit: String = "ללא הגבלה"
    override val walkMinutesChip: (Int) -> String = { m -> "$m דק׳" }
    override val filteredModesHint: String = "סינון אמצעי נסיעה פעיל — חלק מהמסלולים מוסתרים"
    override val fareEstimate: (String) -> String = { amount -> "~$amount" }
    override val nearbyStops: String = "תחנות קרובות"
    override val noNearbyStops: String = "לא נמצאו תחנות בסביבה"
    override val walkingDistance: (Int) -> String = { m -> "${m} מ׳ הליכה" }
    override val serviceAlerts: String = "התראות שירות"
    override val noAlerts: String = "אין התראות פעילות"
    override val favorites: String = "מועדפים"
    override val favoriteLines: String = "קווים מועדפים"
    override val favoriteStations: String = "תחנות מועדפות"
    override val noFavorites: String = "אין מועדפים עדיין"
    override val addedToFavorites: String = "נוסף למועדפים"
    override val removedFromFavorites: String = "הוסר מהמועדפים"
    override val frequentRoute: String = "מסלול מהיר"
    override val quickRoute: (String, String) -> String = { from, to -> "$from → $to" }
    override val trackBus: String = "עקוב אחרי אוטובוס"
    override val trackingBus: String = "עוקב..."
    override val stopTracking: String = "הפסק מעקב"
    override val busLocationUpdated: String = "מיקום האוטובוס עודכן"
    override val trackingPositionUpdated: (String) -> String = { ago -> "המיקום עודכן $ago" }
    override val trackingVehicleOf: (Int, Int) -> String = { i, n -> "$i / $n" }
    override val trackingOtherVehicle: String = "הצג אוטובוס אחר בקו"
    override val trackingSearching: String = "מחפש את האוטובוס…"
    override val trackingNoMonitoredStop: String = "אין תחנה עם מעקב חי ליד תחנת העלייה"
    override val trackingNoVehicle: String = "אף אוטובוס בקו הזה לא משדר כרגע"
    override val trackingNotStartedYet: (String) -> String = { t -> "האוטובוס שלך עוד לא יצא לדרך — מתוכנן ל-$t" }
    override val trackingError: String = "המיקום החי אינו זמין"
    override val trackingDistanceAway: (String) -> String = { distance -> "במרחק $distance" }
    override val trackingFrameBus: String = "הצג את האוטובוס"
    override val liveBusesNearby: String = "אוטובוסים חיים בסביבה"
    override val liveBusesSearching: String = "מחפש אוטובוסים חיים…"
    override val liveBusesNone: (String) -> String = { radius -> "אין אוטובוסים חיים ברדיוס $radius ממרכז המפה" }
    override val liveBusesZoomIn: (String) -> String = { radius -> "החיפוש מכסה $radius סביב מרכז המפה בלבד — התקרב כדי לכסות את מה שרואים" }
    override val liveBusesOffscreen: (String) -> String = { distance -> "האוטובוס החי הקרוב ביותר במרחק $distance — התרחק כדי לראות אותו" }
    override val accessAccessible: String = "נגיש לכיסא גלגלים"
    override val accessNotAccessible: String = "לא נגיש לכיסא גלגלים"
    override val distanceKm: (String) -> String = { km -> "$km ק״מ" }
    override val distanceM: (Int) -> String = { m -> "$m מ׳" }
    override val departureReminder: String = "תזכורת יציאה"
    override val reminderSet: (String) -> String = { time -> "תזכורת נקבעה ל-$time" }
    override val reminderCancelled: String = "תזכורת בוטלה"
    override val cancelReminder: String = "בטל תזכורת"
    override val reminderNotification: (String, String) -> String = { line, time -> "אוטובוס $line יוצא ב-$time" }
    override val minutesBefore: (Int) -> String = { m -> "$m דק׳ לפני" }
    override val linesBrowser: String = "קווים"
    override val searchLine: String = "חפש מספר קו"
    override val viewLine: String = "הצג"
    override val direction: (String) -> String = { d -> "כיוון $d" }
    override val lineShape: String = "מסלול קו"
    override val noShapeData: String = "אין נתוני מסלול"
    override val timetable: String = "לוח זמנים"
    override val commonLines: String = "קווים נפוצים"
    override val saveAs: String = "שמור בתור"
    override val home: String = "בית"
    override val work: String = "עבודה"
    override val setHome: String = "הגדר בית"
    override val selectedLocation: String = "מיקום נבחר"
    override val startJourney: String = "צא לדרך"
    override val journeyLabel: String = "מסע"
    override val journeyExit: String = "יציאה מהמסע"
    override val journeyWalkTo: (String) -> String = { place -> "לך אל $place" }
    override val journeyWalkToDest: String = "לך אל היעד הסופי"
    override val journeyOnFoot: (String) -> String = { d -> "$d ברגל" }
    override val journeyTake: (String) -> String = { ride -> "עלה על $ride" }
    override val journeyBoardAt: String = "עלה בתחנת"
    override val journeyGetOffAt: String = "רד בתחנת"
    override val journeyStopsCount: (Int) -> String = { n -> if (n == 1) "תחנה אחת" else "$n תחנות" }
    override val journeyUpNext: String = "בהמשך"
    override val journeyArriveDest: String = "הגעה ליעד"
    override val journeyStepOf: (Int, Int) -> String = { n, m -> "שלב $n מתוך $m" }
    override val journeyBack: String = "חזור"
    override val journeyArrive: String = "הגעתי"
    override val journeyDone: String = "סיום"
    override val journeyArrived: String = "הגעת"
    override val journeyYouArrived: String = "הגעת ליעד!"
    override val journeyArrivedSummary: (String, String) -> String = { time, d -> "הגעה ב-$time · $d סה״כ" }
    override val journeyLeave: String = "צא"
    override val journeyDeparts: String = "יציאה"
    override val journeyScheduled: (String) -> String = { time -> "מתוכנן ל-$time" }
    override val dayOverview: String = "סקירת יום"
    override val dayLoading: String = "סורק את כל היום..."
    override val dayFailed: String = "סקירת היום נכשלה"
    override val dayNone: String = "לא נמצאו יציאות ביום זה"
    override val dayFirst: String = "ראשונה"
    override val dayLast: String = "אחרונה"
    override val dayFastest: String = "מהירה"
    override val dayDepartures: String = "יציאות"
    override val dayShowTrip: String = "הצג מסלול"
    override val dayCaption: String = "כל היציאות היום · גובה עמודה = משך הנסיעה (דק') · הקש על עמודה"
    override val dayTruncated: (Int) -> String = { n -> "מוצגות $n היציאות הראשונות של היום" }
    override val departureBoard: String = "לוח יציאות"
    override val pinWidget: String = "הוסף ווידג'ט למסך הבית"
    override val widgetPinUnsupported: String = "מסך הבית שלך לא תומך בהוספת ווידג'טים"
    override val widgetNoDepartures: String = "אין יציאות כרגע"
    override val widgetLoadFailed: String = "העדכון נכשל — הקש על החץ לניסיון נוסף"
    override val boardLive: String = "חי"
    override val boardNow: String = "עכשיו"
    override val boardMinUnit: String = "דק׳"
    override val boardLoading: String = "טוען יציאות..."
    override val boardNone: String = "אין יציאות כרגע"
    override val boardClose: String = "סגור לוח"
    override val boardFilterNote: (String) -> String = { line -> "קו $line בלבד" }
    override val boardStop: (String) -> String = { code -> "תחנה $code" }
    override val timetableCaption: String = "יציאות מתוכננות לפי לוח הזמנים"
    override val allLinesChip: String = "הכל"
    override val timetableTomorrow: String = "מחר"
    override val timetableNone: String = "אין יציאות מתוכננות"
    override val timetableUnavailable: String = "לוח הזמנים אינו זמין לתחנה זו"
    override val departuresTitle: String = "היציאות הקרובות"
    override val liveTag: String = "בזמן אמת"
    override val scheduledTag: String = "מתוכנן"
    override val toDestination: (String) -> String = { dest -> "אל $dest" }
    override val addFavorite: String = "הוסף למועדפים"
    override val removeFavorite: String = "הסר מהמועדפים"
    override val arrivalsFetchError: String = "לא ניתן לטעון את זמני ההגעה"
    override val arrivalsRefreshFailed: String = "הרענון נכשל — מוצגות ההגעות האחרונות שנטענו"
    override val shareTrip: String = "שתף מסע"
    override val scanToOpenTrip: String = "סרוק כדי לפתוח את המסע"
    override val shareLink: String = "שתף קישור"
    override val copyLink: String = "העתק"
    override val linkCopied: String = "הקישור הועתק"
    override val close: String = "סגור"
    override val pricingNoticeTitle: String = "האפליקציה חינם — ויום אחד תהיה בתשלום"
    override val pricingNoticeFree: String = "כל היכולות פתוחות לכולם, בחינם. לעולם לא נבקש כרטיס אשראי."
    override val pricingNoticeFuture: String = "אנחנו אומרים את זה מראש כדי שלא תופתע: בעתיד האפליקציה תהפוך לבתשלום. " +
        "המחיר יהיה קטן — לא יותר ממחיר נסיעה אחת בשנה — ותוכל לבחור בין תשלום שנתי " +
        "לבין תשלום אחד שמכסה עשר שנים."
    override val pricingNoticeWarning: String = "נודיע לך מראש לפני כל שינוי. לא ניגבה ממך כסף בלי התראה."
    override val pricingNoticeFounder: String = "משתמשים שהיו איתנו מההתחלה יקבלו את המחיר הטוב ביותר."
    override val pricingNoticeAcknowledge: String = "הבנתי"
    override val pricingSectionTitle: String = "תמחור"
    override val pricingSectionBody: String = "האפליקציה חינם לכולם כרגע. בעתיד תהפוך לבתשלום — לא יותר ממחיר נסיעה אחת " +
        "בשנה, או תשלום אחד שמכסה עשר שנים, לבחירתך. נודיע לך מראש."
    override val registerTitle: String = "פתיחת חשבון"
    override val registerSubtitle: String = "השימוש חינם. אנחנו צריכים דרך ליצור איתך קשר כדי להודיע לך מראש " +
        "לפני שהאפליקציה תהפוך לבתשלום — וכדי שמחיר המשתמשים הוותיקים יעבור איתך " +
        "גם לטלפון הבא."
    override val registerEmail: String = "אימייל"
    override val registerPhone: String = "טלפון"
    override val registerSubmit: String = "המשך"
    override val registerSubmitting: String = "רושם…"
    override val registerInvalidEmail: String = "נא להזין כתובת אימייל תקינה"
    override val registerInvalidPhone: String = "נא להזין מספר טלפון תקין"
    override val registerFailed: String = "ההרשמה נכשלה. בדוק את החיבור ונסה שוב."
    override val registerPrivacy: String = "נשתמש בפרטים רק כדי ליצור איתך קשר בנוגע לאפליקציה. לא לשום דבר אחר, ובלי פרסומות."
    override val registerPickNumber: String = "המספר שלי"
}

val LocalAppStrings = compositionLocalOf { EnStrings }
