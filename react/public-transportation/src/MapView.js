import { useEffect, useState } from 'react';
import './App.css';
import { MapContainer, TileLayer, Marker, Popup, useMapEvents, useMap, Polyline } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';
import axios from 'axios';
import polyline from '@mapbox/polyline';

delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon-2x.png',
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
});
const destinationIcon = new L.Icon({
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon.png',
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon-2x.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41],
  className: 'destination-marker'
});
const centerIcon = new L.Icon({
  iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-green.png',
  iconRetinaUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-green.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41],
  className: 'center-marker'
});

function TrackMapMovement({ setMapCenter }) {
  useMapEvents({
    moveend: (event) => {
      const map = event.target;
      const center = map.getCenter();
      setMapCenter([center.lat, center.lng]);
    },
  });
  return null;
}

function UpdateMapView({ position }) {
  const map = useMap();
  useEffect(() => {
    map.setView(position, map.getZoom());
  }, [position, map]);
  return null;
}

function MapView({ 
  latitude, 
  longitude, 
  destination, 
  onDestinationSet, 
  startingPoint,
  mapCenter: initialMapCenter
}) {
  const [position, setPosition] = useState([latitude, longitude]);
  const [positionAddress, setPositionAddress] = useState('');
  const [destinationAddress, setDestinationAddress] = useState('');
  const [route, setRoute] = useState(null);
  const [mapCenter, setMapCenter] = useState(initialMapCenter || [latitude, longitude]);
  const [searchQuery, setSearchQuery] = useState('');
  const [searchError, setSearchError] = useState(null);
  const [isTrackingEnabled, setIsTrackingEnabled] = useState(true);

  useEffect(() => {
    const style = document.createElement('style');
    style.innerHTML = `
      .destination-marker {
        filter: hue-rotate(120deg);
      }
    `;
    document.head.appendChild(style);
    return () => {
      document.head.removeChild(style);
    };
  }, []);

  useEffect(() => {
    setPosition([latitude, longitude]);
    fetchAddress(latitude, longitude, setPositionAddress);
  }, [latitude, longitude]);

  useEffect(() => {
    if (destination) {
      const newCenter = [
        (position[0] + destination[0]) / 2,
        (position[1] + destination[1]) / 2
      ];
      setMapCenter(newCenter);
      fetchAddress(destination[0], destination[1], setDestinationAddress);
    }
  }, [position, destination]);

  const simplifyAddress = (address) => {
    const parts = address.split(',').slice(0, 2);
    return parts.join(',').trim();
  };

  const fetchAddress = async (lat, lon, setAddress) => {
    try {
      const response = await axios.get(
        `https://nominatim.openstreetmap.org/reverse?lat=${lat}&lon=${lon}&format=json&accept-language=he&addressdetails=1&countrycodes=il`
      );
      const fullAddress = response.data.display_name || 'Address not found';
      setAddress(simplifyAddress(fullAddress));
    } catch (error) {
      console.error('Error fetching address:', error);
      setAddress('Error fetching address');
    }
  };

  const handleSearch = async () => {
    if (!searchQuery.trim()) return;

    try {
      setSearchError(null);
      const response = await axios.get(
        `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(searchQuery)}&format=json`
      );
      if (response.data.length > 0) {
        const { lat, lon } = response.data[0];
        setMapCenter([parseFloat(lat), parseFloat(lon)]);
      } else {
        setSearchError('Location not found.');
      }
    } catch (error) {
      setSearchError('Error fetching location. Please try again.');
    }
  };

  const handleSetStartPoint = () => {
    setPosition(mapCenter);
    fetchAddress(mapCenter[0], mapCenter[1], setPositionAddress);
  };

  const handleSetDestinationPoint = () => {
    const newDestination = mapCenter;
    onDestinationSet(newDestination);
    fetchAddress(newDestination[0], newDestination[1], setDestinationAddress);
  };

  const handleKeyPress = (e) => {
    if (e.key === 'Enter') {
      handleSearch();
    }
  };

  const handleFindRoute = async () => {
    if (!destination) {
      setSearchError('Please set a destination first.');
      return;
    }

    const MAX_RETRIES = 30;
    let attempts = 0;

    while (attempts < MAX_RETRIES) {
      try {
        setSearchError(null);
        const response = await axios.get(
          `https://api.openrouteservice.org/v2/directions/driving-car`,
          {
            params: {
              api_key: '5b3ce3597851110001cf62489ba3c6e8d4824449975eca1f205d2eec',
              start: `${position[1]},${position[0]}`,
              end: `${destination[1]},${destination[0]}`,
            },
          }
        );

        if (response.data && response.data.features && response.data.features.length > 0) {
          const routeGeometry = response.data.features[0].geometry.coordinates;
          const decodedRoute = routeGeometry.map(([lon, lat]) => [lat, lon]);
          setRoute(decodedRoute);
          return;
        } else {
          setSearchError('No route found. Please try again with different locations.');
          return;
        }
      } catch (error) {
        attempts++;
        if (attempts >= MAX_RETRIES) {
          if (error.response && error.response.status === 403) {
            setSearchError('Invalid API key or insufficient permissions.');
          } else if (error.response && error.response.status === 503) {
            setSearchError('Service unavailable. Please try again later.');
          } else {
            setSearchError('Error fetching route. Please try again.');
          }
        }
      }
    }
  };

  return (
    <div style={{ height: '100vh', width: '100%' }}>
      <div style={{ position: 'absolute', top: 10, left: 10, zIndex: 1000, backgroundColor: 'rgba(255,255,255,0.8)', padding: '10px', borderRadius: '5px' }}>
        <div>
          <input
            type="text"
            placeholder="Search for a location"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            onKeyPress={handleKeyPress}
            style={{
              padding: '5px',
              width: '200px',
              marginRight: '5px',
              borderRadius: '5px',
              border: '1px solid #ccc',
              marginBottom: '5px'
            }}
          />
          <div style={{ display: 'flex', gap: '5px' }}>
            <button
              onClick={handleSetStartPoint}
              style={{
                padding: '5px 10px',
                backgroundColor: 'blue',
                color: 'white',
                border: 'none',
                borderRadius: '5px',
                cursor: 'pointer',
                flex: 1
              }}
            >
              Set Starting Point
            </button>
            <button
              onClick={handleSetDestinationPoint}
              style={{
                padding: '5px 10px',
                backgroundColor: 'orange',
                color: 'white',
                border: 'none',
                borderRadius: '5px',
                cursor: 'pointer',
                flex: 1
              }}
            >
              Set Destination
            </button>
          </div>
        </div>
        <button
          onClick={handleFindRoute}
          style={{
            padding: '5px 10px',
            backgroundColor: 'purple',
            color: 'white',
            border: 'none',
            borderRadius: '5px',
            cursor: 'pointer',
            marginTop: '10px',
            width: '100%',
          }}
        >
          Find Route
        </button>
        {searchError && <p style={{ color: 'red', marginTop: '5px', fontSize: '14px' }}>{searchError}</p>}
        <div style={{ marginTop: '10px', fontSize: '14px', border: '1px solid #ccc', padding: '5px', borderRadius: '5px' }}>
          <p><strong>Start:</strong> {positionAddress || 'Loading...'}</p>
        </div>
        <div style={{ marginTop: '5px', fontSize: '14px', border: '1px solid #ccc', padding: '5px', borderRadius: '5px' }}>
          <p><strong>Destination:</strong> {destinationAddress || 'No destination set'}</p>
        </div>
      </div>
      <MapContainer center={mapCenter} zoom={13} style={{ height: '100%', width: '100%' }} zoomControl={false}>
        <TileLayer
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        />
        <UpdateMapView position={mapCenter} />
        <TrackMapMovement setMapCenter={setMapCenter} />
        <Marker position={position}>
          <Popup>
            Your location: {positionAddress}<br/>
            Coordinates: {position[0].toFixed(6)}, {position[1].toFixed(6)}
          </Popup>
        </Marker>
        {destination && (
          <Marker position={destination} icon={destinationIcon}>
            <Popup>
              <div>
                <strong>Destination:</strong> {destinationAddress}<br/>
                <strong>Coordinates:</strong> {destination[0].toFixed(6)}, {destination[1].toFixed(6)}
              </div>
            </Popup>
          </Marker>
        )}
        <Marker position={mapCenter} icon={centerIcon}>
          <Popup>
            <div>
              <strong>Map Center:</strong> {mapCenter[0].toFixed(6)}, {mapCenter[1].toFixed(6)}
            </div>
          </Popup>
        </Marker>
        {route && (
          <Polyline positions={route} color="blue" />
        )}
      </MapContainer>
    </div>
  );
}

export default MapView;