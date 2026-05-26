package com.automatelinux.pt.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.automatelinux.pt.BuildConfig
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.automatelinux.feedbacklib.ui.DismissibleSheet
import com.automatelinux.feedbacklib.ui.rememberDismissibleSheetState
import com.automatelinux.pt.ui.arrivals.ArrivalsPanel
import com.automatelinux.pt.ui.components.PreSuggestion
import com.automatelinux.pt.ui.map.OsmMapView
import com.automatelinux.pt.ui.map.OriginDestinationMarkers
import com.automatelinux.pt.ui.map.RouteOverlay
import com.automatelinux.pt.ui.map.VehicleMarkerOverlay
import com.automatelinux.pt.ui.map.animateToPoint
import com.automatelinux.pt.ui.map.fitBounds
import com.automatelinux.pt.util.PolylineDecoder
import com.automatelinux.pt.ui.routing.DebugSettingsDialog
import com.automatelinux.pt.ui.routing.RoutePlannerPanel
import com.automatelinux.pt.ui.viewmodel.ArrivalsViewModel
import com.automatelinux.pt.ui.viewmodel.RoutingViewModel
import com.automatelinux.pt.util.LocalAppStrings
import com.automatelinux.pt.util.SettingsStore
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

enum class ActiveTab { ROUTE, ARRIVALS }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    settingsStore: SettingsStore,
    onLanguageChange: (String) -> Unit,
    routingViewModel: RoutingViewModel = hiltViewModel(),
    arrivalsViewModel: ArrivalsViewModel = hiltViewModel()
) {
    val strings = LocalAppStrings.current
    val routingState by routingViewModel.state.collectAsState()
    val arrivalsState by arrivalsViewModel.state.collectAsState()
    var activeTab by remember { mutableStateOf(ActiveTab.ROUTE) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val sheetScrollState = rememberScrollState()
    var sheetContentHeightPx by remember { mutableIntStateOf(0) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showOpacitySlider by remember { mutableStateOf(false) }
    var showDebugSettings by remember { mutableStateOf(false) }
    var sheetOpacity by remember { mutableFloatStateOf(settingsStore.sheetOpacity) }
    var cardOpacity by remember { mutableFloatStateOf(settingsStore.cardOpacity) }

    var savePlaceTarget by remember { mutableStateOf<com.automatelinux.pt.data.model.GeocodeSuggestion?>(null) }
    var recentSearchesVersion by remember { mutableIntStateOf(0) }

    val preSuggestions = remember(recentSearchesVersion) {
        buildList {
            settingsStore.homePlace?.let {
                add(PreSuggestion(it, Icons.Default.Home, "Home"))
            }
            settingsStore.workPlace?.let {
                add(PreSuggestion(it, Icons.Default.Work, "Work"))
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
    val applyGpsLocation = { location: android.location.Location ->
        gpsLoading = false
        routingViewModel.setOriginFromCoords(location.latitude, location.longitude)
        mapView?.animateToPoint(GeoPoint(location.latitude, location.longitude), 15.0)
    }

    val fetchCurrentLocation = {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            gpsLoading = true
            fusedLocationClient.lastLocation
                .addOnSuccessListener { cached ->
                    if (cached != null) {
                        applyGpsLocation(cached)
                    } else {
                        fusedLocationClient.getCurrentLocation(
                            Priority.PRIORITY_HIGH_ACCURACY,
                            CancellationTokenSource().token
                        ).addOnSuccessListener { fresh ->
                            if (fresh != null) {
                                applyGpsLocation(fresh)
                            } else {
                                gpsLoading = false
                            }
                        }.addOnFailureListener { gpsLoading = false }
                    }
                }
                .addOnFailureListener { gpsLoading = false }
        }
        Unit
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) fetchCurrentLocation()
    }

    val onGpsClick: () -> Unit = {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            fetchCurrentLocation()
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
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
        }
    }

    LaunchedEffect(Unit) {
        if (routingState.origin == null) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                fetchCurrentLocation()
            }
        }
    }

    // Start/stop polling when arrivals tab is active
    DisposableEffect(activeTab) {
        if (activeTab == ActiveTab.ARRIVALS) {
            arrivalsViewModel.startPolling()
        } else {
            arrivalsViewModel.stopPolling()
        }
        onDispose { arrivalsViewModel.stopPolling() }
    }

    // Center map on selected itinerary
    LaunchedEffect(routingState.selectedItinerary) {
        // Map centering is handled by RouteOverlay's fitBounds
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
                    Box(modifier = Modifier.fillMaxWidth()) {
                        BottomSheetDefaults.DragHandle(
                            modifier = Modifier.align(Alignment.Center)
                        )
                        Icon(
                            Icons.Default.BugReport,
                            contentDescription = strings.debugFill,
                            tint = if (routingState.loading) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.38f)
                                   else MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier
                                .align(AbsoluteAlignment.CenterRight)
                                .absolutePadding(right = 12.dp)
                                .size(36.dp)
                                .combinedClickable(
                                    enabled = !routingState.loading,
                                    onClick = {
                                        routingViewModel.debugFill(
                                            autoSearch = settingsStore.debugAutoSearch,
                                            origin = settingsStore.debugFrom,
                                            destination = settingsStore.debugTo
                                        )
                                        if (settingsStore.debugExpandSheet) {
                                            scope.launch { bottomSheetState.expand() }
                                        }
                                    },
                                    onLongClick = { showDebugSettings = true }
                                )
                                .padding(6.dp)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = activeTab == ActiveTab.ROUTE,
                            onClick = { activeTab = ActiveTab.ROUTE },
                            label = { Text(strings.routePlanner) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        FilterChip(
                            selected = activeTab == ActiveTab.ARRIVALS,
                            onClick = { activeTab = ActiveTab.ARRIVALS },
                            label = { Text(strings.stationArrivals) }
                        )
                        Spacer(Modifier.weight(1f))
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = strings.settings,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (showOpacitySlider) strings.hideOpacitySettings
                                            else strings.opacitySettings
                                        )
                                    },
                                    onClick = {
                                        showOpacitySlider = !showOpacitySlider
                                        menuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (settingsStore.language == "he") "English"
                                            else "עברית"
                                        )
                                    },
                                    onClick = {
                                        val newLang = if (settingsStore.language == "he") "en" else "he"
                                        onLanguageChange(newLang)
                                        menuExpanded = false
                                    }
                                )
                            }
                        }
                    }

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
                                    onSwap = { routingViewModel.swapOriginDestination() },
                                    onTimeChange = { routingViewModel.setDepartureTime(it) },
                                    onArriveByChange = { routingViewModel.setArriveBy(it) },
                                    onSearch = {
                                        routingViewModel.search()
                                        scope.launch {
                                            bottomSheetState.expand()
                                        }
                                    },
                                    onSelectItinerary = { routingViewModel.selectItinerary(it) },
                                    onLegClick = { leg ->
                                        val points = if (leg.polyline.isNotBlank()) {
                                            PolylineDecoder.decode(leg.polyline)
                                        } else {
                                            listOf(
                                                GeoPoint(leg.from.lat, leg.from.lon),
                                                GeoPoint(leg.to.lat, leg.to.lon)
                                            )
                                        }
                                        if (points.isNotEmpty()) {
                                            mapView?.fitBounds(points)
                                        }
                                    },
                                    onStopClick = { stop ->
                                        mapView?.animateToPoint(GeoPoint(stop.lat, stop.lon), 17.0)
                                    },
                                    onGeocode = { routingViewModel.geocode(it) },
                                    onGpsClick = onGpsClick,
                                    gpsLoading = gpsLoading,
                                    cardOpacity = cardOpacity,
                                    preSuggestions = preSuggestions,
                                    onLongPressSuggestion = { suggestion ->
                                        savePlaceTarget = suggestion
                                    }
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
                                        mapView?.animateToPoint(GeoPoint(lat, lon), 16.0)
                                        scope.launch { bottomSheetState.partialExpand() }
                                    },
                                    getDestinationName = { arrivalsViewModel.getDestinationName(it) }
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                OsmMapView(
                    center = GeoPoint(31.77, 35.21),
                    zoom = 13.0,
                    onMapReady = { map -> mapView = map },
                    onLongPress = { point ->
                        if (routingState.origin == null) {
                            routingViewModel.setOriginFromCoords(point.latitude, point.longitude)
                        } else {
                            routingViewModel.setDestinationFromCoords(point.latitude, point.longitude)
                        }
                    }
                ) { map ->
                    RouteOverlay(
                        map = map,
                        itinerary = routingState.selectedItinerary
                    )

                    OriginDestinationMarkers(
                        map = map,
                        origin = routingState.origin?.let { GeoPoint(it.lat, it.lon) },
                        destination = routingState.destination?.let { GeoPoint(it.lat, it.lon) }
                    )

                    if (activeTab == ActiveTab.ARRIVALS) {
                        VehicleMarkerOverlay(
                            map = map,
                            markers = arrivalsState.vehicleMarkers,
                            visible = arrivalsState.showVehicleMarkers
                        )
                    }
                }

                if (showOpacitySlider) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp, start = 16.dp, end = 16.dp)
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                strings.sheet,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.width(40.dp)
                            )
                            Slider(
                                value = sheetOpacity,
                                onValueChange = { sheetOpacity = it },
                                onValueChangeFinished = { settingsStore.sheetOpacity = sheetOpacity },
                                valueRange = 0.3f..1f,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${(sheetOpacity * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                strings.cards,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.width(40.dp)
                            )
                            Slider(
                                value = cardOpacity,
                                onValueChange = { cardOpacity = it },
                                onValueChangeFinished = { settingsStore.cardOpacity = cardOpacity },
                                valueRange = 0.2f..1f,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${(cardOpacity * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
            }
        )

        savePlaceTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { savePlaceTarget = null },
                title = { Text("Save as") },
                text = { Text(target.name) },
                confirmButton = {
                    TextButton(onClick = {
                        settingsStore.homePlace = target
                        recentSearchesVersion++
                        savePlaceTarget = null
                    }) { Text("Home") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        settingsStore.workPlace = target
                        recentSearchesVersion++
                        savePlaceTarget = null
                    }) { Text("Work") }
                }
            )
        }

        if (showDebugSettings) {
            DebugSettingsDialog(
                autoSearch = settingsStore.debugAutoSearch,
                expandSheet = settingsStore.debugExpandSheet,
                fromSuggestion = settingsStore.debugFrom,
                toSuggestion = settingsStore.debugTo,
                onConfirm = { autoSearch, expandSheet, from, to ->
                    settingsStore.debugAutoSearch = autoSearch
                    settingsStore.debugExpandSheet = expandSheet
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
