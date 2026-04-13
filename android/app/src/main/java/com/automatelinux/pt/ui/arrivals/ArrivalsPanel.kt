package com.automatelinux.pt.ui.arrivals

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.automatelinux.pt.data.model.StopResult
import com.automatelinux.pt.ui.viewmodel.ArrivalsState

@Composable
fun ArrivalsPanel(
    state: ArrivalsState,
    onStationSelect: (String, String) -> Unit,
    onLineFilterChange: (String) -> Unit,
    onShowVehicleMarkersChange: (Boolean) -> Unit,
    onSearchStops: suspend (String) -> List<StopResult>,
    onVehicleSelect: ((Double, Double) -> Unit)? = null,
    getDestinationName: (String?) -> String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        TransportControls(
            stationCode = state.stationCode,
            stationName = state.stationName,
            onStationSelect = onStationSelect,
            lastUpdated = state.lastUpdated,
            lineFilter = state.lineFilter,
            onLineFilterChange = onLineFilterChange,
            showVehicleMarkers = state.showVehicleMarkers,
            onShowVehicleMarkersChange = onShowVehicleMarkersChange,
            onSearchStops = onSearchStops
        )

        StationArrivals(
            visits = state.visits,
            error = state.error,
            loading = state.loading,
            getDestinationName = getDestinationName,
            onVehicleSelect = onVehicleSelect
        )
    }
}
