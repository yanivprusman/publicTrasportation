import { useEffect, useState } from 'react';
import './App.css';
import { fetchAddress } from './components/map/MapUtilities';
import { configureDefaultLeafletIcons } from './components/map/MapMarkers';
import MapControls from './components/map/MapControls';
import useMapHandlers from './hooks/useMapHandlers';
import MapControlPanel from './components/map/MapControlPanel';
import RouteLayer from './components/map/RouteLayer';
import MarkersLayer from './components/map/MarkersLayer';
import MapEffect from './components/map/MapEffect';
import { MapContainer as LeafletMapContainer, TileLayer } from 'react-leaflet';
import { fetchStops } from './services/gtfs-service';
import MiddlePointHandler from './components/map/MiddlePointHandler';
import useRouteHandler from './hooks/useRouteHandler';
import { useTransport } from './contexts/TransportContext';
import L from 'leaflet';

// Configure default Leaflet icons
configureDefaultLeafletIcons();

function MapView(props) {
  // Use context for shared state
  const { setMapCenter: updateAppMapCenter } = useTransport();
  
  // Core state
  const [position, setPosition] = useState(props.defaultStartingPoint || [props.latitude, props.longitude]);
  const [mapReady, setMapReady] = useState(false);
  const [mapCenter, setMapCenterLocal] = useState(props.center || [props.latitude, props.longitude]);
  const [middlePoint, setMiddlePoint] = useState(null);
  const [showStops, setShowStops] = useState(false);
  const [stopsData, setStopsData] = useState([]);
  
  // External state sync - update context when map center changes
  useEffect(() => {
    updateAppMapCenter(mapCenter);
  }, [mapCenter, updateAppMapCenter]);
  
  // Default destination setup
  useEffect(() => {
    if (props.defaultDestination && (!props.destination || !props.destination[0])) {
      props.onDestinationSet(props.defaultDestination);
    }
  }, [props.defaultDestination, props.destination, props.onDestinationSet]);
  
  // Use custom hooks for map handlers
  const mapHandlers = useMapHandlers(position, setPosition, props.destination, props.onDestinationSet, setMapCenterLocal, mapCenter);
  const routeHandler = useRouteHandler(position, props.destination, mapHandlers.handleFindRoute, props.routeShape);
  
  // When destination changes, ensure it's properly set
  useEffect(() => {
    if (props.destination && Array.isArray(props.destination) && props.destination.length === 2) {
      // Destination is valid
      fetchAddress(props.destination[0], props.destination[1], mapHandlers.setDestinationAddress);
    }
  }, [props.destination]);
  
  // When position changes
  useEffect(() => {
    if (position && Array.isArray(position) && position.length === 2) {
      fetchAddress(position[0], position[1], mapHandlers.setPositionAddress);
    }
  }, [position]);
  
  // Add effect to respond to calculateRoute changes
  useEffect(() => {
    if (props.calculateRoute && position && props.destination) {
      // Calculate the route using the position as starting point
      routeHandler.handleFindRouteClick();
      
      // Let parent know we've processed the request
      if (props.onRouteCalculated) {
        props.onRouteCalculated();
      }
    }
  }, [props.calculateRoute, position, props.destination, routeHandler, props.onRouteCalculated]);
  
  // Fetch stops data when showStops changes
  useEffect(() => {
    if (showStops) {
      const fetchStopsData = async () => {
        try {
          const data = await fetchStops('60');
          setStopsData(data || []);
          
          // If we have stops and the map is available, fit the map to show them
          if (data && data.length > 0 && mapReady) {
            const stopPoints = data.map(stop => [stop.lat, stop.lon]);
            
            // Create a bounds object from the stops
            const bounds = stopPoints.reduce((bounds, point) => {
              bounds.extend(point);
              return bounds;
            }, L.latLngBounds(stopPoints[0], stopPoints[0]));
            
            // Get access to the Leaflet map instance
            const map = document.querySelector('.leaflet-container')?._leafletRef?.current?.getMap();
            
            // Fit map to the stops with padding if map is available
            if (map && map.fitBounds) {
              map.fitBounds(bounds, {
                padding: [50, 50],
                maxZoom: 14
              });
              
              console.log(`Map fitted to ${data.length} stops`);
            }
          }
        } catch (error) {
          console.error('Failed to fetch stops:', error);
          mapHandlers.setSearchError('Error fetching stops data');
          setTimeout(() => mapHandlers.setSearchError(null), 3000);
        }
      };
      fetchStopsData();
    }
  }, [showStops, mapReady]);

  return (
    <div style={{ height: '100%', width: '100%', position: 'relative' }}>
      {/* Search and controls */}
      <MapControls {...mapHandlers} />
      
      {/* Middle point handler */}
      <MiddlePointHandler 
        routeShape={props.routeShape || routeHandler.routeShape}
        route={routeHandler.route}
        setMiddlePoint={setMiddlePoint}
        setMapCenterLocal={setMapCenterLocal}
        setSearchError={mapHandlers.setSearchError}
      />
      
      {/* Route panel and controls */}
      <MapControlPanel
        showRoutePanel={routeHandler.showRoutePanel}
        setShowRoutePanel={routeHandler.setShowRoutePanel}
        optimizedRouteShape={routeHandler.optimizedRouteShape}
        handleShowRoutePanel={routeHandler.handleShowRoutePanel}
      />
      
      {/* Toggle Stops Button */}
      <button
        onClick={() => setShowStops(!showStops)}
        style={{
          position: 'absolute',
          bottom: '100px',  // Moved up to avoid overlap with middle point button
          left: '10px',
          zIndex: 1000,
          padding: '8px 12px',
          backgroundColor: showStops ? '#f44336' : '#4CAF50',
          color: 'white',
          border: 'none',
          borderRadius: '4px',
          cursor: 'pointer'
        }}
        title={showStops ? 'Hide bus stops' : 'Show bus stops for Line 60'}
      >
        {showStops ? 'Hide Stops' : 'Show Stops'}
      </button>
      
      <LeafletMapContainer 
        center={mapCenter} 
        zoom={13} 
        style={{ height: '100%', width: '100%' }} 
        zoomControl={false}
        whenReady={() => setMapReady(true)}
        preferCanvas={true}
        className="leaflet-container"
      >
        <TileLayer
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        />
        <MapEffect routeShape={routeHandler.optimizedRouteShape || props.routeShape} />
        
        {/* All route polylines */}
        <RouteLayer 
          routeShape={props.routeShape || routeHandler.routeShape}
          route={routeHandler.route}
        />
        
        {/* All map markers */}
        <MarkersLayer
          position={position}
          positionAddress={mapHandlers.positionAddress}
          destination={props.destination}
          destinationAddress={mapHandlers.destinationAddress}
          mapCenter={mapCenter}
          vehicleMarkers={props.vehicleMarkers || []}
          stops={showStops ? stopsData : (props.stops || [])}
          selectedStop={props.selectedStop}
          handleSetStartPoint={mapHandlers.handleSetStartPoint}
          middlePoint={middlePoint}
        />
      </LeafletMapContainer>
    </div>
  );
}

export default MapView;