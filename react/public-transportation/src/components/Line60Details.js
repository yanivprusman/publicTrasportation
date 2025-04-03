import React, { useState, useEffect } from 'react';
import { MapContainer, TileLayer, Polyline, Popup } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';

const Line60Details = () => {
  const [routeData, setRouteData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);
        const response = await fetch('http://localhost/api/line60-data.php');
        
        if (!response.ok) {
          throw new Error(`HTTP error: ${response.status}`);
        }
        
        const data = await response.json();
        setRouteData(data);
      } catch (err) {
        setError(`Failed to fetch Line 60 data: ${err.message}`);
        console.error('Error fetching Line 60 data:', err);
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, []);

  if (loading) return <div>Loading Line 60 route data...</div>;
  if (error) return <div>Error: {error}</div>;
  if (!routeData) return <div>No data available</div>;

  const colors = {
    '0': 'blue',
    '1': 'red'
  };

  return (
    <div>
      <h2>Line 60 Route Details</h2>
      <p>Found {routeData.routeIds.length} route IDs for Line 60</p>
      
      <div style={{ height: '500px', marginTop: '20px' }}>
        <MapContainer center={[32.0729, 34.8046]} zoom={12} style={{ height: '100%', width: '100%' }}>
          <TileLayer
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          />
          
          {Object.entries(routeData.shapes).map(([direction, points]) => (
            <Polyline 
              key={direction}
              positions={points.map(p => [p.lat, p.lon])}
              pathOptions={{ 
                color: colors[direction] || 'green', 
                weight: 3,
                opacity: 0.7
              }}
            >
              <Popup>
                Direction: {direction}
              </Popup>
            </Polyline>
          ))}
        </MapContainer>
      </div>
      
      <div style={{ marginTop: '20px' }}>
        <h3>Route IDs</h3>
        <ul>
          {routeData.routeIds.map((id, index) => (
            <li key={index}>{id}</li>
          ))}
        </ul>
      </div>
      
      <div style={{ marginTop: '10px', fontSize: '0.9em', color: '#666' }}>
        <p>
          For more detailed information, visit the <a href="http://localhost/find-line60-route.php" target="_blank" rel="noopener noreferrer">
            Line 60 Route Analysis page
          </a>
        </p>
      </div>
    </div>
  );
};

export default Line60Details;
