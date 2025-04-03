import axios from 'axios';

/**
 * Get real-time arrivals for a station
 * @param {string} stationCode - The station code
 * @param {string} detailLevel - The level of detail ('normal' or 'calls')
 * @returns {Promise<Object>} - The arrivals data
 */
export const fetchStationArrivals = async (stationCode, detailLevel = 'calls') => {
  try {
    const response = await axios.get(`/transport.php?station=${stationCode}&detail=${detailLevel}`);
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
    
    // Order is important - try the most reliable endpoints first
    const urls = [
      `/simple-shape-api.php?line=${lineNumber}`, // Try direct path first
      `/api/simple-shape-api.php?line=${lineNumber}`, // Then try in api folder
      `/api/gtfs-api.php?action=getLineShape&line=${lineNumber}`,
      `/gtfs-api.php?action=getLineShape&line=${lineNumber}`
    ];
    
    console.log("Will try endpoints in this order:", urls);
    
    for (const url of urls) {
      try {
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
          continue;
        }
        
        // Check for error in response
        if (data.error) {
          console.warn(`Error in data from ${url}:`, data.error);
          continue;
        }
        
        // Validate the response structure
        if (!data || typeof data !== 'object') {
          console.warn(`Invalid data structure from ${url}`);
          continue;
        }
        
        // Must have at least one direction with data
        const hasDirection0 = Array.isArray(data['0']) && data['0'].length > 0;
        const hasDirection1 = Array.isArray(data['1']) && data['1'].length > 0;
        
        if (!hasDirection0 && !hasDirection1) {
          console.warn(`No valid directions found in data from ${url}`);
          continue;
        }
        
        console.log(`Success with ${url}:`, {
          directions: Object.keys(data).length,
          direction0Points: hasDirection0 ? data['0'].length : 0,
          direction1Points: hasDirection1 ? data['1'].length : 0
        });
        
        return data;
      } catch (e) {
        console.warn(`Error with ${url}:`, e.message);
      }
    }
    
    throw new Error('All API endpoints failed to return valid shape data');
  } catch (error) {
    console.error('Error fetching line shape:', error);
    throw error;
  }
};
