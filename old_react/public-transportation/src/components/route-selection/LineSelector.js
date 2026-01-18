import React, { useState, useEffect } from 'react';
import axios from 'axios';
import './LineSelector.css';

/**
 * Component to search for routes by line number and select specific route
 */
const LineSelector = ({ onLineSelected, initialLineNumber = '' }) => {
  const [lineNumber, setLineNumber] = useState(initialLineNumber);
  const [matchingRoutes, setMatchingRoutes] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [selectedRouteId, setSelectedRouteId] = useState(null);
  
  // Search for routes when line number changes
  const searchRoutes = async () => {
    if (!lineNumber.trim()) {
      setMatchingRoutes([]);
      return;
    }
    
    setLoading(true);
    setError(null);
    
    try {
      const response = await axios.get(`/api/routes/search?line=${lineNumber}`);
      setMatchingRoutes(response.data.routes || []);
      
      // Auto-select if there's only one route
      if (response.data.routes.length === 1) {
        setSelectedRouteId(response.data.routes[0].route_id);
      }
    } catch (err) {
      setError('Failed to fetch route information.');
      console.error('Error searching routes:', err);
    } finally {
      setLoading(false);
    }
  };
  
  // Handle form submission
  const handleSubmit = (e) => {
    e.preventDefault();
    searchRoutes();
  };
  
  // Handle route selection
  const handleSelectRoute = (routeId, routeShortName) => {
    setSelectedRouteId(routeId);
    onLineSelected && onLineSelected({
      routeId,
      routeShortName
    });
  };
  
  // Auto-search on initial load if line number is provided
  useEffect(() => {
    if (initialLineNumber) {
      setLineNumber(initialLineNumber);
      searchRoutes();
    }
  }, [initialLineNumber]);
  
  return (
    <div className="line-selector">
      <form onSubmit={handleSubmit}>
        <div className="form-row">
          <input
            type="text"
            value={lineNumber}
            onChange={(e) => setLineNumber(e.target.value)}
            placeholder="Enter line number"
            className="line-input"
          />
          <button type="submit" className="search-button" disabled={loading}>
            {loading ? 'Searching...' : 'Search'}
          </button>
        </div>
      </form>
      
      {error && <div className="error-message">{error}</div>}
      
      {matchingRoutes.length > 0 && (
        <div className="routes-list">
          <h4>Select the correct route:</h4>
          <ul>
            {matchingRoutes.map(route => (
              <li 
                key={route.route_id}
                className={selectedRouteId === route.route_id ? 'selected' : ''}
                onClick={() => handleSelectRoute(route.route_id, route.route_short_name)}
              >
                <div className="route-header">
                  <span className="route-number">{route.route_short_name}</span>
                  <span className="route-name">{route.route_long_name}</span>
                </div>
                <div className="route-details">
                  <span className="route-agency">Agency: {route.agency.name}</span>
                  {route.cities && route.cities.length > 0 && (
                    <span className="route-cities">Cities: {route.cities.join(' → ')}</span>
                  )}
                </div>
              </li>
            ))}
          </ul>
        </div>
      )}
      
      {loading && <div className="loading">Searching for routes...</div>}
      
      {!loading && matchingRoutes.length === 0 && lineNumber && (
        <div className="no-results">No matching routes found for line {lineNumber}</div>
      )}
    </div>
  );
};

export default LineSelector;
