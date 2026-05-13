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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.automatelinux.pt.ui.arrivals.ArrivalsPanel
import com.automatelinux.pt.ui.map.OsmMapView
import com.automatelinux.pt.ui.map.OriginDestinationMarkers
import com.automatelinux.pt.ui.map.RouteOverlay
import com.automatelinux.pt.ui.map.VehicleMarkerOverlay
import com.automatelinux.pt.ui.map.animateToPoint
import com.automatelinux.pt.ui.routing.RoutePlannerPanel
import com.automatelinux.pt.ui.viewmodel.ArrivalsViewModel
import com.automatelinux.pt.ui.viewmodel.RoutingViewModel
import com.automatelinux.pt.util.SettingsStore
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

enum class ActiveTab { ROUTE, ARRIVALS }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    settingsStore: SettingsStore,
    routingViewModel: RoutingViewModel = hiltViewModel(),
    arrivalsViewModel: ArrivalsViewModel = hiltViewModel()
) {
    val routingState by routingViewModel.state.collectAsState()
    val arrivalsState by arrivalsViewModel.state.collectAsState()
    var activeTab by remember { mutableStateOf(ActiveTab.ROUTE) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var swipeDragX by remember { mutableFloatStateOf(0f) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showOpacitySlider by remember { mutableStateOf(false) }
    var sheetOpacity by remember { mutableFloatStateOf(settingsStore.sheetOpacity) }
    var cardOpacity by remember { mutableFloatStateOf(settingsStore.cardOpacity) }

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

    val bottomSheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = false
    )
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = bottomSheetState
    )

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

    Box(modifier = Modifier.fillMaxSize()) {
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = 280.dp,
            sheetContent = {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = activeTab == ActiveTab.ROUTE,
                            onClick = { activeTab = ActiveTab.ROUTE },
                            label = { Text("Route Planner") },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        FilterChip(
                            selected = activeTab == ActiveTab.ARRIVALS,
                            onClick = { activeTab = ActiveTab.ARRIVALS },
                            label = { Text("Station Arrivals") }
                        )
                        Spacer(Modifier.weight(1f))
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "Settings",
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
                                            if (showOpacitySlider) "Hide Opacity Settings"
                                            else "Opacity Settings"
                                        )
                                    },
                                    onClick = {
                                        showOpacitySlider = !showOpacitySlider
                                        menuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .imePadding()
                            .verticalScroll(rememberScrollState())
                    ) {
                        when (activeTab) {
                            ActiveTab.ROUTE -> {
                                RoutePlannerPanel(
                                    state = routingState,
                                    onOriginSelect = { routingViewModel.setOrigin(it) },
                                    onDestinationSelect = { routingViewModel.setDestination(it) },
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
                                    onGeocode = { routingViewModel.geocode(it) },
                                    onGpsClick = onGpsClick,
                                    gpsLoading = gpsLoading,
                                    cardOpacity = cardOpacity
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
                    }
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .width(20.dp)
                            .draggable(
                                state = rememberDraggableState { delta ->
                                    swipeDragX = (swipeDragX + delta).coerceAtLeast(0f)
                                },
                                orientation = Orientation.Horizontal,
                                onDragStarted = { swipeDragX = 0f },
                                onDragStopped = { velocity ->
                                    if (swipeDragX > 200f || velocity > 1000f) {
                                        bottomSheetState.hide()
                                    }
                                    swipeDragX = 0f
                                }
                            ),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .width(4.dp)
                                .height(32.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }
            },
            sheetContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = sheetOpacity)
        ) { _ ->
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

                com.automatelinux.feedbacklib.ui.VersionSnackbar(
                    modifier = Modifier.align(Alignment.TopCenter),
                )

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
                                "Sheet",
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
                                "Cards",
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

                if (bottomSheetState.currentValue == SheetValue.Hidden) {
                    SmallFloatingActionButton(
                        onClick = { scope.launch { bottomSheetState.partialExpand() } },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp),
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = sheetOpacity),
                        elevation = FloatingActionButtonDefaults.elevation(4.dp),
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = "Show panel",
                        )
                    }
                }
            }
        }

        if (BuildConfig.FEEDBACK_ENABLED) {
            SmallFloatingActionButton(
                onClick = {
                    val intent = Intent().setClassName(
                        context.packageName,
                        "com.automatelinux.pt.ui.FeedbackChatActivity"
                    )
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .zIndex(Float.MAX_VALUE)
                    .padding(bottom = 16.dp, end = 12.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                elevation = FloatingActionButtonDefaults.elevation(2.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Chat,
                    contentDescription = "Issue Clarifier",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
