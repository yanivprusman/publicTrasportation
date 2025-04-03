<?php
header("Content-Type: text/plain");
error_reporting(E_ALL);
ini_set('display_errors', 1);

// Configuration
$dataDir = __DIR__ . '/../israel-public-transportation';
$lineNumber = isset($_GET['line']) ? $_GET['line'] : '60';

try {
    // Check if directory exists
    if (!is_dir($dataDir)) {
        throw new Exception("Error: Directory $dataDir does not exist.");
    }

    // Step 1: Find route IDs for the line number
    $routeIds = [];
    $routesFile = "$dataDir/routes.txt";
    if (!file_exists($routesFile)) {
        throw new Exception("Routes file not found");
    }

    if (($handle = fopen($routesFile, "r")) !== false) {
        $header = fgetcsv($handle, 1000, ",");
        $routeIdIndex = array_search('route_id', $header);
        $routeShortNameIndex = array_search('route_short_name', $header);

        while (($data = fgetcsv($handle, 1000, ",")) !== false) {
            if (isset($data[$routeShortNameIndex]) && $data[$routeShortNameIndex] == $lineNumber) {
                $routeIds[] = $data[$routeIdIndex];
            }
        }
        fclose($handle);
    }

    if (empty($routeIds)) {
        throw new Exception("No routes found for line $lineNumber");
    }

    // Step 2: Find trip IDs for those routes
    $tripIds = [];
    $tripsFile = "$dataDir/trips.txt";
    if (!file_exists($tripsFile)) {
        throw new Exception("Trips file not found");
    }

    if (($handle = fopen($tripsFile, "r")) !== false) {
        $header = fgetcsv($handle, 1000, ",");
        $tripIdIndex = array_search('trip_id', $header);
        $routeIdIndex = array_search('route_id', $header);

        while (($data = fgetcsv($handle, 1000, ",")) !== false) {
            if (in_array($data[$routeIdIndex], $routeIds)) {
                $tripIds[] = $data[$tripIdIndex];
            }
        }
        fclose($handle);
    }

    if (empty($tripIds)) {
        throw new Exception("No trips found for line $lineNumber");
    }

    // Step 3: Get all stops for each trip
    $stopIds = [];
    $stopTimesFile = "$dataDir/stop_times.txt";
    if (!file_exists($stopTimesFile)) {
        throw new Exception("Stop times file not found");
    }

    if (($handle = fopen($stopTimesFile, "r")) !== false) {
        $header = fgetcsv($handle, 1000, ",");
        $tripIdIndex = array_search('trip_id', $header);
        $stopIdIndex = array_search('stop_id', $header);

        while (($data = fgetcsv($handle, 1000, ",")) !== false) {
            if (in_array($data[$tripIdIndex], $tripIds)) {
                $stopIds[] = $data[$stopIdIndex];
            }
        }
        fclose($handle);
    }

    if (empty($stopIds)) {
        throw new Exception("No stops found for line $lineNumber");
    }

    // Step 4: Get stop details from stops.txt
    $stops = [];
    $stopsFile = "$dataDir/stops.txt";
    if (!file_exists($stopsFile)) {
        throw new Exception("Stops file not found");
    }

    if (($handle = fopen($stopsFile, "r")) !== false) {
        $header = fgetcsv($handle, 1000, ",");
        $stopIdIndex = array_search('stop_id', $header);
        $stopNameIndex = array_search('stop_name', $header);
        $stopLatIndex = array_search('stop_lat', $header);
        $stopLonIndex = array_search('stop_lon', $header);

        while (($data = fgetcsv($handle, 1000, ",")) !== false) {
            if (in_array($data[$stopIdIndex], $stopIds)) {
                $stops[] = [
                    'id' => $data[$stopIdIndex],
                    'name' => $data[$stopNameIndex],
                    'lat' => $data[$stopLatIndex],
                    'lon' => $data[$stopLonIndex]
                ];
            }
        }
        fclose($handle);
    }

    if (empty($stops)) {
        throw new Exception("No stop details found for line $lineNumber");
    }

    // Output the stops
    echo "Stops for Line $lineNumber:\n";
    foreach ($stops as $stop) {
        echo "ID: {$stop['id']}, Name: {$stop['name']}, Latitude: {$stop['lat']}, Longitude: {$stop['lon']}\n";
    }
} catch (Exception $e) {
    echo "Error: " . $e->getMessage();
}
?>
