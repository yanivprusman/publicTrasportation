package com.automatelinux.pt.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.automatelinux.pt.BuildConfig
import com.google.android.gms.location.LocationServices
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import com.automatelinux.feedbacklib.ui.DismissibleSheet
import com.automatelinux.feedbacklib.ui.rememberDismissibleSheetState
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.automatelinux.pt.data.model.RouteLeg
import com.automatelinux.pt.data.model.StopResult
import com.automatelinux.pt.data.model.TransitMode
import com.automatelinux.pt.data.model.access
import com.automatelinux.pt.reminder.ReminderScheduler
import com.automatelinux.pt.ui.arrivals.ArrivalsPanel
import com.automatelinux.pt.ui.components.PreSuggestion
import com.automatelinux.pt.ui.lines.LineShapeData
import com.automatelinux.pt.ui.lines.LinesBrowserPanel
import com.automatelinux.pt.map.LatLng
import com.automatelinux.pt.ui.map.MapStyles
import com.automatelinux.pt.ui.map.PtMap
import com.automatelinux.pt.ui.map.PtMapOverlays
import com.automatelinux.pt.ui.map.PtMapState
import com.automatelinux.pt.ui.map.PtMapStyle
import com.automatelinux.pt.ui.map.PtUserLocationIcon
import com.automatelinux.pt.ui.routing.DebugSettingsDialog
import com.automatelinux.pt.ui.routing.JourneyNavigator
import com.automatelinux.pt.ui.routing.RoutePlannerPanel
import com.automatelinux.pt.ui.routing.TrackedBusCard
import android.widget.Toast
import com.automatelinux.pt.ui.viewmodel.ArrivalsViewModel
import com.automatelinux.pt.ui.viewmodel.RoutingViewModel
import com.automatelinux.pt.widget.DeparturesWidgetProvider
import com.automatelinux.pt.util.LocalAppStrings
import com.automatelinux.pt.util.PolylineDecoder
import com.automatelinux.pt.util.formatDistance
import com.automatelinux.pt.util.SettingsStore
import com.automatelinux.pt.util.TripLink
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    settingsStore: SettingsStore,
    onLanguageChange: (String) -> Unit,
    sharedTrip: TripLink.SharedTrip? = null,
    onSharedTripConsumed: () -> Unit = {},
    widgetStation: Pair<String, String>? = null,
    onWidgetStationConsumed: () -> Unit = {},
    // Called after any edit to state the account owns (favourites), so it can
    // be pushed to the server instead of living only on this handset.
    onSyncedStateChanged: () -> Unit = {},
    routingViewModel: RoutingViewModel = koinViewModel(),
    arrivalsViewModel: ArrivalsViewModel = koinViewModel()
) {
    val strings = LocalAppStrings.current
    val routingState by routingViewModel.state.collectAsState()
    val arrivalsState by arrivalsViewModel.state.collectAsState()
    var activeTab by remember { mutableStateOf(ActiveTab.ROUTE) }
    val mapState = remember { PtMapState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val sheetScrollState = rememberScrollState()
    var sheetContentHeightPx by remember { mutableIntStateOf(0) }
    var showOpacitySlider by remember { mutableStateOf(false) }
    var showDebugSettings by remember { mutableStateOf(false) }
    var sheetOpacity by remember { mutableFloatStateOf(settingsStore.sheetOpacity) }
    var cardOpacity by remember { mutableFloatStateOf(settingsStore.cardOpacity) }

    var savePlaceTarget by remember { mutableStateOf<com.automatelinux.pt.data.model.GeocodeSuggestion?>(null) }
    var showSetHomeDialog by remember { mutableStateOf(false) }
    var recentSearchesVersion by remember { mutableIntStateOf(0) }

    var followingLocation by remember { mutableStateOf(false) }
    // "Show me the buses around me", independent of any planned route or chosen
    // stop — the question people open a transit app with when they have not
    // planned anything yet.
    var liveBuses by remember { mutableStateOf(false) }
    var nearbyStops by remember { mutableStateOf<List<StopResult>>(emptyList()) }
    var currentMapZoom by remember { mutableStateOf(13.0) }
    var currentMapCenter by remember { mutableStateOf(PtMapState.DEFAULT_CENTER) }
    // How much ground the map is showing, reported by the map itself. 0 until the first
    // camera callback, which is why the live-buses effect waits for a real value rather
    // than searching a radius nobody measured.
    var currentMapRadiusMeters by remember { mutableStateOf(0.0) }
    var favoriteLines by remember { mutableStateOf(settingsStore.getFavoriteLines()) }
    var favoriteStations by remember { mutableStateOf(settingsStore.getFavoriteStations()) }
    var reminderLegIndex by remember { mutableStateOf<Int?>(null) }
    var reminderJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var selectedLine by remember { mutableStateOf<String?>(null) }
    var lineShapeData by remember { mutableStateOf(LineShapeData()) }
    var journeyMode by remember { mutableStateOf(false) }
    var boardMode by remember { mutableStateOf(false) }
    var shareTripLink by remember { mutableStateOf<String?>(null) }
    var mapStyle by remember { mutableStateOf(settingsStore.mapStyle) }
    var showMapStyleMenu by remember { mutableStateOf(false) }

    val preSuggestions = remember(recentSearchesVersion, strings) {
        buildList {
            settingsStore.homePlace?.let {
                add(PreSuggestion(it, Icons.Default.Home, strings.home))
            }
            settingsStore.workPlace?.let {
                add(PreSuggestion(it, Icons.Default.Work, strings.work))
            }
            settingsStore.getRecentSearches().forEach {
                add(PreSuggestion(it, Icons.Default.History))
            }
        }
    }

    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    var gpsLoading by remember { mutableStateOf(false) }
    var gpsLoadingDestination by remember { mutableStateOf(false) }

    val fetchCurrentLocation = {
        if (LocationHelper.hasPermission(context)) {
            LocationHelper.fetchLocation(
                fusedLocationClient = fusedLocationClient,
                onStart = { gpsLoading = true },
                onLocation = { loc ->
                    gpsLoading = false
                    routingViewModel.setOriginFromCoords(
                        loc.latitude, loc.longitude,
                        placeholder = strings.selectedLocation
                    )
                    mapState.animateTo(LatLng(loc.latitude, loc.longitude), 15.0)
                },
                onFailure = { gpsLoading = false }
            )
        }
    }

    val fetchCurrentLocationForDestination = {
        if (LocationHelper.hasPermission(context)) {
            LocationHelper.fetchLocation(
                fusedLocationClient = fusedLocationClient,
                onStart = { gpsLoadingDestination = true },
                onLocation = { loc ->
                    gpsLoadingDestination = false
                    routingViewModel.setDestinationFromCoords(
                        loc.latitude, loc.longitude,
                        placeholder = strings.selectedLocation
                    )
                    mapState.animateTo(LatLng(loc.latitude, loc.longitude), 15.0)
                },
                onFailure = { gpsLoadingDestination = false }
            )
        }
    }

    val centerMapOnCurrentLocation = {
        if (LocationHelper.hasPermission(context)) {
            LocationHelper.centerOnLocation(fusedLocationClient) { loc ->
                mapState.animateTo(LatLng(loc.latitude, loc.longitude), 15.0)
            }
        }
    }

    // Follow-my-location: subscribe to continuous location updates while enabled and
    // keep the map centered. onUserPan (real touch only) flips followingLocation off.
    DisposableEffect(followingLocation) {
        if (!followingLocation || !LocationHelper.hasPermission(context)) {
            return@DisposableEffect onDispose {}
        }
        val callback = LocationHelper.startFollowing(fusedLocationClient) { loc ->
            mapState.animateTo(LatLng(loc.latitude, loc.longitude))
        }
        onDispose { LocationHelper.stopFollowing(fusedLocationClient, callback) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) fetchCurrentLocation()
    }

    val onGpsClick: () -> Unit = {
        if (LocationHelper.hasPermission(context)) fetchCurrentLocation()
        else permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val onGpsClickDestination: () -> Unit = {
        if (LocationHelper.hasPermission(context)) fetchCurrentLocationForDestination()
        else permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // Departure reminder: exact AlarmManager alarm + system notification, so it fires
    // even if the app is killed. reminderLegIndex/reminderJob only drive the button UI.
    val armReminder: (RouteLeg) -> Unit = { leg ->
        val legIdx = routingState.selectedItinerary?.legs?.indexOf(leg) ?: -1
        if (legIdx >= 0) {
            try {
                val departTime = Instant.parse(leg.startTime)
                val triggerAt = departTime.minus(5, DateTimeUnit.MINUTE).toEpochMilliseconds()
                val lineName = leg.routeShortName ?: strings.busMode
                val timeStr = departTime.toLocalDateTime(TimeZone.currentSystemDefault())
                    .time.toString().take(5)
                ReminderScheduler.schedule(
                    context,
                    triggerAtMillis = triggerAt,
                    title = strings.departureReminder,
                    text = strings.reminderNotification(lineName, timeStr),
                    channelName = strings.departureReminder
                )
                reminderLegIndex = legIdx
                reminderJob?.cancel()
                reminderJob = scope.launch {
                    val delayMs = triggerAt - Clock.System.now().toEpochMilliseconds()
                    if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
                    reminderLegIndex = null
                }
            } catch (_: Exception) {
                // Unparseable departure time — leave the button unarmed.
            }
        }
    }

    var pendingReminderLeg by remember { mutableStateOf<RouteLeg?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val leg = pendingReminderLeg
        pendingReminderLeg = null
        if (granted && leg != null) armReminder(leg)
    }

    val setDepartureReminder: (RouteLeg) -> Unit = { leg ->
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            pendingReminderLeg = leg
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            armReminder(leg)
        }
    }

    var locationIconStyle by remember { mutableStateOf(settingsStore.locationIconStyle) }


    LaunchedEffect(currentMapCenter, currentMapZoom) {
        if (currentMapZoom >= 14.5) {
            kotlinx.coroutines.delay(300)
            val stops = arrivalsViewModel.fetchNearbyStops(
                currentMapCenter.latitude,
                currentMapCenter.longitude,
                500
            )
            nearbyStops = stops
        } else if (nearbyStops.isNotEmpty()) {
            kotlinx.coroutines.delay(300)
            nearbyStops = emptyList()
        }
    }

    LaunchedEffect(liveBuses, currentMapCenter, currentMapRadiusMeters) {
        if (!liveBuses) {
            arrivalsViewModel.stopNearbyVehicles()
            return@LaunchedEffect
        }
        // Zooming changes the radius, so this now restarts on zoom too — debounced so a
        // pinch or a pan does not fire a burst of stop queries on the way.
        kotlinx.coroutines.delay(400)
        arrivalsViewModel.startNearbyVehicles(
            currentMapCenter.latitude,
            currentMapCenter.longitude,
            currentMapRadiusMeters
        )
    }

    val sheetState = rememberDismissibleSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = false,
    )
    val bottomSheetState = sheetState.bottomSheetState

    val imeVisible = WindowInsets.isImeVisible
    var expandedByIme by remember { mutableStateOf(false) }

    LaunchedEffect(imeVisible) {
        if (imeVisible) {
            if (bottomSheetState.currentValue != SheetValue.Expanded) {
                expandedByIme = true
                bottomSheetState.expand()
            }
        } else if (expandedByIme) {
            expandedByIme = false
            bottomSheetState.partialExpand()
        }
    }

    LaunchedEffect(bottomSheetState.currentValue) {
        if (bottomSheetState.currentValue != SheetValue.Expanded && imeVisible) {
            keyboardController?.hide()
        }
    }

    LaunchedEffect(activeTab) {
        com.automatelinux.pt.util.ScreenTracker.currentScreen = when (activeTab) {
            ActiveTab.ROUTE -> "Route Planner"
            ActiveTab.ARRIVALS -> "Station Arrivals"
            ActiveTab.LINES -> "Lines"
        }
    }

    LaunchedEffect(routingState.origin) {
        routingState.origin?.let { origin ->
            mapState.animateTo(LatLng(origin.lat, origin.lon), 15.0)
        }
    }

    // "Where is my bus" is a question about two points, so both are framed: the
    // vehicle and the stop you are waiting at. Keyed on the vehicle rather than its
    // position, so the camera settles once per bus instead of lurching every poll.
    val frameTrackedBus: () -> Unit = {
        val tracked = routingState.trackedBus
        val bus = tracked?.marker
        if (bus != null && (tracked.stopLat != 0.0 || tracked.stopLon != 0.0)) {
            mapState.fitBounds(
                listOf(LatLng(bus.lat, bus.lon), LatLng(tracked.stopLat, tracked.stopLon)),
                padding = 90
            )
        }
    }
    LaunchedEffect(routingState.trackedBus?.marker?.vehicleRef) { frameTrackedBus() }

    // Tracking is a screen, not a badge: the sheet goes away so the map is the whole
    // view, and comes back exactly as it was when tracking ends.
    val isTracking = routingState.trackedBus != null
    LaunchedEffect(isTracking) {
        if (isTracking) {
            bottomSheetState.hide()
        } else if (bottomSheetState.currentValue == SheetValue.Hidden) {
            bottomSheetState.partialExpand()
        }
    }

    LaunchedEffect(Unit) {
        // A trip arriving via a shared link supplies the origin — GPS must not overwrite it.
        if (routingState.origin == null && sharedTrip == null) {
            if (LocationHelper.hasPermission(context)) {
                fetchCurrentLocation()
            }
        }
    }

    // A tap on a home-screen departures widget lands here with its station.
    LaunchedEffect(widgetStation) {
        if (widgetStation != null) {
            activeTab = ActiveTab.ARRIVALS
            arrivalsViewModel.setStationCode(widgetStation.first, widgetStation.second)
            bottomSheetState.expand()
            onWidgetStationConsumed()
        }
    }

    LaunchedEffect(sharedTrip) {
        if (sharedTrip != null) {
            activeTab = ActiveTab.ROUTE
            routingViewModel.setOrigin(sharedTrip.origin)
            routingViewModel.setDestination(sharedTrip.destination)
            routingViewModel.setDepartureTime(sharedTrip.departureTime)
            routingViewModel.setArriveBy(sharedTrip.arriveBy)
            routingViewModel.search()
            bottomSheetState.expand()
            onSharedTripConsumed()
        }
    }

    // Location-first arrivals: opening the tab selects the stop the user is
    // standing at (favorites within 150m win) unless they explicitly picked a
    // station, and fills the GPS quick-switch chips.
    LaunchedEffect(activeTab) {
        if (activeTab != ActiveTab.ARRIVALS) return@LaunchedEffect
        if (!LocationHelper.hasPermission(context)) return@LaunchedEffect
        LocationHelper.fetchLocation(
            fusedLocationClient = fusedLocationClient,
            onLocation = { loc ->
                arrivalsViewModel.autoSelectNearestStation(
                    loc.latitude,
                    loc.longitude,
                    favoriteStations.map { it.first }.toSet()
                )
            }
        )
    }

    // Poll only while the Arrivals tab is showing AND the app is foregrounded —
    // otherwise a backgrounded app keeps hitting the SIRI endpoint every 15s.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(activeTab, lifecycleOwner) {
        if (activeTab != ActiveTab.ARRIVALS) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            arrivalsViewModel.startPolling()
            try {
                awaitCancellation()
            } finally {
                arrivalsViewModel.stopPolling()
            }
        }
    }

    // Tracking polls on the same terms, and for the same reason: a live vehicle
    // position is only worth fetching while somebody can see it. What is being
    // tracked survives the pause — only the requests stop.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            routingViewModel.resumeTracking()
            try {
                awaitCancellation()
            } finally {
                routingViewModel.pauseTracking()
            }
        }
    }

    com.automatelinux.feedbacklib.ui.FeedbackOverlay(
        modifier = Modifier.fillMaxSize(),
        showFab = BuildConfig.FEEDBACK_ENABLED,
    ) {
        DismissibleSheet(
            state = sheetState,
            peekHeight = 280.dp,
            sheetOpacity = sheetOpacity,
            sheetContent = {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { sheetContentHeightPx = it.size.height },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SheetDragHandleRow(
                        strings = strings,
                        loading = routingState.loading,
                        sheetOpacity = sheetOpacity,
                        onSheetOpacityChange = { sheetOpacity = it },
                        onSheetOpacityFinished = { settingsStore.sheetOpacity = sheetOpacity },
                        onDebugFill = {
                            routingViewModel.debugFill(
                                autoSearch = settingsStore.debugAutoSearch,
                                origin = settingsStore.debugFrom,
                                destination = settingsStore.debugTo
                            )
                            if (settingsStore.debugExpandSheet) {
                                scope.launch { bottomSheetState.expand() }
                            }
                        },
                        onDebugLongClick = { showDebugSettings = true },
                        showDebugFill = BuildConfig.FEEDBACK_ENABLED
                    )
                    SheetTabRow(
                        activeTab = activeTab,
                        onTabChange = { activeTab = it },
                        strings = strings,
                        showOpacitySlider = showOpacitySlider,
                        onToggleOpacitySlider = { showOpacitySlider = !showOpacitySlider },
                        locationIconStyle = locationIconStyle,
                        onLocationIconStyleChange = { newStyle ->
                            settingsStore.locationIconStyle = newStyle
                            locationIconStyle = newStyle
                        },
                        language = settingsStore.language,
                        onLanguageChange = onLanguageChange
                    )

                    Column(
                        modifier = Modifier
                            .imePadding()
                            .verticalScroll(sheetScrollState)
                    ) {
                        when (activeTab) {
                            ActiveTab.ROUTE -> {
                                RoutePlannerPanel(
                                    state = routingState,
                                    onOriginSelect = { s ->
                                        routingViewModel.setOrigin(s)
                                        if (s != null) {
                                            settingsStore.addRecentSearch(s)
                                            recentSearchesVersion++
                                        }
                                    },
                                    onDestinationSelect = { s ->
                                        routingViewModel.setDestination(s)
                                        if (s != null) {
                                            settingsStore.addRecentSearch(s)
                                            recentSearchesVersion++
                                        }
                                    },
                                    onViaSelect = { s ->
                                        routingViewModel.setVia(s)
                                        if (s != null) {
                                            settingsStore.addRecentSearch(s)
                                            recentSearchesVersion++
                                        }
                                    },
                                    onShowViaField = { routingViewModel.showViaField() },
                                    onRemoveVia = { routingViewModel.removeVia() },
                                    onSwap = { routingViewModel.swapOriginDestination() },
                                    onTimeChange = { routingViewModel.setDepartureTime(it) },
                                    onArriveByChange = { routingViewModel.setArriveBy(it) },
                                    onSearch = {
                                        routingViewModel.search()
                                        scope.launch { bottomSheetState.expand() }
                                    },
                                    onSelectItinerary = { routingViewModel.selectItinerary(it) },
                                    onLegClick = { leg ->
                                        val points = if (leg.polyline.isNotBlank()) {
                                            PolylineDecoder.decode(leg.polyline)
                                        } else {
                                            listOf(
                                                LatLng(leg.from.lat, leg.from.lon),
                                                LatLng(leg.to.lat, leg.to.lon)
                                            )
                                        }
                                        if (points.isNotEmpty()) {
                                            mapState.fitBounds(points)
                                        }
                                    },
                                    onStopClick = { stop ->
                                        mapState.animateTo(LatLng(stop.lat, stop.lon), 17.0)
                                    },
                                    onGeocode = { routingViewModel.geocode(it) },
                                    onGpsClick = onGpsClick,
                                    gpsLoading = gpsLoading,
                                    onGpsClickDestination = onGpsClickDestination,
                                    gpsLoadingDestination = gpsLoadingDestination,
                                    cardOpacity = cardOpacity,
                                    preSuggestions = preSuggestions,
                                    onLongPressSuggestion = { suggestion ->
                                        savePlaceTarget = suggestion
                                    },
                                    sortMode = routingState.sortMode,
                                    onSortChange = { routingViewModel.setSortMode(it) },
                                    onTravelModeChange = { routingViewModel.setTravelMode(it) },
                                    onToggleModeFilter = { routingViewModel.toggleModeFilter(it) },
                                    onMaxWalkChange = { routingViewModel.setMaxWalk(it) },
                                    onEarlier = { routingViewModel.searchEarlier() },
                                    onLater = { routingViewModel.searchLater() },
                                    homePlace = settingsStore.homePlace,
                                    workPlace = settingsStore.workPlace,
                                    onQuickRoute = { home, work ->
                                        routingViewModel.setOrigin(home)
                                        routingViewModel.setDestination(work)
                                        routingViewModel.search()
                                        scope.launch { bottomSheetState.expand() }
                                    },
                                    onQuickDestination = { place ->
                                        routingViewModel.setDestination(place)
                                        if (routingState.origin != null) {
                                            routingViewModel.search()
                                            scope.launch { bottomSheetState.expand() }
                                        }
                                    },
                                    onSavePlace = { savePlaceTarget = it },
                                    onSetHome = { showSetHomeDialog = true },
                                    onTrackBus = { legIndex, leg ->
                                        if (routingState.trackedBus?.legIndex == legIndex) {
                                            routingViewModel.stopTracking()
                                        } else {
                                            val lineName = leg.routeShortName ?: return@RoutePlannerPanel
                                            routingViewModel.trackBusOnLeg(
                                                legIndex = legIndex,
                                                lat = leg.from.lat,
                                                lon = leg.from.lon,
                                                lineName = lineName,
                                                access = leg.access,
                                                destination = leg.to.name,
                                                tripId = leg.tripId
                                            )
                                        }
                                    },
                                    trackedLegIndex = routingState.trackedBus?.legIndex,
                                    onSetReminder = setDepartureReminder,
                                    activeReminderLegIndex = reminderLegIndex,
                                    onCancelReminder = {
                                        ReminderScheduler.cancel(context)
                                        reminderJob?.cancel()
                                        reminderJob = null
                                        reminderLegIndex = null
                                    },
                                    onStartJourney = {
                                        keyboardController?.hide()
                                        journeyMode = true
                                    },
                                    onShareTrip = {
                                        val origin = routingState.origin
                                        val destination = routingState.destination
                                        if (origin != null && destination != null) {
                                            keyboardController?.hide()
                                            shareTripLink = TripLink.build(
                                                origin = origin,
                                                destination = destination,
                                                departureTime = routingState.departureTime,
                                                arriveBy = routingState.arriveBy,
                                                // Credits this user if the recipient installs.
                                                // Null until the first launch ping has run —
                                                // in that case the link simply carries no ref
                                                // rather than inventing an attribution.
                                                referrerInstallId = settingsStore.installId
                                            )
                                        }
                                    },
                                    onToggleDayOverview = {
                                        keyboardController?.hide()
                                        routingViewModel.toggleDayOverview()
                                        scope.launch { bottomSheetState.expand() }
                                    },
                                    onSelectDayDeparture = { routingViewModel.selectDayDeparture(it) },
                                    onPickDayDeparture = { routingViewModel.pickDayDeparture(it) },
                                    onRetryDayOverview = { routingViewModel.loadDayOverview() }
                                )
                            }

                            ActiveTab.LINES -> {
                                LinesBrowserPanel(
                                    selectedLine = selectedLine,
                                    lineShapeData = lineShapeData,
                                    onSelectLine = { line ->
                                        selectedLine = line
                                        lineShapeData = LineShapeData(loading = true)
                                        scope.launch {
                                            try {
                                                val shape = routingViewModel.getLineShape(line)
                                                lineShapeData = LineShapeData(directions = shape)
                                            } catch (e: Exception) {
                                                lineShapeData = LineShapeData(error = e.message ?: "Failed to load")
                                            }
                                        }
                                    },
                                    favoriteLines = favoriteLines
                                )
                            }

                            ActiveTab.ARRIVALS -> {
                                ArrivalsPanel(
                                    state = arrivalsState,
                                    onStationSelect = { code, name ->
                                        arrivalsViewModel.setStationCode(code, name)
                                    },
                                    onLineFilterChange = { arrivalsViewModel.setLineFilter(it) },
                                    onShowVehicleMarkersChange = { arrivalsViewModel.setShowVehicleMarkers(it) },
                                    onSearchStops = { arrivalsViewModel.searchStops(it) },
                                    onVehicleSelect = { lat, lon ->
                                        mapState.animateTo(LatLng(lat, lon), 16.0)
                                        scope.launch { bottomSheetState.partialExpand() }
                                    },
                                    // "Where is the bus coming to my stop" reaches the
                                    // tracking screen straight from the arrivals list,
                                    // without planning a journey to get there.
                                    onTrackVehicle = { line, dest, routeId, vehicleRef ->
                                        keyboardController?.hide()
                                        routingViewModel.trackBusAtStop(
                                            stationCode = arrivalsState.stationCode,
                                            lineName = line,
                                            destination = dest,
                                            routeId = routeId,
                                            vehicleRef = vehicleRef
                                        )
                                    },
                                    getDestinationName = { arrivalsViewModel.getDestinationName(it) },
                                    nearbyStops = nearbyStops,
                                    gpsNearbyStops = arrivalsState.gpsNearbyStops,
                                    favoriteLines = favoriteLines,
                                    onToggleFavoriteLine = { line ->
                                        settingsStore.toggleFavoriteLine(line)
                                        favoriteLines = settingsStore.getFavoriteLines()
                                        onSyncedStateChanged()
                                    },
                                    favoriteStations = favoriteStations,
                                    isStationFavorite = arrivalsState.stationCode.isNotEmpty() &&
                                        settingsStore.isStationFavorite(arrivalsState.stationCode),
                                    onToggleFavoriteStation = {
                                        if (arrivalsState.stationCode.isNotEmpty()) {
                                            settingsStore.toggleFavoriteStation(
                                                arrivalsState.stationCode,
                                                arrivalsState.stationName
                                            )
                                            favoriteStations = settingsStore.getFavoriteStations()
                                            onSyncedStateChanged()
                                        }
                                    },
                                    onOpenBoard = {
                                        keyboardController?.hide()
                                        boardMode = true
                                    },
                                    onPinWidget = {
                                        val pinned = DeparturesWidgetProvider.requestPin(
                                            context,
                                            arrivalsState.stationCode,
                                            arrivalsState.stationName
                                        )
                                        if (!pinned) {
                                            Toast.makeText(
                                                context,
                                                strings.widgetPinUnsupported,
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    onRetry = { arrivalsViewModel.fetchArrivals() }
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .offset {
                                val offset = try { bottomSheetState.requireOffset() } catch (_: Exception) { 0f }
                                val visibleHeight = sheetContentHeightPx - offset
                                val centerY = (visibleHeight / 2f - 16.dp.toPx()).toInt()
                                IntOffset(4.dp.roundToPx(), maxOf(0, centerY))
                            }
                            .width(4.dp)
                            .height(32.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
            },
            content = { _ ->
            Box(modifier = Modifier.fillMaxSize()) {
                PtMap(
                    state = mapState,
                    style = PtMapStyle.fromStored(mapStyle),
                    overlays = PtMapOverlays(
                        itinerary = routingState.displayedItinerary,
                        origin = routingState.origin?.let { LatLng(it.lat, it.lon) },
                        destination = routingState.destination?.let { LatLng(it.lat, it.lon) },
                        via = routingState.via?.let { LatLng(it.lat, it.lon) },
                        // Vehicles and line shapes belong to their own tabs; drawing them
                        // everywhere would clutter a map being used for something else.
                        vehicles = when {
                            liveBuses -> arrivalsState.nearbyVehicles
                            activeTab == ActiveTab.ARRIVALS -> arrivalsState.vehicleMarkers
                            else -> emptyList()
                        },
                        vehiclesVisible = liveBuses || arrivalsState.showVehicleMarkers,
                        stops = nearbyStops,
                        // Tracking wins over the Lines tab: a bus can be reported far
                        // up the road, well off the stretch of the itinerary you ride,
                        // so its own line is what makes the marker mean anything.
                        // While tracking, the line is this trip's real geometry, keyed
                        // by trip id — not the line number, which repeats nationwide.
                        lineShape = routingState.trackedBus?.shape
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { mapOf("trip" to it) }
                            ?: if (activeTab == ActiveTab.LINES) lineShapeData.directions else null,
                        lineShapeFitsCamera = routingState.trackedBus?.shape?.isEmpty() != false,
                        trackedBus = routingState.trackedBus?.marker,
                        // The map never prompts; it only draws what permission already allows.
                        showUserLocation = LocationHelper.hasPermission(context),
                        userLocationIcon = if (locationIconStyle == "dot") {
                            PtUserLocationIcon.DOT
                        } else {
                            PtUserLocationIcon.PLATFORM_DEFAULT
                        }
                    ),
                    onLongPress = { point ->
                        if (routingState.origin == null) {
                            routingViewModel.setOriginFromCoords(
                                point.latitude, point.longitude,
                                placeholder = strings.selectedLocation
                            )
                        } else {
                            routingViewModel.setDestinationFromCoords(
                                point.latitude, point.longitude,
                                placeholder = strings.selectedLocation
                            )
                        }
                    },
                    onUserPan = { followingLocation = false },
                    onCameraChanged = { viewport ->
                        currentMapCenter = viewport.center
                        currentMapZoom = viewport.zoom
                        currentMapRadiusMeters = viewport.visibleRadiusMeters
                    },
                    onStopTap = { stop ->
                        activeTab = ActiveTab.ARRIVALS
                        arrivalsViewModel.setStationCode(stop.stopCode, stop.stopName)
                        scope.launch { bottomSheetState.expand() }
                    },
                    // A bus on the map is the answer to "where is it" only once you
                    // can ask it for more; tapping opens the same tracking screen.
                    onVehicleTap = { marker ->
                        val stopCode = marker.stopCode
                        if (stopCode != null) {
                            routingViewModel.trackBusAtStop(
                                stationCode = stopCode,
                                lineName = marker.lineNumber,
                                destination = arrivalsViewModel.getDestinationName(marker.destinationRef),
                                routeId = marker.lineRef,
                                vehicleRef = marker.vehicleRef
                            )
                        }
                    }
                )

                // Tracking takes the screen rather than perching on it: the sheet steps
                // aside (below), the map is the whole view, and the card sits at the
                // bottom where a thumb already is.
                routingState.trackedBus?.let { tracked ->
                    TrackedBusCard(
                        tracked = tracked,
                        onSelectVehicle = { routingViewModel.selectTrackedVehicle(it) },
                        onClose = { routingViewModel.stopTracking() },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 12.dp, end = 12.dp, bottom = 24.dp)
                    )
                }

                // An empty map is not an answer. Live buses can legitimately report
                // nothing, but nothing-because-the-area-is-quiet and
                // nothing-because-the-search-is-a-dot-in-your-view are different facts
                // and the user cannot tell them apart by looking. Two causes, two
                // sentences — and silence while the first poll is still in flight,
                // because "no buses" before asking would be a guess.
                if (liveBuses) {
                    val searchedMeters = arrivalsState.nearbyVehiclesRadiusMeters
                    val liveBusesHint = when {
                        !arrivalsState.nearbyVehiclesLoaded -> strings.liveBusesSearching
                        arrivalsState.nearbyVehicles.isNotEmpty() -> null
                        // The view covers half again more ground than was searched, so
                        // the emptiness says nothing about most of what is on screen.
                        currentMapRadiusMeters > searchedMeters * 1.5 ->
                            strings.liveBusesZoomIn(formatDistance(searchedMeters, strings))
                        else -> strings.liveBusesNone(formatDistance(searchedMeters, strings))
                    }
                    liveBusesHint?.let { hint ->
                        Surface(
                            // Start-aligned against the end-aligned button column, so the
                            // two swap sides together under RTL and never collide; the end
                            // padding keeps a long sentence off the buttons.
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(top = 48.dp, start = 12.dp, end = 76.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            shape = RoundedCornerShape(20.dp),
                            shadowElevation = 4.dp
                        ) {
                            Text(
                                text = hint,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 48.dp, end = 12.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Always here, whatever the sheet is doing and whether or not a
                    // route is planned: the plainest form of "where are the buses".
                    SmallFloatingActionButton(
                        onClick = { liveBuses = !liveBuses },
                        containerColor = if (liveBuses) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        contentColor = if (liveBuses) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        elevation = FloatingActionButtonDefaults.elevation(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Sensors,
                            contentDescription = strings.liveBusesNearby,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Picking a route already says which bus you care about. Until
                    // now that intent went nowhere: the only Track control lived
                    // inside the itinerary's leg detail, so with the sheet closed —
                    // the state you actually read the map in — there was no way to
                    // ask where your bus was. This button is on the map, so it is
                    // there whatever the sheet is doing.
                    val trackableLeg = remember(routingState.displayedItinerary) {
                        routingState.displayedItinerary?.legs
                            ?.withIndex()
                            ?.firstOrNull { (_, leg) ->
                                leg.mode != TransitMode.WALK && leg.routeShortName != null
                            }
                    }
                    if (routingState.trackedBus == null && trackableLeg != null) {
                        val (legIndex, leg) = trackableLeg
                        SmallFloatingActionButton(
                            onClick = {
                                routingViewModel.trackBusOnLeg(
                                    legIndex = legIndex,
                                    lat = leg.from.lat,
                                    lon = leg.from.lon,
                                    lineName = leg.routeShortName ?: return@SmallFloatingActionButton,
                                    access = leg.access,
                                    destination = leg.to.name,
                                    tripId = leg.tripId
                                )
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            elevation = FloatingActionButtonDefaults.elevation(4.dp)
                        ) {
                            Icon(
                                Icons.Default.DirectionsBus,
                                contentDescription = strings.trackBus,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Panning away from the bus is normal — you look ahead down the
                    // road — so getting back to it has to be one tap, not a re-track.
                    if (routingState.trackedBus?.marker != null) {
                        SmallFloatingActionButton(
                            onClick = frameTrackedBus,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
                            elevation = FloatingActionButtonDefaults.elevation(4.dp)
                        ) {
                            Icon(
                                Icons.Default.DirectionsBus,
                                contentDescription = strings.trackingFrameBus,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    SmallFloatingActionButton(
                        onClick = {
                            if (LocationHelper.hasPermission(context)) {
                                followingLocation = !followingLocation
                                if (followingLocation) {
                                    centerMapOnCurrentLocation()
                                }
                            } else {
                                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        },
                        containerColor = if (followingLocation)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surface,
                        contentColor = if (followingLocation)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.primary,
                        elevation = FloatingActionButtonDefaults.elevation(4.dp)
                    ) {
                        Icon(
                            Icons.Default.MyLocation,
                            contentDescription = strings.followMyLocation,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Box {
                        SmallFloatingActionButton(
                            onClick = { showMapStyleMenu = true },
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
                            elevation = FloatingActionButtonDefaults.elevation(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Layers,
                                contentDescription = strings.mapStyle,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMapStyleMenu,
                            onDismissRequest = { showMapStyleMenu = false }
                        ) {
                            val styleOptions = listOf(
                                Triple(MapStyles.DARK, Icons.Default.DarkMode, strings.mapStyleDark),
                                Triple(MapStyles.LIGHT, Icons.Default.LightMode, strings.mapStyleLight),
                                Triple(MapStyles.SATELLITE, Icons.Default.SatelliteAlt, strings.mapStyleSatellite)
                            )
                            for ((style, icon, label) in styleOptions) {
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    leadingIcon = { Icon(icon, contentDescription = null) },
                                    trailingIcon = if (mapStyle == style) {
                                        {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    } else null,
                                    onClick = {
                                        settingsStore.mapStyle = style
                                        mapStyle = style
                                        showMapStyleMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (showOpacitySlider) {
                    OpacityControls(
                        strings = strings,
                        sheetOpacity = sheetOpacity,
                        onSheetOpacityChange = { sheetOpacity = it },
                        onSheetOpacityFinished = { settingsStore.sheetOpacity = sheetOpacity },
                        cardOpacity = cardOpacity,
                        onCardOpacityChange = { cardOpacity = it },
                        onCardOpacityFinished = { settingsStore.cardOpacity = cardOpacity },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp, start = 16.dp, end = 16.dp)
                    )
                }
            }
            }
        )

        val journeyItinerary = routingState.selectedItinerary
        LaunchedEffect(journeyItinerary) {
            if (journeyItinerary == null) journeyMode = false
        }
        if (journeyMode && journeyItinerary != null) {
            JourneyNavigator(
                itinerary = journeyItinerary,
                onClose = { journeyMode = false }
            )
        }

        if (boardMode) {
            com.automatelinux.pt.ui.arrivals.DepartureBoardScreen(
                state = arrivalsState,
                getDestinationName = { arrivalsViewModel.getDestinationName(it) },
                onClose = { boardMode = false }
            )
        }

        shareTripLink?.let { link ->
            val origin = routingState.origin
            val destination = routingState.destination
            if (origin != null && destination != null) {
                com.automatelinux.pt.ui.routing.ShareTripDialog(
                    origin = origin,
                    destination = destination,
                    link = link,
                    onDismiss = { shareTripLink = null }
                )
            }
        }

        if (showSetHomeDialog) {
            com.automatelinux.pt.ui.SetHomeDialog(
                onGeocode = { routingViewModel.geocode(it) },
                onSave = { place ->
                    settingsStore.homePlace = place
                    recentSearchesVersion++
                },
                onDismiss = { showSetHomeDialog = false }
            )
        }

        savePlaceTarget?.let { target ->
            SavePlaceDialog(
                target = target,
                onSaveHome = {
                    settingsStore.homePlace = target
                    recentSearchesVersion++
                    savePlaceTarget = null
                },
                onSaveWork = {
                    settingsStore.workPlace = target
                    recentSearchesVersion++
                    savePlaceTarget = null
                },
                onDismiss = { savePlaceTarget = null }
            )
        }

        if (showDebugSettings) {
            DebugSettingsDialog(
                autoSearch = settingsStore.debugAutoSearch,
                expandSheet = settingsStore.debugExpandSheet,
                locationIconStyle = settingsStore.locationIconStyle,
                fromSuggestion = settingsStore.debugFrom,
                toSuggestion = settingsStore.debugTo,
                onConfirm = { autoSearch, expandSheet, iconStyle, from, to ->
                    settingsStore.debugAutoSearch = autoSearch
                    settingsStore.debugExpandSheet = expandSheet
                    settingsStore.locationIconStyle = iconStyle
                    locationIconStyle = iconStyle
                    settingsStore.debugFrom = from
                    settingsStore.debugTo = to
                    showDebugSettings = false
                },
                onDismiss = { showDebugSettings = false },
                onGeocode = { routingViewModel.geocode(it) }
            )
        }
    }
}
