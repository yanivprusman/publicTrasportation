<?php
// Main entry point for GTFS shapes processing

// Enable error reporting for this file
error_reporting(E_ALL);
ini_set('display_errors', 0);

require_once __DIR__ . '/gtfs-core.php';
require_once __DIR__ . '/gtfs-shapes-base.php';
require_once __DIR__ . '/gtfs-shapes-lines.php';

// Custom error handler to log issues
function shape_error_handler($errno, $errstr, $errfile, $errline) {
    $errorMsg = date('Y-m-d H:i:s') . " - Error [$errno]: $errstr in $errfile on line $errline";
    error_log($errorMsg, 3, '/tmp/gtfs_logs/shape_errors.log');
    
    // Don't execute PHP internal error handler
    return true;
}

// Set custom error handler
set_error_handler('shape_error_handler');

/**
 * Get shape for a specific line
 * @param string $line_number Line number to get shape for
 * @return array Shape data indexed by direction
 */
function getLineShape($line_number) {
    global $dataDir;
    
    // Log beginning of operation
    shapeLog("==== Starting getLineShape for line: $line_number ====");
    shapeLog("Using data directory: $dataDir");
    
    // Check data directory with detailed error handling
    if (!is_dir($dataDir)) {
        $error = "GTFS data directory not found at: $dataDir";
        shapeLog("ERROR: $error");
        return ["error" => $error];
    } else {
        shapeLog("Data directory exists and is readable: " . (is_readable($dataDir) ? 'yes' : 'no'));
    }

    if (!is_file($dataDir . '/routes.txt')) {
        $error = "Routes file not found at: " . $dataDir . '/routes.txt';
        shapeLog("ERROR: $error");
        return ["error" => $error];
    }
    
    try {
        // Step 1: Find route_ids for the line number
        shapeLog("Searching for route IDs for line $line_number");
        $route_ids = findRouteIdsByLineNumber($line_number);
        
        if (empty($route_ids)) {
            $error = "Line $line_number not found in routes.txt";
            shapeLog("ERROR: $error");
            return ["error" => $error];
        }
        
        shapeLog("Found " . count($route_ids) . " route IDs for line $line_number: " . implode(', ', $route_ids));
        
        // Step 2: Find shape_ids for the route_ids
        $shape_ids = findShapeIdsByRouteIds($route_ids);
        
        if (empty($shape_ids)) {
            $error = "No shapes found for line $line_number in trips.txt";
            shapeLog("ERROR: $error");
            return ["error" => $error];
        }
        
        // Log shape IDs found
        foreach ($shape_ids as $direction => $shape_ids_array) {
            shapeLog("Direction $direction: Found " . count($shape_ids_array) . " shape IDs");
        }
        
        // Step 3: Get shape points for each direction
        $result = [];
        
        foreach ($shape_ids as $direction => $direction_shape_ids) {
            // Just use the first shape ID for each direction
            $shape_id = $direction_shape_ids[0];
            shapeLog("Processing shape $shape_id for direction $direction");
            
            $coordinates = extractShapePoints($shape_id);
            
            if (!empty($coordinates)) {
                $result[$direction] = $coordinates;
                shapeLog("Added " . count($coordinates) . " points for direction $direction");
            } else {
                shapeLog("WARNING: No coordinates found for shape $shape_id");
            }
        }
        
        if (empty($result)) {
            $error = "No shape data available for line $line_number";
            shapeLog("ERROR: No shape coordinates found for any direction");
            return ["error" => $error];
        }
        
        // Log success
        $totalPoints = array_reduce($result, function($carry, $points) {
            return $carry + count($points);
        }, 0);
        
        shapeLog("Success! Found " . count($result) . " directions with a total of $totalPoints points for line $line_number");
        
        return $result;
    } catch (Exception $e) {
        $error = "Error processing GTFS data: " . $e->getMessage();
        shapeLog("EXCEPTION: " . $e->getMessage() . " in " . $e->getFile() . " on line " . $e->getLine());
        shapeLog("TRACE: " . $e->getTraceAsString());
        return [
            "error" => $error,
            "file" => $e->getFile(),
            "line" => $e->getLine()
        ];
    }
}

// Restore default error handler
restore_error_handler();
?>
