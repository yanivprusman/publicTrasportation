<?php
// Functions for retrieving line shapes from GTFS data

require_once __DIR__ . '/gtfs-shapes-base.php';

/**
 * Process route IDs for a line number
 * @param string $line_number Line number to search for
 * @return array Array of route IDs
 */
function findRouteIdsByLineNumber($line_number) {
    global $dataDir;
    shapeLog("Finding route IDs for line: $line_number");
    
    $routesFile = $dataDir . '/routes.txt';
    $route_ids = [];
    
    try {
        if (($handle = fopen($routesFile, "r")) === FALSE) {
            shapeLog("ERROR: Unable to open routes file");
            throw new Exception("Unable to open routes file");
        }
        
        // Read and clean header row
        $header = fgetcsv($handle);
        foreach ($header as &$column) {
            // Remove BOM and whitespace
            $column = preg_replace('/[\x{FEFF}\x{200B}]/u', '', $column);
            $column = trim($column);
        }
        
        // Find column indices
        $routeIdIndex = -1;
        $routeShortNameIndex = -1;
        
        foreach ($header as $index => $column) {
            if (strcasecmp($column, 'route_id') === 0) $routeIdIndex = $index;
            if (strcasecmp($column, 'route_short_name') === 0) $routeShortNameIndex = $index;
        }
        
        if ($routeIdIndex === -1 || $routeShortNameIndex === -1) {
            fclose($handle);
            shapeLog("ERROR: Required columns missing in routes file");
            throw new Exception("Invalid GTFS routes file format - missing columns");
        }
        
        shapeLog("Found column indices: route_id=$routeIdIndex, route_short_name=$routeShortNameIndex");
        
        // Search for matching routes
        while (($data = fgetcsv($handle)) !== FALSE) {
            if (isset($data[$routeShortNameIndex]) && trim($data[$routeShortNameIndex]) === trim($line_number)) {
                $route_ids[] = $data[$routeIdIndex];
                shapeLog("Found matching route_id: " . $data[$routeIdIndex]);
            }
        }
        fclose($handle);
        
        return $route_ids;
    } catch (Exception $e) {
        shapeLog("ERROR: " . $e->getMessage());
        throw $e;
    }
}

/**
 * Find shape IDs for a set of route IDs
 * @param array $route_ids Array of route IDs
 * @return array Shape IDs by direction
 */
function findShapeIdsByRouteIds($route_ids) {
    global $dataDir;
    shapeLog("Finding shape IDs for route IDs: " . implode(',', $route_ids));
    
    $tripsFile = $dataDir . '/trips.txt';
    $shape_ids = [];
    
    try {
        if (($handle = fopen($tripsFile, "r")) === FALSE) {
            shapeLog("ERROR: Unable to open trips file");
            throw new Exception("Unable to open trips file");
        }
        
        // Read and clean header
        $header = fgetcsv($handle);
        foreach ($header as &$column) {
            $column = preg_replace('/[\x{FEFF}\x{200B}]/u', '', $column);
            $column = trim($column);
        }
        
        // Find column indices
        $tripRouteIdIndex = -1;
        $shapeIdIndex = -1;
        $directionIdIndex = -1;
        
        foreach ($header as $index => $column) {
            if (strcasecmp($column, 'route_id') === 0) $tripRouteIdIndex = $index;
            if (strcasecmp($column, 'shape_id') === 0) $shapeIdIndex = $index;
            if (strcasecmp($column, 'direction_id') === 0) $directionIdIndex = $index;
        }
        
        if ($tripRouteIdIndex === -1 || $shapeIdIndex === -1) {
            fclose($handle);
            shapeLog("ERROR: Required columns missing from trips file");
            throw new Exception("Invalid GTFS trips file format - missing columns");
        }
        
        // Process trips
        $processed = 0;
        
        while (($data = fgetcsv($handle)) !== FALSE) {
            if (in_array($data[$tripRouteIdIndex], $route_ids)) {
                if (!empty($data[$shapeIdIndex])) {
                    $direction = ($directionIdIndex !== -1 && isset($data[$directionIdIndex])) ? 
                        $data[$directionIdIndex] : '0';
                    
                    if (!isset($shape_ids[$direction])) {
                        $shape_ids[$direction] = [];
                    }
                    
                    // Only add unique shape IDs
                    if (!in_array($data[$shapeIdIndex], $shape_ids[$direction])) {
                        $shape_ids[$direction][] = $data[$shapeIdIndex];
                    }
                }
                $processed++;
            }
        }
        fclose($handle);
        
        shapeLog("Processed $processed trips, found shapes for " . count($shape_ids) . " directions");
        return $shape_ids;
    } catch (Exception $e) {
        shapeLog("ERROR: " . $e->getMessage());
        throw $e;
    }
}

/**
 * Extract shape points for a shape ID using grep for efficiency
 * @param string $shape_id The shape ID
 * @return array Array of coordinates
 */
function extractShapePoints($shape_id) {
    global $dataDir;
    $shapesFile = $dataDir . '/shapes.txt';
    
    $command = "grep -F \"$shape_id,\" \"$shapesFile\"";
    $output = [];
    exec($command, $output);
    
    if (empty($output)) {
        shapeLog("WARNING: No shape points found for shape $shape_id");
        return [];
    }
    
    // Parse shape points
    $shape_points = [];
    foreach ($output as $line) {
        $data = str_getcsv($line);
        
        // We need at least 4 elements: shape_id, lat, lon, sequence
        if (count($data) >= 4) {
            $shape_points[] = [
                'sequence' => intval($data[3]), // Assuming shape_pt_sequence is the 4th column
                'lat' => floatval($data[1]),    // Assuming shape_pt_lat is the 2nd column
                'lon' => floatval($data[2])     // Assuming shape_pt_lon is the 3rd column
            ];
        }
    }
    
    // Sort by sequence
    usort($shape_points, function($a, $b) {
        return $a['sequence'] - $b['sequence'];
    });
    
    // Format for leaflet
    $coordinates = [];
    foreach ($shape_points as $point) {
        $coordinates[] = [$point['lat'], $point['lon']];
    }
    
    return $coordinates;
}
?>
