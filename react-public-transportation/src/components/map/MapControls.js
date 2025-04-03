import React from 'react';
import PropTypes from 'prop-types';

const MapControls = ({ 
  searchQuery, 
  setSearchQuery, 
  handleSearch, 
  handleKeyPress,
  handleSetStartPoint,
  handleSetDestinationPoint,
  searchError,
  positionAddress,
  destinationAddress
}) => {
  return (
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
      {searchError && <p style={{ color: 'red', marginTop: '5px', fontSize: '14px' }}>{searchError}</p>}
      <div style={{ marginTop: '10px', fontSize: '14px', border: '1px solid #ccc', padding: '5px', borderRadius: '5px' }}>
        <p><strong>Start:</strong> {positionAddress || 'Loading...'}</p>
      </div>
      <div style={{ marginTop: '5px', fontSize: '14px', border: '1px solid #ccc', padding: '5px', borderRadius: '5px' }}>
        <p><strong>Destination:</strong> {destinationAddress || 'No destination set'}</p>
      </div>
    </div>
  );
};

MapControls.propTypes = {
  searchQuery: PropTypes.string.isRequired,
  setSearchQuery: PropTypes.func.isRequired,
  handleSearch: PropTypes.func.isRequired,
  handleKeyPress: PropTypes.func.isRequired,
  handleSetStartPoint: PropTypes.func.isRequired,
  handleSetDestinationPoint: PropTypes.func.isRequired,
  searchError: PropTypes.string,
  positionAddress: PropTypes.string,
  destinationAddress: PropTypes.string
};

export default MapControls;
