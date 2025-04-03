import React from 'react';
import PropTypes from 'prop-types';
import './MapControls.css';

function MapControls({
  searchQuery,
  setSearchQuery,
  handleSearch,
  handleKeyPress,
  handleSetStartPoint,
  handleSetDestinationPoint,
  searchError,
  positionAddress,
  destinationAddress
}) {
  return (
    <div className="map-search-controls">
      <h4 className="map-controls-heading">Location Search</h4>
      <input
        type="text"
        placeholder="Search for a location"
        value={searchQuery}
        onChange={(e) => setSearchQuery(e.target.value)}
        onKeyPress={handleKeyPress}
        className="map-search-input"
      />
      <button onClick={handleSearch} className="secondary-button search-button">
        Search
      </button>
      <div className="map-button-group">
        <button
          onClick={handleSetStartPoint}
          className="map-button primary-button"
          title="Set the current map center as starting point"
        >
          Set Start
        </button>
        <button
          onClick={handleSetDestinationPoint}
          className="map-button secondary-button"
          title="Set the current map center as destination"
        >
          Set Destination
        </button>
      </div>
      {searchError && <p className="map-error-message">{searchError}</p>}
      <div className="map-address-box">
        <p><strong>Start:</strong> {positionAddress || 'אלרום רמת גן'}</p>
      </div>
      <div className="map-address-box">
        <p><strong>Destination:</strong> {destinationAddress || 'המסגר 49 תל אביב'}</p>
      </div>
    </div>
  );
}

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
