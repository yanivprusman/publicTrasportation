import axios from 'axios';

// Base URL for the GTFS API
const GTFS_BASE_URL = '/api/gtfs-api.php';

// Mockup data for development until the backend is ready
const MOCK_STOPS = [
  { stopId: '26472', stopName: 'מסוף עמידר', lat: 32.06948949599059, lon: 34.83984033547383 },
  { stopId: '20832', stopName: 'גבעת שמואל', lat: 32.06764959792775, lon: 34.7867112130343 },
  { stopId: '20834', stopName: 'רמת גן', lat: 32.08012, lon: 34.81044 }
];

const MOCK_ROUTES = [
  { routeId: '9141', routeShortName: '60', routeLongName: 'פתח תקוה - תל אביב' },
  { routeId: '9142', routeShortName: '61', routeLongName: 'בני ברק - תל אביב' },
];

/**
 * Check the status of GTFS data
 * @returns {Promise<Object>} Status information
 */
export async function checkGtfsStatus() {
  try {
    const response = await axios.get(`${GTFS_BASE_URL}?endpoint=status`);
    return response.data;
  } catch (error) {
    console.error('Error checking GTFS status:', error);
    return { status: 'error', error: error.message };
  }
}

/**
 * Update GTFS data - triggers a background update process
 * @returns {Promise<Object>} Update status
 */
export async function updateGtfsData() {
  try {
    const response = await axios.get(`${GTFS_BASE_URL}?endpoint=update`);
    return response.data;
  } catch (error) {
    console.error('Error updating GTFS data:', error);
    return { status: 'error', error: error.message };
  }
}

/**
 * Load stop information from GTFS data
 * @returns {Promise<Array>} List of stops
 */
export async function getStops() {
  try {
    // First check if GTFS data is available
    const status = await checkGtfsStatus();
    
    if (status.status === 'ok') {
      // Get stops from API
      const response = await axios.get(`${GTFS_BASE_URL}?endpoint=stops`);
      return Array.isArray(response.data) ? response.data : MOCK_STOPS;
    } else {
      console.warn('GTFS data not available, using mock data');
      return MOCK_STOPS;
    }
  } catch (error) {
    console.error('Error fetching GTFS stops:', error);
    return MOCK_STOPS;
  }
}

/**
 * Load route information from GTFS data
 * @returns {Promise<Array>} List of routes
 */
export async function getRoutes() {
  try {
    // First check if GTFS data is available
    const status = await checkGtfsStatus();
    
    if (status.status === 'ok') {
      // Get routes from API
      const response = await axios.get(`${GTFS_BASE_URL}?endpoint=routes`);
      return Array.isArray(response.data) ? response.data : MOCK_ROUTES;
    } else {
      console.warn('GTFS data not available, using mock data');
      return MOCK_ROUTES;
    }
  } catch (error) {
    console.error('Error fetching GTFS routes:', error);
    return MOCK_ROUTES;
  }
}

/**
 * Get scheduled departures for a stop
 * @param {string} stopId - The stop ID
 * @returns {Promise<Array>} List of scheduled departures
 */
export async function getScheduledDepartures(stopId) {
  try {
    // First check if GTFS data is available
    const status = await checkGtfsStatus();
    
    if (status.status === 'ok') {
      // Get departures from API
      const response = await axios.get(`${GTFS_BASE_URL}?endpoint=departures&stop=${stopId}`);
      return Array.isArray(response.data) ? response.data : [];
    } else {
      console.warn('GTFS data not available, using mock data');
      // Return mock data for development
      return [
        {
          tripId: "20925867",
          routeId: "9141",
          lineNumber: "60",
          destination: "תל אביב",
          scheduledArrival: new Date(Date.now() + 600000).toISOString(), // 10 minutes from now
        },
        {
          tripId: "20925868",
          routeId: "9142",
          lineNumber: "61",
          destination: "בני ברק",
          scheduledArrival: new Date(Date.now() + 1200000).toISOString(), // 20 minutes from now
        }
      ];
    }
  } catch (error) {
    console.error(`Error fetching scheduled departures for stop ${stopId}:`, error);
    // Return mock data as fallback
    return [
      {
        tripId: "20925867",
        routeId: "9141",
        lineNumber: "60",
        destination: "תל אביב",
        scheduledArrival: new Date(Date.now() + 600000).toISOString(),
      },
      {
        tripId: "20925868",
        routeId: "9142",
        lineNumber: "61",
        destination: "בני ברק",
        scheduledArrival: new Date(Date.now() + 1200000).toISOString(),
      }
    ];
  }
}

/**
 * Search for stops by name
 * @param {string} query - Search query
 * @returns {Promise<Array>} List of matching stops
 */
export async function searchStops(query) {
  try {
    const response = await axios.get(`${GTFS_BASE_URL}/stops/search?q=${encodeURIComponent(query)}`);
    return response.data;
  } catch (error) {
    console.error('Error searching stops:', error);
    return [];
  }
}

/**
 * Get route shape (geometry)
 * @param {string} routeId - The route ID
 * @returns {Promise<Array>} List of coordinates forming the route shape
 */
export async function getRouteShape(routeId) {
  try {
    // First check if GTFS data is available
    const status = await checkGtfsStatus();
    
    if (status.status === 'ok') {
      // Get shape from API
      const response = await axios.get(`${GTFS_BASE_URL}?endpoint=shapes&route=${routeId}`);
      return Array.isArray(response.data) ? response.data : [];
    } else {
      console.warn('GTFS data not available, using mock data');
      // Return mock shape data
      if (routeId === '9141') {
        return [
          [32.06948949599059, 34.83984033547383],  // Starting point
          [32.06856, 34.82541],                   // Point in between
          [32.06764959792775, 34.7867112130343]    // Destination
        ];
      }
      return [];
    }
  } catch (error) {
    console.error(`Error fetching shape for route ${routeId}:`, error);
    return [];
  }
}
