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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

enum class ActiveTab { ROUTE, ARRIVALS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    routingViewModel: RoutingViewModel = hiltViewModel(),
    arrivalsViewModel: ArrivalsViewModel = hiltViewModel()
) {
    val routingState by routingViewModel.state.collectAsState()
    val arrivalsState by arrivalsViewModel.state.collectAsState()
    var activeTab by remember { mutableStateOf(ActiveTab.ROUTE) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    var gpsLoading by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("pt_version", android.content.Context.MODE_PRIVATE)
        val lastVersion = prefs.getString("last_version", null)
        val currentVersion = BuildConfig.VERSION_NAME
        if (lastVersion != currentVersion) {
            prefs.edit().putString("last_version", currentVersion).apply()
            snackbarHostState.showSnackbar(
                message = "Updated to $currentVersion",
                duration = androidx.compose.material3.SnackbarDuration.Indefinite
            )
        }
    }

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
        initialValue = SheetValue.PartiallyExpanded
    )
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = bottomSheetState
    )

    LaunchedEffect(activeTab) {
        com.automatelinux.pt.util.ScreenTracker.currentScreen = when (activeTab) {
            ActiveTab.ROUTE -> "Route Planner"
            ActiveTab.ARRIVALS -> "Station Arrivals"
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

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 280.dp,
        sheetContent = {
            Column {
                // Tab switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
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
                }

                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
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
                                gpsLoading = gpsLoading
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
        },
        sheetContainerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OsmMapView(
                center = GeoPoint(31.77, 35.21),
                zoom = 13.0,
                onMapReady = { map -> mapView = map },
                onLongPress = { point ->
                    // Long press sets destination if origin exists, otherwise sets origin
                    if (routingState.origin == null) {
                        routingViewModel.setOriginFromCoords(point.latitude, point.longitude)
                    } else {
                        routingViewModel.setDestinationFromCoords(point.latitude, point.longitude)
                    }
                }
            ) { map ->
                // Route overlay
                RouteOverlay(
                    map = map,
                    itinerary = routingState.selectedItinerary
                )

                // Origin/destination markers
                OriginDestinationMarkers(
                    map = map,
                    origin = routingState.origin?.let { GeoPoint(it.lat, it.lon) },
                    destination = routingState.destination?.let { GeoPoint(it.lat, it.lon) }
                )

                // Vehicle markers (arrivals tab)
                if (activeTab == ActiveTab.ARRIVALS) {
                    VehicleMarkerOverlay(
                        map = map,
                        markers = arrivalsState.vehicleMarkers,
                        visible = arrivalsState.showVehicleMarkers
                    )
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .clickable { snackbarHostState.currentSnackbarData?.dismiss() }
            )

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
}
