<?php
// Base functions for working with GTFS shapes

require_once __DIR__ . '/gtfs-core.php';

// Set log file
$shapesLogFile = '/tmp/gtfs_shapes_log.txt';
function shapeLog($message) {
    global $shapesLogFile;
    @file_put_contents($shapesLogFile, date('Y-m-d H:i:s') . ' - ' . $message . PHP_EOL, FILE_APPEND);
}

/**
 * Get shape for a route
 */
function getShapes($routeId) {
    global $cacheDir, $dataDir;
    
    $cacheFile = $cacheDir . "/shape_${routeId}.json";
    
    // Use cached data if available and recent
    if (file_exists($cacheFile) && (time() - filemtime($cacheFile) < 86400)) {
        header('Content-Type: application/json');
        readfile($cacheFile);
        return;
    }
    
    // Find shape ID for this route from trips
    $tripsFile = $dataDir . '/trips.txt';
    if (!file_exists($tripsFile)) {
        throw new Exception("GTFS trips file not found");
    }
    
    $shapeId = null;
    if (($handle = fopen($tripsFile, "r")) !== false) {
        $header = fgetcsv($handle, 1000, ",");
        
        $routeIdIndex = array_search('route_id', $header);
        $shapeIdIndex = array_search('shape_id', $header);
        
        while (($data = fgetcsv($handle, 1000, ",")) !== false) {
            if ($data[$routeIdIndex] === $routeId && isset($data[$shapeIdIndex]) && $data[$shapeIdIndex]) {
                $shapeId = $data[$shapeIdIndex];
                break;
            }
        }
        fclose($handle);
    }
    
    if (!$shapeId) {
        echo json_encode([]);
        return;
    }
    
    // Get shape points
    $shapesFile = $dataDir . '/shapes.txt';
    if (!file_exists($shapesFile)) {
        throw new Exception("GTFS shapes file not found");
    }
    
    $points = [];
    if (($handle = fopen($shapesFile, "r")) !== false) {
        $header = fgetcsv($handle, 1000, ",");
        
        $shIdIndex = array_search('shape_id', $header);
        $latIndex = array_search('shape_pt_lat', $header);
        $lonIndex = array_search('shape_pt_lon', $header);
        $seqIndex = array_search('shape_pt_sequence', $header);
        
        $shapePoints = [];
        while (($data = fgetcsv($handle, 1000, ",")) !== false) {
            if ($data[$shIdIndex] === $shapeId) {
                $shapePoints[] = [
                    'lat' => (float)$data[$latIndex],
                    'lon' => (float)$data[$lonIndex],
                    'seq' => (int)$data[$seqIndex]
                ];
            }
        }
        fclose($handle);
        
        // Sort by sequence
        usort($shapePoints, function($a, $b) {
            return $a['seq'] - $b['seq'];
        });
        
        // Format for leaflet
        foreach ($shapePoints as $point) {
            $points[] = [$point['lat'], $point['lon']];
        }
    }
    
    // Cache the result
    file_put_contents($cacheFile, json_encode($points));
    
    header('Content-Type: application/json');
    echo json_encode($points);
}
?>
