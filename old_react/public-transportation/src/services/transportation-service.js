import * as siriService from '../api-service';
import * as gtfsService from './gtfs-service';

/**
 * Get combined static and real-time data for a station
 * @param {string} stationCode - The station code
 * @returns {Promise<Object>} Combined data
 */
export async function getStationData(stationCode) {
  try {
    // Get real-time arrivals
    const realTimeArrivals = await siriService.getStationArrivals(stationCode);
    
    // Get scheduled departures
    const scheduledDepartures = await gtfsService.getScheduledDepartures(stationCode);
    
    // Merge the data
    const combinedData = mergeRealTimeAndSchedule(realTimeArrivals, scheduledDepartures);
    
    return {
      stationCode,
      realTimeArrivals,
      scheduledDepartures,
      combinedData
    };
  } catch (error) {
    console.error(`Error getting combined data for station ${stationCode}:`, error);
    throw error;
  }
}

/**
 * Get stop details including geographical location
 * @param {string} stopId - The stop ID
 * @returns {Promise<Object>} Stop details
 */
export async function getStopDetails(stopId) {
  try {
    const stops = await gtfsService.getStops();
    return stops.find(stop => stop.stopId === stopId) || null;
  } catch (error) {
    console.error(`Error getting details for stop ${stopId}:`, error);
    return null;
  }
}

/**
 * Merge real-time arrivals with scheduled departures
 * @param {Array} realTimeArrivals - Real-time arrival data
 * @param {Array} scheduledDepartures - Scheduled departure data
 * @returns {Array} Combined data with real-time updates where available
 */
function mergeRealTimeAndSchedule(realTimeArrivals, scheduledDepartures) {
  // Create a map of trips from real-time data
  const realTimeMap = new Map();
  realTimeArrivals.forEach(arrival => {
    // Use tripId as the key
    realTimeMap.set(arrival.tripId, arrival);
  });
  
  // Enhance scheduled departures with real-time data
  return scheduledDepartures.map(departure => {
    const realTimeData = realTimeMap.get(departure.tripId);
    
    if (realTimeData) {
      // Add real-time information
      return {
        ...departure,
        realTime: true,
        expectedArrival: realTimeData.expectedArrival,
        delay: calculateDelay(departure.scheduledArrival, realTimeData.expectedArrival),
        vehicleLocation: realTimeData.vehicleLocation || null,
        distanceFromStop: realTimeData.distanceFromStop || null
      };
    } else {
      // No real-time data available, use scheduled time
      return {
        ...departure,
        realTime: false
      };
    }
  });
}

/**
 * Calculate delay in minutes between scheduled and expected times
 * @param {string} scheduledTime - ISO datetime string
 * @param {string} expectedTime - ISO datetime string
 * @returns {number} Delay in minutes (positive = late, negative = early)
 */
function calculateDelay(scheduledTime, expectedTime) {
  if (!scheduledTime || !expectedTime) return 0;
  
  const scheduled = new Date(scheduledTime);
  const expected = new Date(expectedTime);
  
  // Calculate difference in minutes
  return Math.round((expected - scheduled) / (60 * 1000));
}
