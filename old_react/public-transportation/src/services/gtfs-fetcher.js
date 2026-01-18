import axios from 'axios';
import JSZip from 'jszip';
import Papa from 'papaparse';

const GTFS_BASE_URL = 'https://gtfs.mot.gov.il/gtfsfiles';

/**
 * Download and extract a GTFS zip file
 * @param {string} fileName - Name of the zip file to download
 * @returns {Promise<Object>} Extracted files as a map of filename -> content
 */
async function downloadAndExtractGtfsFile(fileName) {
  try {
    console.log(`Downloading ${fileName} from ${GTFS_BASE_URL}...`);
    const response = await axios.get(`${GTFS_BASE_URL}/${fileName}`, {
      responseType: 'arraybuffer',
      onDownloadProgress: (progressEvent) => {
        const percentCompleted = Math.round((progressEvent.loaded * 100) / progressEvent.total);
        console.log(`Download progress for ${fileName}: ${percentCompleted}%`);
      }
    });
    
    console.log(`Extracting ${fileName}...`);
    const zip = await JSZip.loadAsync(response.data);
    
    // Extract files
    const files = {};
    const extractPromises = [];
    zip.forEach((relativePath, zipEntry) => {
      if (!zipEntry.dir) {
        const promise = zipEntry.async('string').then(content => {
          files[relativePath] = content;
        });
        extractPromises.push(promise);
      }
    });
    
    await Promise.all(extractPromises);
    console.log(`Extracted ${Object.keys(files).length} files from ${fileName}`);
    return files;
  } catch (error) {
    console.error(`Error downloading or extracting ${fileName}:`, error);
    throw error;
  }
}

/**
 * Parse a CSV content string into an array of objects
 * @param {string} csvContent - CSV content as string
 * @returns {Array<Object>} Parsed data as array of objects
 */
function parseCsvContent(csvContent) {
  const result = Papa.parse(csvContent, {
    header: true,
    skipEmptyLines: true,
    dynamicTyping: true
  });
  
  if (result.errors && result.errors.length > 0) {
    console.warn('CSV parsing had errors:', result.errors);
  }
  
  return result.data;
}

/**
 * Download, extract, and parse the main GTFS package
 * @returns {Promise<Object>} Parsed GTFS data
 */
export async function fetchMainGtfsData() {
  const fileName = 'israel-public-transportation.zip';
  const files = await downloadAndExtractGtfsFile(fileName);
  
  // Parse all CSV files
  const parsedData = {};
  for (const [filePath, content] of Object.entries(files)) {
    if (filePath.endsWith('.txt')) {
      const tableName = filePath.split('.')[0]; // Remove extension
      parsedData[tableName] = parseCsvContent(content);
      console.log(`Parsed ${tableName}: ${parsedData[tableName].length} records`);
    }
  }
  
  return parsedData;
}

/**
 * Fetch trip ID to date mapping
 * @returns {Promise<Array>} Trip ID date mapping
 */
export async function fetchTripIdToDate() {
  const fileName = 'TripIdToDate.zip';
  const files = await downloadAndExtractGtfsFile(fileName);
  const content = files['TripIdToDate.txt'];
  return parseCsvContent(content);
}

/**
 * Fetch tariff data
 * @returns {Promise<Object>} Tariff data
 */
export async function fetchTariffData() {
  const fileName = 'tariff_2022.zip';
  const files = await downloadAndExtractGtfsFile(fileName);
  
  const parsedData = {};
  for (const [filePath, content] of Object.entries(files)) {
    if (filePath.endsWith('.csv')) {
      const tableName = filePath.split('.')[0]; 
      parsedData[tableName] = parseCsvContent(content);
    }
  }
  
  return parsedData;
}

/**
 * Initialize GTFS data
 * This will download and process all required GTFS files
 * @param {boolean} useCached - Whether to use cached data when available
 * @returns {Promise<Object>} Processed GTFS data
 */
export async function initializeGtfsData(useCached = true) {
  // Check if we have cached data
  if (useCached) {
    const cachedData = localStorage.getItem('gtfsData');
    if (cachedData) {
      try {
        const parsed = JSON.parse(cachedData);
        const cacheTimestamp = parsed.timestamp || 0;
        const now = Date.now();
        
        // Use cache if it's less than 24 hours old
        if (now - cacheTimestamp < 24 * 60 * 60 * 1000) {
          console.log('Using cached GTFS data');
          return parsed.data;
        }
      } catch (e) {
        console.warn('Failed to parse cached GTFS data:', e);
      }
    }
  }

  // Download and process all data
  const gtfsData = {};
  
  try {
    // Start with the most important data
    gtfsData.main = await fetchMainGtfsData();
    
    // Cache the data
    localStorage.setItem('gtfsData', JSON.stringify({
      timestamp: Date.now(),
      data: gtfsData
    }));
    
    return gtfsData;
  } catch (error) {
    console.error('Error initializing GTFS data:', error);
    throw error;
  }
}
