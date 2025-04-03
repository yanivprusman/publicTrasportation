<?php
// Core functions for GTFS data processing

error_reporting(E_ALL);
ini_set('display_errors', 0);

// Configuration
$logFile = '/tmp/gtfs_api_log.txt';
$cacheDir = __DIR__ . '/gtfs_cache';
// Updated path to use local files
$dataDir = __DIR__ . '/../israel-public-transportation';

// Create necessary directories
if (!file_exists($cacheDir)) {
    mkdir($cacheDir, 0755, true);
}

// Function for safe logging
function logMessage($message) {
    global $logFile;
    @file_put_contents($logFile, date('Y-m-d H:i:s') . ' - ' . $message . PHP_EOL, FILE_APPEND);
}

/**
 * Check if GTFS data is available and up to date
 */
function checkStatus() {
    global $cacheDir, $dataDir;
    
    $stopsFile = $cacheDir . '/stops.json';
    $routesFile = $cacheDir . '/routes.json';
    $needsUpdate = false;
    
    // Check if local data files exist
    $hasData = file_exists($dataDir . '/stops.txt') && file_exists($dataDir . '/routes.txt');
    
    // Check if cached processed files exist and are up to date
    $hasCachedData = file_exists($stopsFile) && file_exists($routesFile);
    
    if ($hasData && !$hasCachedData) {
        $needsUpdate = true;
    }
    
    echo json_encode([
        'status' => $hasData ? 'ok' : 'missing',
        'lastUpdate' => $hasCachedData ? date('Y-m-d H:i:s', filemtime($stopsFile)) : null,
        'needsUpdate' => $needsUpdate
    ]);
    
    // If update is needed, trigger processing of the data
    if ($needsUpdate) {
        updateGtfsData();
    }
}

/**
 * Update GTFS data - process local files
 */
function updateGtfsData($async = false) {
    global $dataDir;
    
    if ($async) {
        // Start a background process to update the data
        exec("php -f " . __FILE__ . " endpoint=update > /dev/null 2>&1 &");
        return;
    }
    
    logMessage("Starting GTFS data processing");
    
    // Check if source files exist
    if (!file_exists($dataDir . '/stops.txt') || !file_exists($dataDir . '/routes.txt')) {
        throw new Exception("GTFS source files not found at $dataDir");
    }
    
    // Process the data
    processStops();
    processRoutes();
    
    logMessage("GTFS data processing completed");
}

/**
 * Process GTFS stops data
 */
function processStops() {
    global $dataDir, $cacheDir;
    
    $stopsFile = $dataDir . '/stops.txt';
    $outputFile = $cacheDir . '/stops.json';
    
    if (!file_exists($stopsFile)) {
        throw new Exception("GTFS stops file not found");
    }
    
    $stops = [];
    if (($handle = fopen($stopsFile, "r")) !== false) {
        // Read header row
        $header = fgetcsv($handle, 1000, ",");
        
        // Find column indices
        $stopIdIndex = array_search('stop_id', $header);
        $stopCodeIndex = array_search('stop_code', $header);
        $stopNameIndex = array_search('stop_name', $header);
        $stopLatIndex = array_search('stop_lat', $header);
        $stopLonIndex = array_search('stop_lon', $header);
        $zoneIdIndex = array_search('zone_id', $header);
        $locationTypeIndex = array_search('location_type', $header);
        $parentStationIndex = array_search('parent_station', $header);
        
        // Process rows
        while (($data = fgetcsv($handle, 1000, ",")) !== false) {
            $stops[] = [
                'stopId' => $data[$stopIdIndex],
                'stopCode' => $data[$stopCodeIndex],
                'stopName' => $data[$stopNameIndex],
                'lat' => (float)$data[$stopLatIndex],
                'lon' => (float)$data[$stopLonIndex],
                'zoneId' => isset($data[$zoneIdIndex]) ? $data[$zoneIdIndex] : null,
                'locationType' => isset($data[$locationTypeIndex]) ? (int)$data[$locationTypeIndex] : 0,
                'parentStation' => isset($data[$parentStationIndex]) && $data[$parentStationIndex] ? $data[$parentStationIndex] : null
            ];
        }
        fclose($handle);
    }
    
    file_put_contents($outputFile, json_encode($stops));
}

/**
 * Process GTFS routes data
 */
function processRoutes() {
    global $dataDir, $cacheDir;
    
    $routesFile = $dataDir . '/routes.txt';
    $agencyFile = $dataDir . '/agency.txt';
    $outputFile = $cacheDir . '/routes.json';
    
    if (!file_exists($routesFile) || !file_exists($agencyFile)) {
        throw new Exception("GTFS routes or agency file not found");
    }
    
    // Load agency data
    $agencies = [];
    if (($handle = fopen($agencyFile, "r")) !== false) {
        $header = fgetcsv($handle, 1000, ",");
        $agencyIdIndex = array_search('agency_id', $header);
        $agencyNameIndex = array_search('agency_name', $header);
        
        while (($data = fgetcsv($handle, 1000, ",")) !== false) {
            $agencies[$data[$agencyIdIndex]] = $data[$agencyNameIndex];
        }
        fclose($handle);
    }
    
    // Process routes
    $routes = [];
    if (($handle = fopen($routesFile, "r")) !== false) {
        $header = fgetcsv($handle, 1000, ",");
        
        $routeIdIndex = array_search('route_id', $header);
        $agencyIdIndex = array_search('agency_id', $header);
        $routeShortNameIndex = array_search('route_short_name', $header);
        $routeLongNameIndex = array_search('route_long_name', $header);
        $routeDescIndex = array_search('route_desc', $header);
        $routeTypeIndex = array_search('route_type', $header);
        $routeColorIndex = array_search('route_color', $header);
        
        while (($data = fgetcsv($handle, 1000, ",")) !== false) {
            $agencyId = $data[$agencyIdIndex];
            $routes[] = [
                'routeId' => $data[$routeIdIndex],
                'agencyId' => $agencyId,
                'agencyName' => isset($agencies[$agencyId]) ? $agencies[$agencyId] : null,
                'routeShortName' => $data[$routeShortNameIndex],
                'routeLongName' => $data[$routeLongNameIndex],
                'routeDesc' => isset($data[$routeDescIndex]) ? $data[$routeDescIndex] : '',
                'routeType' => (int)$data[$routeTypeIndex],
                'routeColor' => isset($data[$routeColorIndex]) ? $data[$routeColorIndex] : ''
            ];
        }
        fclose($handle);
    }
    
    file_put_contents($outputFile, json_encode($routes));
}

/**
 * Convert GTFS time format to DateTime
 */
function convertGtfsTimeToDateTime($gtfsTime) {
    $parts = explode(':', $gtfsTime);
    $hours = (int)$parts[0];
    $minutes = (int)$parts[1];
    $seconds = (int)$parts[2];
    
    $now = new DateTime();
    $date = clone $now;
    
    // If hours > 24, it's for the next day
    if ($hours >= 24) {
        $date->add(new DateInterval('P1D'));
        $hours -= 24;
    }
    
    $date->setTime($hours, $minutes, $seconds);
    return $date;
}
?>
