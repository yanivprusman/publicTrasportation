import './App.css';
import axios from 'axios';
import { useEffect, useState, useRef } from 'react';
import MapView from './MapView';

// Renamed component from App to TransportationMap for clarity
function TransportationMap() {
  const [startingPoint, setStartingPoint] = useState([32.06948949599059, 34.83984033547383]);
  const [destination, setDestination] = useState([32.06764959792775, 34.7867112130343]);
  const [mapCenter, setMapCenter] = useState([
    (startingPoint[0] + destination[0]) / 2,
    (startingPoint[1] + destination[1]) / 2,
  ]);

  useEffect(() => {
    setMapCenter([
      (startingPoint[0] + destination[0]) / 2,
      (startingPoint[1] + destination[1]) / 2,
    ]);
  }, [startingPoint, destination]);

  const isInitialRender = useRef(true);
  const [Siri, setSiriData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (isInitialRender.current) {
      isInitialRender.current = false;
      return;
    }
    console.log('Hi');
  }, []);

  const fetchData = () => {
    setLoading(true);
    setError(null);
    console.group('Fetch Data Process');
    console.log('Starting fetch at:', new Date().toISOString());

    axios({
      method: 'get',
      url: '/transport.php',
      timeout: 10000,
    })
      .then((res) => {
        console.log('Full Axios Response:', res);
        console.log('Response Data:', res.data);
        const siriData =
          res.data?.Siri?.ServiceDelivery?.StopMonitoringDelivery || [];
        console.log('Extracted Siri Data:', siriData);
        setSiriData(siriData);
        setLoading(false);
      })
      .catch((err) => {
        console.error('Complete Error Object:', err);
        console.log('Error Details:', {
          message: err.message,
          code: err.code,
          response: err.response,
          request: err.request,
        });
        const errorMessage = err.response
          ? `Server responded with status ${err.response.status}`
          : err.request
            ? 'Network error: Unable to reach the server'
            : 'An unexpected error occurred';
        setError(errorMessage);
        setLoading(false);
      })
      .finally(() => {
        console.groupEnd();
      });
  };

  useEffect(() => {
    console.log('Initial Render - Fetching Data');
    fetchData();
  }, []);

  useEffect(() => {
    console.log('Siri State Updated:', Siri);
  }, [Siri]);

  function VehicleDetails({ journey }) {
    return (
      <div className="vehicle-details-container">
        <div className="details-row">
          <label className="label">Line:</label>
          <span className="value">{journey.PublishedLineName}</span>
        </div>
        <div className="details-row">
          <label className="label">Direction:</label>
          <span className="value">{journey.DirectionRef}</span>
        </div>
        <div className="details-row">
          <label className="label">Expected Arrival:</label>
          <span className="value">{journey.MonitoredCall?.ExpectedArrivalTime || 'N/A'}</span>
        </div>
        <div className="details-row">
          <label className="label">Distance From Stop:</label>
          <span className="value">{journey.MonitoredCall?.DistanceFromStop ? `${journey.MonitoredCall.DistanceFromStop} meters` : 'N/A'}</span>
        </div>
        <div className="details-row">
          <label className="label">Vehicle Location:</label>
          <span className="value">
            {journey.VehicleLocation?.Latitude && journey.VehicleLocation?.Longitude
              ? `${journey.VehicleLocation.Latitude}, ${journey.VehicleLocation.Longitude}`
              : 'Location not available'}
          </span>
        </div>
      </div>
    );
  }

  function ShowData({ Siri, loading, error }) {
    if (loading) return <h2>Loading...</h2>;
    if (error) return <h2>{error}</h2>;
    if (!Siri || !Siri.ServiceDelivery) {
      console.warn('No Siri data or ServiceDelivery found');
      return <h2>No data available</h2>;
    }

    const monitoredVehicles =
      Siri.ServiceDelivery.StopMonitoringDelivery[0]?.MonitoredStopVisit || [];

    if (monitoredVehicles.length === 0) {
      return <h2>No vehicles are currently being monitored</h2>;
    }

    console.log('Monitored Vehicles:', monitoredVehicles);

    return (
      <div>
        <h2>Monitored Vehicles:</h2>
        {monitoredVehicles.map((visit, index) => (
          <VehicleDetails
            key={visit.ItemIdentifier || index}
            journey={visit.MonitoredVehicleJourney || {}}
          />
        ))}
      </div>
    );
  }

  return (
    <div className="App">
      <div className="map-container">
        <MapView
          latitude={startingPoint[0]}
          longitude={startingPoint[1]}
          destination={destination}
          onDestinationSet={setDestination}
          startingPoint={startingPoint}
          destinationCoords={destination}
          tooltipContent={`Your location: ${startingPoint[0]}, ${startingPoint[1]}`}
        />
        {console.log('MapView Props:', { latitude: mapCenter.latitude, longitude: mapCenter.longitude })}
      </div>
      <button
        className="refresh-button"
        onClick={fetchData}
        disabled={loading}
      >
        {loading ? 'Refreshing...' : 'Refresh'}
      </button>
      <div className="content">
        <div className="left-side">
          <ShowData Siri={Siri} loading={loading} error={error} />
        </div>
        <div className="right-side">
          <h2>
            {Siri.map((item, index) => (
              <div key={index}>
              </div>
            ))}
          </h2>
        </div>
      </div>
    </div>
  );
}

export default TransportationMap;
