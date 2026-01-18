import axios from 'axios';

/**
 * Get real-time arrivals for a station
 * @param {string} stationCode - The station code
 * @param {string} detailLevel - The level of detail ('normal' or 'calls')
 * @returns {Promise<Object>} - The arrivals data
 */
export const fetchStationArrivals = async (stationCode, detailLevel = 'calls') => {
  try {
    // Updated to use the new Laravel endpoint
    const response = await axios.get(`/api/transport?station=${stationCode}`);
    return response.data;
  } catch (error) {
    console.error('Error fetching station arrivals:', error);
    throw new Error(`Failed to fetch arrivals: ${error.message}`);
  }
};

/**
 * Extract vehicle markers from SIRI data
 * @param {Object} siriData - The SIRI data from the API
 * @returns {Array} - Array of vehicle marker objects
 */
export const extractVehicleMarkers = (siriData) => {
  if (!siriData?.Siri?.ServiceDelivery?.StopMonitoringDelivery?.[0]?.MonitoredStopVisit) {
    return [];
  }

  const monitoredStopVisits = siriData.Siri.ServiceDelivery.StopMonitoringDelivery[0].MonitoredStopVisit;
  
  return monitoredStopVisits.map(visit => {
    const journey = visit.MonitoredVehicleJourney || {};
    const vehicleLocation = journey.VehicleLocation || {};
    
    if (vehicleLocation.Latitude && vehicleLocation.Longitude) {
      return {
        position: [vehicleLocation.Latitude, vehicleLocation.Longitude],
        vehicleRef: journey.VehicleRef,
        lineNumber: journey.PublishedLineName,
        expectedArrival: journey.MonitoredCall?.ExpectedArrivalTime,
        distanceFromStop: journey.MonitoredCall?.DistanceFromStop
      };
    }
    return null;
  }).filter(Boolean);
};

/**
 * Fetch the route shape for a given line number
 * @param {string} lineNumber - The line number
 * @returns {Promise<Object>} - The route shape data
 */
export const fetchLineShape = async (lineNumber) => {
  try {
    console.log(`Fetching line shape for line ${lineNumber}`);
    
    // Add logging to track the issue
    console.log("Starting API request for line shape...");
    
    // Update to use new Laravel endpoint first
    const url = `/api/shapes/${lineNumber}`;
    
    console.log(`Attempting to fetch from: ${url}`);
    const response = await fetch(url, {
      method: 'GET',
      headers: {
        'Accept': 'application/json',
        'Cache-Control': 'no-cache'
      }
    });
    
    const responseText = await response.text();
    console.log(`Raw response from ${url}:`, responseText.substring(0, 200) + '...');
    
    let data;
    try {
      data = JSON.parse(responseText);
    } catch (parseError) {
      console.error(`Error parsing JSON from ${url}:`, parseError);
      throw new Error('Failed to parse JSON');
    }
    
    // Check for error in response
    if (data.error) {
      console.warn(`Error in data from ${url}:`, data.error);
      throw new Error(data.error);
    }
    
    // Validate the response structure
    if (!data || typeof data !== 'object') {
      console.warn(`Invalid data structure from ${url}`);
      throw new Error('Invalid data structure');
    }
    
    // Must have at least one direction with data
    const hasDirection0 = Array.isArray(data['0']) && data['0'].length > 0;
    const hasDirection1 = Array.isArray(data['1']) && data['1'].length > 0;
    
    if (!hasDirection0 && !hasDirection1) {
      console.warn(`No valid directions found in data from ${url}`);
      throw new Error('No valid directions found');
    }
    
    console.log(`Success with ${url}:`, {
      directions: Object.keys(data).length,
      direction0Points: hasDirection0 ? data['0'].length : 0,
      direction1Points: hasDirection1 ? data['1'].length : 0
    });
    
    return data;
  } catch (error) {
    console.error('Error fetching line shape:', error);
    throw error;
  }
};
