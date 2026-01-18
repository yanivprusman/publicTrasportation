import React from 'react';
import './App.css';
import MapView from './MapView';
import StationArrivals from './components/data-display/StationArrivals';
import TransportControls from './components/controls/TransportControls';
import { useTransport } from './contexts/TransportContext';

function CombinedTransportApp() {
  const {
    siriData,
    loading,
    error,
    stationCode,
    mapCenter,
    vehicleMarkers,
    showVehicleMarkers,
    routeShape,
    stops,
    destination,
    defaultStartingPoint,
    defaultDestination,
    calculateRoute,
    setCalculateRoute,
    setDestination
  } = useTransport();

  return (
    <div className="combined-app">
      <div className="app-header" style={{ position: 'relative' }}>
        <h1>TEST CHANGE - Israel Public Transportation Tracker</h1>
        <div style={{
          position: 'absolute',
          top: '10px',
          right: '10px',
          background: 'red',
          color: 'white',
          padding: '10px',
          fontWeight: 'bold',
          fontSize: '20px',
          borderRadius: '5px'
        }}>
          TEST BANNER
        </div>
      </div>
      
      <TransportControls />
      
      <div className="main-content">
        <div className="map-section">
          <MapView
            latitude={mapCenter[0]}
            longitude={mapCenter[1]}
            vehicleMarkers={showVehicleMarkers ? vehicleMarkers : []}
            routeShape={routeShape}
            stops={stops}
            destination={destination}
            center={mapCenter}
            defaultStartingPoint={defaultStartingPoint}
            defaultDestination={defaultDestination}
            calculateRoute={calculateRoute}
            onRouteCalculated={() => setCalculateRoute(false)}
            onDestinationSet={setDestination}
          />
        </div>
        <div className="data-section">
          <StationArrivals 
            siriData={siriData} 
            loading={loading} 
            error={error} 
            stationCode={stationCode}
          />
        </div>
      </div>
    </div>
  );
}

export default CombinedTransportApp;
