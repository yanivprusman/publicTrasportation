import React, { useState, useEffect } from 'react';
import './App.css';
import axios from 'axios';

function App() {
  const [siriData, setSiriData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [stationCode, setStationCode] = useState('26472'); // Default station code

  const fetchData = () => {
    setLoading(true);
    setError(null);
    console.group('Fetch Data Process');
    console.log('Starting fetch at:', new Date().toISOString());
    
    // Use direct axios call to the transport.php endpoint
    axios.get(`/transport.php?station=${stationCode}`)
      .then(response => {
        console.log('API Response:', response.data);
        // Store the raw SIRI data directly without transformation
        setSiriData(response.data);
        setLoading(false);
      })
      .catch(err => {
        console.error('Error fetching arrivals:', err);
        setError(`Connection error: ${err.message}. Make sure the API server is running.`);
        setLoading(false);
      })
      .finally(() => {
        console.groupEnd();
      });
  };

  useEffect(() => {
    console.log('Initial Render - Fetching Data');
    fetchData();
  }, [stationCode]);

  function ShowData({ siriData, loading, error }) {
    if (loading) return <h2>Loading...</h2>;
    if (error) return <h2>Error: {error}</h2>;
    if (!siriData) return <h2>No data available</h2>;

    const monitoredStopVisits = siriData?.Siri?.ServiceDelivery?.StopMonitoringDelivery?.[0]?.MonitoredStopVisit || [];

    if (monitoredStopVisits.length === 0) {
      return <h2>No vehicles are currently being monitored for station {stationCode}</h2>;
    }

    return (
      <div>
        <h2>Monitored Vehicles:</h2>
        <table className="vehicle-table">
          <thead>
            <tr>
              <th>Line</th>
              <th>Direction</th>
              <th>Destination</th>
              <th>Arrival Time</th>
              <th>Vehicle</th>
              <th>Distance</th>
              <th>Location</th>
            </tr>
          </thead>
          <tbody>
            {monitoredStopVisits.map((visit, index) => {
              const journey = visit.MonitoredVehicleJourney || {};
              const vehicleLocation = journey.VehicleLocation || {};
              const monitoredCall = journey.MonitoredCall || {};
              
              // Format the expected arrival time
              let arrivalTime = "N/A";
              if (monitoredCall.ExpectedArrivalTime) {
                const date = new Date(monitoredCall.ExpectedArrivalTime);
                arrivalTime = date.toLocaleTimeString();
              }

              return (
                <tr key={visit.ItemIdentifier || index}>
                  <td>{journey.PublishedLineName || 'N/A'}</td>
                  <td>{journey.DirectionRef || 'N/A'}</td>
                  <td>{journey.DestinationRef || 'N/A'}</td>
                  <td>{arrivalTime}</td>
                  <td>{journey.VehicleRef || 'N/A'}</td>
                  <td>{monitoredCall.DistanceFromStop ? `${monitoredCall.DistanceFromStop} meters` : 'N/A'}</td>
                  <td>
                    {vehicleLocation.Latitude && vehicleLocation.Longitude
                      ? `${vehicleLocation.Latitude}, ${vehicleLocation.Longitude}`
                      : 'Location not available'}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    );
  }

  return (
    <div className="App">
      <div className="station-selector">
        <h3>Select Station</h3>
        <select 
          value={stationCode}
          onChange={(e) => setStationCode(e.target.value)}
        >
          <option value="26472">מסוף עמידר (26472)</option>
          <option value="20832">Station 20832</option>
          {/* Add more stations as needed */}
        </select>
        <button onClick={fetchData}>Refresh Data</button>
      </div>
      <div className="content">
        <ShowData siriData={siriData} loading={loading} error={error} />
      </div>
    </div>
  );
}

export default App;