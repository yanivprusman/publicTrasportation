import React from 'react';
import RouteMapView from './RouteMapView';

/**
 * Displays control panels and overlays for the map
 */
const MapControlPanel = ({ 
  showRoutePanel, 
  setShowRoutePanel, 
  optimizedRouteShape,
  handleShowRoutePanel
}) => {
  return (
    <>
      {/* Route panel for showing the shape */}
      {(showRoutePanel || optimizedRouteShape) && (
        <div style={{
          position: 'absolute',
          top: '10px',
          right: '10px',
          background: 'white',
          padding: '10px',
          zIndex: 1000,
          width: '300px',
          boxShadow: '0 0 10px rgba(0,0,0,0.2)',
          borderRadius: '5px'
        }}>
          <h3>Route Shape View</h3>
          <button onClick={() => setShowRoutePanel(false)}>Close</button>
          <div style={{ height: '300px', marginTop: '10px' }}>
            <RouteMapView routeShape={optimizedRouteShape} />
          </div>
        </div>
      )}
      
      {/* Show Route Panel button */}
      {!showRoutePanel && optimizedRouteShape && (
        <button 
          onClick={handleShowRoutePanel}
          style={{
            position: 'absolute',
            bottom: '20px',
            right: '20px',
            zIndex: 1000,
            padding: '10px',
            background: 'blue',
            color: 'white',
            border: 'none',
            borderRadius: '5px'
          }}
        >
          Show Route Panel
        </button>
      )}
    </>
  );
};

export default MapControlPanel;
