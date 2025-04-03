<?php
// Set proper CORS headers to allow all origins
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET, POST, OPTIONS");
header("Access-Control-Allow-Headers: Content-Type, Authorization");

// Handle preflight OPTIONS requests
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    exit(0);
}

header("Content-Type: application/json");
error_reporting(E_ALL);
ini_set('display_errors', 0);
ini_set('log_errors', 1);
ini_set('error_log', '/tmp/shape_api_errors.log');

// Create a debug log function
function logDebug($message) {
    file_put_contents('/tmp/shape_debug.log', date('Y-m-d H:i:s') . ' - ' . $message . PHP_EOL, FILE_APPEND);
}

// Start output buffer to catch errors/warnings
ob_start();

try {
    // Get line number from request
    $lineNumber = isset($_GET['line']) ? trim($_GET['line']) : '60';
    logDebug("API call received for line: $lineNumber");
    
    // Configure paths to GTFS data files
    $dataDir = __DIR__ . '/../israel-public-transportation';
    $routesFile = $dataDir . '/routes.txt';
    $tripsFile = $dataDir . '/trips.txt';
    $shapesFile = $dataDir . '/shapes.txt';
    
    // Check if the data files exist
    if (!is_dir($dataDir)) {
        throw new Exception("GTFS data directory not found at: $dataDir");
    }
    
    if (!file_exists($routesFile)) {
        throw new Exception("Routes file not found at: $routesFile");
    }
    
    if (!file_exists($tripsFile)) {
        throw new Exception("Trips file not found at: $tripsFile");
    }
    
    if (!file_exists($shapesFile)) {
        throw new Exception("Shapes file not found at: $shapesFile");
    }
    
    logDebug("All required files exist");
    
    // Step 1: Find route IDs for the line number
    $routeIds = [];
    if (($handle = fopen($routesFile, "r")) !== false) {
        $header = fgetcsv($handle, 1000, ",");
        
        // Find column indices
        $routeIdIndex = -1;
        $routeShortNameIndex = -1;
        
        foreach ($header as $index => $column) {
            $cleanColumn = preg_replace('/[\x{FEFF}\x{200B}]/u', '', trim($column));
            if (strcasecmp($cleanColumn, 'route_id') === 0) $routeIdIndex = $index;
            if (strcasecmp($cleanColumn, 'route_short_name') === 0) $routeShortNameIndex = $index;
        }
        
        logDebug("Column indices: route_id=$routeIdIndex, route_short_name=$routeShortNameIndex");
        
        if ($routeIdIndex === -1 || $routeShortNameIndex === -1) {
            throw new Exception("Required columns not found in routes.txt");
        }
        
        // Find matching routes
        while (($data = fgetcsv($handle, 1000, ",")) !== false) {
            if (isset($data[$routeShortNameIndex]) && trim($data[$routeShortNameIndex]) === $lineNumber) {
                $routeIds[] = $data[$routeIdIndex];
            }
        }
        fclose($handle);
    }
    
    if (empty($routeIds)) {
        throw new Exception("No routes found for line $lineNumber");
    }
    
    logDebug("Found " . count($routeIds) . " routes: " . implode(', ', $routeIds));
    
    // Step 2: Find shape IDs from trips
    $shapeIds = [];
    if (($handle = fopen($tripsFile, "r")) !== false) {
        $header = fgetcsv($handle, 1000, ",");
        
        // Find column indices
        $tripRouteIdIndex = -1;
        $shapeIdIndex = -1;
        $directionIdIndex = -1;
        
        foreach ($header as $index => $column) {
            $cleanColumn = preg_replace('/[\x{FEFF}\x{200B}]/u', '', trim($column));
            if (strcasecmp($cleanColumn, 'route_id') === 0) $tripRouteIdIndex = $index;
            if (strcasecmp($cleanColumn, 'shape_id') === 0) $shapeIdIndex = $index;
            if (strcasecmp($cleanColumn, 'direction_id') === 0) $directionIdIndex = $index;
        }
        
        if ($tripRouteIdIndex === -1 || $shapeIdIndex === -1) {
            throw new Exception("Required columns not found in trips.txt");
        }
        
        // Find matching trips
        while (($data = fgetcsv($handle, 1000, ",")) !== false) {
            if (in_array($data[$tripRouteIdIndex], $routeIds)) {
                if (!empty($data[$shapeIdIndex])) {
                    $direction = ($directionIdIndex !== -1 && isset($data[$directionIdIndex])) ? 
                        $data[$directionIdIndex] : '0';
                    
                    if (!isset($shapeIds[$direction])) {
                        $shapeIds[$direction] = [];
                    }
                    
                    // Only add unique shape IDs
                    if (!in_array($data[$shapeIdIndex], $shapeIds[$direction])) {
                        $shapeIds[$direction][] = $data[$shapeIdIndex];
                    }
                }
            }
        }
        fclose($handle);
    }
    
    if (empty($shapeIds)) {
        throw new Exception("No shapes found for line $lineNumber");
    }
    
    logDebug("Found shape IDs for " . count($shapeIds) . " directions");
    
    // Step 3: Get shape points
    $result = [];
    
    foreach ($shapeIds as $direction => $directionShapeIds) {
        // Just use the first shape ID for each direction
        $shapeId = $directionShapeIds[0];
        logDebug("Processing shape $shapeId for direction $direction");
        
        $points = [];
        
        // Use a more efficient approach for large files
        $command = "grep -F \"$shapeId,\" \"$shapesFile\"";
        $output = [];
        exec($command, $output);
        
        if (!empty($output)) {
            $shapePoints = [];
            
            foreach ($output as $line) {
                $data = str_getcsv($line);
                
                // Need at least 4 elements: shape_id, lat, lon, sequence
                if (count($data) >= 4) {
                    $shapePoints[] = [
                        'sequence' => intval($data[3]), 
                        'lat' => floatval($data[1]),
                        'lon' => floatval($data[2])
                    ];
                }
            }
            
            // Sort by sequence
            usort($shapePoints, function($a, $b) {
                return $a['sequence'] - $b['sequence'];
            });
            
            // Format for Leaflet
            foreach ($shapePoints as $point) {
                $points[] = [$point['lat'], $point['lon']];
            }
        }
        
        if (!empty($points)) {
            $result[$direction] = $points;
            logDebug("Added " . count($points) . " points for direction $direction");
        }
    }
    
    if (empty($result)) {
        throw new Exception("No shape points found for line $lineNumber");
    }
    
    // Clear any buffered output and return the shape
    ob_end_clean();
    echo json_encode($result);
    logDebug("API call completed successfully for line $lineNumber with " . 
        (isset($result['0']) ? count($result['0']) : 0) . " points in direction 0 and " . 
        (isset($result['1']) ? count($result['1']) : 0) . " points in direction 1");
    
} catch (Exception $e) {
    // Clear any buffered output
    ob_end_clean();
    
    // Log error
    $errorMsg = "API Error: " . $e->getMessage();
    error_log($errorMsg);
    logDebug("ERROR: " . $e->getMessage());
    
    // Return error response with clear message, but NO fallback data
    http_response_code(500);
    echo json_encode([
        'error' => $e->getMessage(),
        'message' => 'Failed to retrieve route shape data'
    ]);
}
?>
