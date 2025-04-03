<?php
header("Content-Type: text/html");
error_reporting(E_ALL);
ini_set('display_errors', 1);

// Configuration
$dataDir = __DIR__ . '/../israel-public-transportation';
$lineNumber = isset($_GET['line']) ? $_GET['line'] : '60';

echo "<html><head><title>Line $lineNumber Stops</title>";
echo "<style>
    body { font-family: Arial, sans-serif; margin: 20px; }
    table { border-collapse: collapse; width: 100%; margin-bottom: 30px; }
    th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
    th { background-color: #f2f2f2; }
    tr:nth-child(even) { background-color: #f9f9f9; }
    h1 { color: #333; }
    h2 { color: #555; margin-top: 30px; }
    .error { color: red; font-weight: bold; }
    .success { color: green; }
    pre { background-color: #f5f5f5; padding: 10px; overflow-x: auto; }
    .map { height: 500px; width: 100%; margin-top: 20px; }
</style>
<script src='https://unpkg.com/leaflet@1.7.1/dist/leaflet.js'></script>
<link rel='stylesheet' href='https://unpkg.com/leaflet@1.7.1/dist/leaflet.css' />
</head><body>";

echo "<h1>Stops for Line $lineNumber</h1>";

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
        
        // Find column indices
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
    
    echo "<p>Found " . count($routeIds) . " routes for line $lineNumber: " . implode(', ', $routeIds) . "</p>";
    
    // Step 2: Find trip IDs for those routes
    $tripIds = [];
    $tripDirections = [];
    $tripsFile = "$dataDir/trips.txt";
    
    if (!file_exists($tripsFile)) {
        throw new Exception("Trips file not found");
    }
    
    if (($handle = fopen($tripsFile, "r")) !== false) {
        $header = fgetcsv($handle, 1000, ",");
        
        $tripIdIndex = array_search('trip_id', $header);
        $routeIdIndex = array_search('route_id', $header);
        $directionIdIndex = array_search('direction_id', $header);
        $headsignIndex = array_search('trip_headsign', $header);
        
        while (($data = fgetcsv($handle, 1000, ",")) !== false) {
            if (in_array($data[$routeIdIndex], $routeIds)) {
                $tripIds[] = $data[$tripIdIndex];
                $tripDirections[$data[$tripIdIndex]] = [
                    'direction' => $data[$directionIdIndex] ?? 'unknown',
                    'headsign' => $data[$headsignIndex] ?? 'unknown'
                ];
            }
        }
        fclose($handle);
    }
    
    if (empty($tripIds)) {
        throw new Exception("No trips found for line $lineNumber");
    }
    
    echo "<p>Found " . count($tripIds) . " trips</p>";
    
    // Step 3: Get all stops for each trip
    $tripStops = [];
    $stopTimesFile = "$dataDir/stop_times.txt";
    
    if (!file_exists($stopTimesFile)) {
        throw new Exception("Stop times file not found");
    }
    
    if (($handle = fopen($stopTimesFile, "r")) !== false) {
        $header = fgetcsv($handle, 1000, ",");
        
        $tripIdIndex = array_search('trip_id', $header);
        $stopIdIndex = array_search('stop_id', $header);
        $stopSequenceIndex = array_search('stop_sequence', $header);
        
        while (($data = fgetcsv($handle, 1000, ",")) !== false) {
            $tripId = $data[$tripIdIndex];
            if (in_array($tripId, $tripIds)) {
                if (!isset($tripStops[$tripId])) {
                    $tripStops[$tripId] = [];
                }
                $tripStops[$tripId][] = [
                    'stop_id' => $data[$stopIdIndex],
                    'sequence' => $data[$stopSequenceIndex]
                ];
            }
        }
        fclose($handle);
    }
    
    // Sort stops by sequence for each trip
    foreach ($tripStops as &$stops) {
        usort($stops, function($a, $b) {
            return $a['sequence'] - $b['sequence'];
        });
    }
    
    // Step 4: Get stop details from stops.txt
    $stopDetails = [];
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
            $stopDetails[$data[$stopIdIndex]] = [
                'name' => $data[$stopNameIndex],
                'lat' => (float)$data[$stopLatIndex],
                'lon' => (float)$data[$stopLonIndex]
            ];
        }
        fclose($handle);
    }
    
    // Step 5: Display trip stops grouped by direction
    $directionStops = [
        '0' => [],
        '1' => []
    ];
    
    // Take the first trip for each direction as reference
    $processedTrips = [
        '0' => false,
        '1' => false
    ];
    
    foreach ($tripStops as $tripId => $stops) {
        $direction = $tripDirections[$tripId]['direction'];
        
        // Skip if we already processed a trip for this direction
        if ($processedTrips[$direction]) {
            continue;
        }
        
        $processedTrips[$direction] = true;
        
        foreach ($stops as $stop) {
            $stopId = $stop['stop_id'];
            if (isset($stopDetails[$stopId])) {
                $directionStops[$direction][] = [
                    'id' => $stopId,
                    'name' => $stopDetails[$stopId]['name'],
                    'lat' => $stopDetails[$stopId]['lat'],
                    'lon' => $stopDetails[$stopId]['lon'],
                    'sequence' => $stop['sequence']
                ];
            }
        }
    }
    
    // Display for each direction
    foreach ($directionStops as $direction => $stops) {
        if (!empty($stops)) {
            echo "<h2>Direction $direction</h2>";
            
            // Display the table of stops
            echo "<table>";
            echo "<tr><th>Sequence</th><th>Stop ID</th><th>Name</th><th>Latitude</th><th>Longitude</th></tr>";
            
            foreach ($stops as $stop) {
                echo "<tr>";
                echo "<td>{$stop['sequence']}</td>";
                echo "<td>{$stop['id']}</td>";
                echo "<td>{$stop['name']}</td>";
                echo "<td>{$stop['lat']}</td>";
                echo "<td>{$stop['lon']}</td>";
                echo "</tr>";
            }
            
            echo "</table>";
            
            // Calculate middle point
            if (count($stops) > 0) {
                $middleIndex = floor(count($stops) / 2);
                $middlePoint = $stops[$middleIndex];
                echo "<p><strong>Middle Stop: </strong> {$middlePoint['name']} (ID: {$middlePoint['id']}) at coordinates: {$middlePoint['lat']}, {$middlePoint['lon']}</p>";
            }
            
            // Add a map showing the stops
            echo "<div id='map-$direction' class='map'></div>";
            echo "<script>
                document.addEventListener('DOMContentLoaded', function() {
                    var map = L.map('map-$direction').setView([32.09, 34.78], 11);
                    
                    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                        attribution: '&copy; <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a> contributors'
                    }).addTo(map);
                    
                    var stops = " . json_encode($stops) . ";
                    var markers = [];
                    var latlngs = [];
                    
                    stops.forEach(function(stop, index) {
                        var latLng = [stop.lat, stop.lon];
                        latlngs.push(latLng);
                        
                        var marker = L.marker(latLng).addTo(map);
                        marker.bindPopup(index + ': ' + stop.name + ' (ID: ' + stop.id + ')');
                        markers.push(marker);
                        
                        if (index === Math.floor(stops.length/2)) {
                            marker.setIcon(L.icon({
                                iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-violet.png',
                                iconRetinaUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-violet.png',
                                shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
                                iconSize: [25, 41],
                                iconAnchor: [12, 41]
                            }));
                            marker.openPopup();
                        }
                    });
                    
                    var polyline = L.polyline(latlngs, {color: 'red'}).addTo(map);
                    map.fitBounds(polyline.getBounds());
                });
            </script>";
        }
    }

} catch (Exception $e) {
    echo "<p class='error'>" . $e->getMessage() . "</p>";
}

echo "</body></html>";
?>
