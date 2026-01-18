/**
 * Public Transportation API Service
 * Handles queries to Israel's public transportation SIRI-SM API
 */

import axios from 'axios';

// Use the correct URL to the transport.php script
const PROXY_URL = '/transport.php';

/**
 * Get real-time arrivals for a station
 * @param {string|number} stationCode - The station code (e.g., 26472)
 * @returns {Promise<Object>} - The arrivals data
 */
export async function getStationArrivals(stationCode) {
  try {
    console.log('Fetching data from:', `${PROXY_URL}?station=${stationCode}`);
    const response = await axios.get(`${PROXY_URL}?station=${stationCode}`);
    console.log('Response received:', response);
    
    // Add fallback if data is not in expected format
    if (!response.data?.Siri?.ServiceDelivery) {
      console.warn('Unexpected response format:', response.data);
      throw new Error('Unexpected response format from API');
    }
    return processArrivalsData(response.data);
  } catch (error) {
    console.error('Error fetching station arrivals:', error);
    throw error;
  }
}

/**
 * Get arrivals for a specific line at a station
 * @param {string|number} stationCode - The station code (e.g., 26472)
 * @param {string|number} lineNumber - The line number (e.g., 60)
 * @returns {Promise<Object>} - The filtered arrivals data
 */
export async function getLineArrivalsAtStation(stationCode, lineNumber) {
  try {
    const response = await axios.get(`${PROXY_URL}?station=${stationCode}&line=${lineNumber}`);
    return processArrivalsData(response.data);
  } catch (error) {
    console.error('Error fetching line arrivals:', error);
    throw error;
  }
}

/**
 * Process the raw data from the SIRI-SM API
 * @param {Object} data - Raw API response
 * @returns {Array} - Processed arrival data
 */
function processArrivalsData(data) {
  try {
    // Extract the monitor visits from the response
    const monitoredStopVisits = 
      data?.Siri?.ServiceDelivery?.StopMonitoringDelivery?.[0]?.MonitoredStopVisit || [];
    
    // Map the data to a more usable format
    return monitoredStopVisits.map(visit => {
      const journey = visit.MonitoredVehicleJourney;
      const call = journey.MonitoredCall;
      
      return {
        lineNumber: journey.PublishedLineName,
        operatorRef: journey.OperatorRef,
        destinationName: journey.DestinationName || journey.DestinationRef,
        vehicleRef: journey.VehicleRef,
        expectedArrival: call?.ExpectedArrivalTime || null,
        aimedArrival: call?.AimedArrivalTime || null,
        arrivalStatus: call?.ArrivalStatus || null,
        distanceFromStop: call?.DistanceFromStop || null,
        recordedAt: visit.RecordedAtTime,
        stopPointRef: call?.StopPointRef || null,
        tripId: journey.FramedVehicleJourneyRef?.DatedVehicleJourneyRef || null,
        onwardCalls: journey.OnwardCalls?.OnwardCall?.map(call => ({
          stopCode: call.StopPointRef,
          order: call.Order,
          expectedArrival: call.ExpectedArrivalTime,
          status: call.ArrivalStatus
        })) || []
      };
    });
  } catch (error) {
    console.error('Error processing arrivals data:', error);
    return [];
  }
}
