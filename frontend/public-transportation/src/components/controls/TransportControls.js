import React from 'react';
import PropTypes from 'prop-types';

function TransportControls({ 
  stationCode, 
  setStationCode, 
  fetchStationData,
  lineNumber,
  setLineNumber,
  routeDirection,
  setRouteDirection,
  fetchLineShape,
  showVehicleMarkers,
  setShowVehicleMarkers,
  handleFindRoute
}) {
  return (
    <div className="controls-panel">
      <div style={{ marginRight: '20px' }}>
        <label style={{ marginRight: '10px' }}>Station:</label>
        <select 
          value={stationCode}
          onChange={(e) => setStationCode(e.target.value)}
          style={{ padding: '5px' }}
        >
          <option value="26472">מסוף עמידר (26472)</option>
          <option value="20832">Station 20832</option>
          {/* Add more stations as needed */}
        </select>
        <button 
          onClick={fetchStationData} 
          style={{ marginLeft: '10px', padding: '5px 10px' }}
        >
          Refresh Station Data
        </button>
        
        {/* Add checkbox for vehicle markers */}
        <label style={{ marginLeft: '15px' }}>
          <input 
            type="checkbox" 
            checked={showVehicleMarkers} 
            onChange={(e) => setShowVehicleMarkers(e.target.checked)} 
          />
          Show Vehicles
        </label>
      </div>
      <div>
        <label style={{ marginRight: '10px' }}>Line:</label>
        <input 
          type="text" 
          value={lineNumber} 
          onChange={(e) => setLineNumber(e.target.value)}
          style={{ width: '50px', padding: '5px' }}
        />
        <label style={{ marginLeft: '10px', marginRight: '5px' }}>Direction:</label>
        <select
          value={routeDirection}
          onChange={(e) => setRouteDirection(e.target.value)}
          style={{ padding: '5px' }}
        >
          <option value="0">Outbound</option>
          <option value="1">Inbound</option>
        </select>
        <button 
          onClick={fetchLineShape} 
          style={{ marginLeft: '10px', padding: '5px 10px' }}
        >
          Show Route
        </button>
        
        {/* Add Find Route button */}
        <button 
          onClick={handleFindRoute} 
          style={{ marginLeft: '10px', padding: '5px 10px', backgroundColor: '#4CAF50', color: 'white' }}
        >
          Find Route
        </button>
      </div>
    </div>
  );
}

TransportControls.propTypes = {
  stationCode: PropTypes.string.isRequired,
  setStationCode: PropTypes.func.isRequired,
  fetchStationData: PropTypes.func.isRequired,
  lineNumber: PropTypes.string.isRequired,
  setLineNumber: PropTypes.func.isRequired,
  routeDirection: PropTypes.string.isRequired,
  setRouteDirection: PropTypes.func.isRequired,
  fetchLineShape: PropTypes.func.isRequired,
  showVehicleMarkers: PropTypes.bool.isRequired,
  setShowVehicleMarkers: PropTypes.func.isRequired,
  handleFindRoute: PropTypes.func
};

export default TransportControls;
