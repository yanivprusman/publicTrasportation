<?php
header("Content-Type: application/json");
header("Access-Control-Allow-Origin: *");  // Allow cross-origin requests from your React app
error_reporting(E_ALL);
ini_set('display_errors', 0);

// Configuration
$dataDir = __DIR__ . '/../israel-public-transportation';

try {
    // Find route IDs for line 60
    $routeIds = [];
    $routesFile = "$dataDir/routes.txt";
    
    if (($handle = fopen($routesFile, "r")) !== false) {
        $header = fgetcsv($handle, 1000, ",");
        $routeIdIndex = array_search('route_id', $header);
        $routeShortNameIndex = array_search('route_short_name', $header);
        
        while (($data = fgetcsv($handle, 1000, ",")) !== false) {
            if (isset($data[$routeShortNameIndex]) && $data[$routeShortNameIndex] === '60') {
                $routeIds[] = $data[$routeIdIndex];
            }
        }
        fclose($handle);
    }
    
    // Find shape IDs for these routes
    $shapes = [];
    $tripsFile = "$dataDir/trips.txt";
    
    if (($handle = fopen($tripsFile, "r")) !== false) {
        $header = fgetcsv($handle, 1000, ",");
        $tripRouteIdIndex = array_search('route_id', $header);
        $shapeIdIndex = array_search('shape_id', $header);
        $directionIdIndex = array_search('direction_id', $header);
        
        while (($data = fgetcsv($handle, 1000, ",")) !== false) {
            if (in_array($data[$tripRouteIdIndex], $routeIds)) {
                $direction = isset($data[$directionIdIndex]) ? $data[$directionIdIndex] : '0';
                if (!isset($shapes[$direction])) {
                    $shapes[$direction] = [];
                }
                if (!in_array($data[$shapeIdIndex], $shapes[$direction])) {
                    $shapes[$direction][] = $data[$shapeIdIndex];
                }
            }
        }
        fclose($handle);
    }
    
    // Get shape points for the first shape ID from each direction
    $routeShapes = [];
    $shapesFile = "$dataDir/shapes.txt";
    
    foreach ($shapes as $direction => $shapeIds) {
        if (!empty($shapeIds)) {
            $shapeId = $shapeIds[0];
            $command = "grep -F \"$shapeId,\" \"$shapesFile\" | sort -t, -k4,4n";
            $output = [];
            exec($command, $output);
            
            $points = [];
            foreach ($output as $line) {
                $data = str_getcsv($line);
                if (count($data) >= 4) {
                    $points[] = [
                        'lat' => (float)$data[1],
                        'lon' => (float)$data[2]
                    ];
                }
            }
            
            if (!empty($points)) {
                $routeShapes[$direction] = $points;
            }
        }
    }
    
    // Return the data as JSON
    echo json_encode([
        'line' => '60',
        'routeIds' => $routeIds,
        'shapes' => $routeShapes
    ]);
    
} catch (Exception $e) {
    echo json_encode(['error' => $e->getMessage()]);
}
?>
