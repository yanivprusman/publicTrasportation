<?php
header("Content-Type: application/json");
header("Access-Control-Allow-Origin: *");
error_reporting(E_ALL);
ini_set('display_errors', 1);

try {
    // Configuration
    $dataDir = __DIR__ . '/../israel-public-transportation';
    $lineNumber = isset($_GET['line']) ? trim($_GET['line']) : '60';

    $routesFile = "$dataDir/routes.txt";
    $tripsFile = "$dataDir/trips.txt";
    $shapesFile = "$dataDir/shapes.txt";

    // Validate GTFS files
    foreach ([$routesFile, $tripsFile, $shapesFile] as $file) {
        if (!file_exists($file)) {
            throw new Exception("Missing GTFS file: $file");
        }
    }

    // Step 1: Find route IDs for the given line number
    $routeIds = [];
    if (($handle = fopen($routesFile, "r")) !== false) {
        $header = fgetcsv($handle);
        $routeIdIndex = array_search('route_id', $header);
        $routeShortNameIndex = array_search('route_short_name', $header);

        while (($data = fgetcsv($handle)) !== false) {
            if (isset($data[$routeShortNameIndex]) && $data[$routeShortNameIndex] === $lineNumber) {
                $routeIds[] = $data[$routeIdIndex];
            }
        }
        fclose($handle);
    }

    if (empty($routeIds)) {
        throw new Exception("No routes found for line $lineNumber");
    }

    // Step 2: Find shape IDs for the route IDs
    $shapeIds = [];
    if (($handle = fopen($tripsFile, "r")) !== false) {
        $header = fgetcsv($handle);
        $routeIdIndex = array_search('route_id', $header);
        $shapeIdIndex = array_search('shape_id', $header);

        while (($data = fgetcsv($handle)) !== false) {
            if (in_array($data[$routeIdIndex], $routeIds) && !empty($data[$shapeIdIndex])) {
                $shapeIds[] = $data[$shapeIdIndex];
            }
        }
        fclose($handle);
    }

    if (empty($shapeIds)) {
        throw new Exception("No shapes found for line $lineNumber");
    }

    // Step 3: Extract shape points for the shape IDs
    $shapes = [];
    if (($handle = fopen($shapesFile, "r")) !== false) {
        $header = fgetcsv($handle);
        $shapeIdIndex = array_search('shape_id', $header);
        $latIndex = array_search('shape_pt_lat', $header);
        $lonIndex = array_search('shape_pt_lon', $header);
        $sequenceIndex = array_search('shape_pt_sequence', $header);

        while (($data = fgetcsv($handle)) !== false) {
            if (in_array($data[$shapeIdIndex], $shapeIds)) {
                $shapes[$data[$shapeIdIndex]][] = [
                    'lat' => (float)$data[$latIndex],
                    'lon' => (float)$data[$lonIndex],
                    'sequence' => (int)$data[$sequenceIndex]
                ];
            }
        }
        fclose($handle);
    }

    // Sort shape points by sequence
    foreach ($shapes as &$points) {
        usort($points, fn($a, $b) => $a['sequence'] <=> $b['sequence']);
    }

    // Format response
    $response = [];
    foreach ($shapes as $shapeId => $points) {
        $response[$shapeId] = array_map(fn($point) => [$point['lat'], $point['lon']], $points);
    }

    echo json_encode($response);
} catch (Exception $e) {
    http_response_code(500);
    echo json_encode(['error' => $e->getMessage()]);
}
?>
