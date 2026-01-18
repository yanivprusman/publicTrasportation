import React, { useState } from 'react';
import { useTransport } from '../../contexts/TransportContext';
import LineSelector from '../route-selection/LineSelector';

function TransportControls() {
  const {
    stationCode,
    setStationCode,
    fetchStationData,
    lineNumber,
    setLineNumber,
    routeDirection,
    setRouteDirection,
    handleFetchLineShape,
    showVehicleMarkers,
    setShowVehicleMarkers,
    handleFindRoute,
    defaultStartingPointLabel,
    defaultDestinationLabel
  } = useTransport();
  
  // State to track if we're in advanced line selection mode
  const [showAdvancedLineSelection, setShowAdvancedLineSelection] = useState(false);
  const [selectedRouteId, setSelectedRouteId] = useState(null);

  // Handle line selection from LineSelector component
  const handleLineSelected = ({ routeId, routeShortName }) => {
    setSelectedRouteId(routeId);
    setLineNumber(routeShortName);
    // Close the selector automatically
    setShowAdvancedLineSelection(false);
  };

  return (
    <div className="controls-panel">
      <div className="controls-section">
        <div className="station-controls">
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
            className="primary-button"
          >
            Refresh Data
          </button>
          
          <label style={{ marginLeft: '15px' }}>
            <input 
              type="checkbox" 
              checked={showVehicleMarkers} 
              onChange={(e) => setShowVehicleMarkers(e.target.checked)} 
            />
            Show Vehicles
          </label>
        </div>
      </div>
      
      <div className="controls-section route-info-section">
        <div className="default-route-info">
          <p className="default-route-label">Default Navigation: <b>{defaultStartingPointLabel}</b> → <b>{defaultDestinationLabel}</b></p>
          <button onClick={handleFindRoute} className="primary-button navigation-button">
            Find Navigation Route
          </button>
        </div>
      </div>

      <div className="controls-section">
        {!showAdvancedLineSelection ? (
          <div className="line-controls">
            <label style={{ marginRight: '10px' }}>Bus Line:</label>
            <input 
              type="text" 
              value={lineNumber}
              onChange={(e) => setLineNumber(e.target.value)}
              style={{ width: '50px', padding: '5px' }}
            />
            <button
              onClick={() => setShowAdvancedLineSelection(true)}
              className="secondary-button"
            >
              Find Lines
            </button>
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
              onClick={handleFetchLineShape} 
              className="primary-button"
            >
              Show Bus Route
            </button>
          </div>
        ) : (
          <div>
            <LineSelector 
              onLineSelected={handleLineSelected} 
              initialLineNumber={lineNumber} 
            />
            <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: '10px' }}>
              <button
                onClick={() => setShowAdvancedLineSelection(false)}
                className="secondary-button"
              >
                Cancel
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export default TransportControls;
