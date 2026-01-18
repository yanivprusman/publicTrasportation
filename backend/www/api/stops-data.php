<?php
header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');

// Configuration
$dataDir = __DIR__ . '/../israel-public-transportation';
$lineNumber = isset($_GET['line']) ? $_GET['line'] : '60';
$direction = isset($_GET['direction']) ? (int)$_GET['direction'] : null;

try {
    $results = [];
    
    // Check if files exist
    if (!is_dir($dataDir)) {
        throw new Exception("GTFS data directory not found");
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
        $routeLongNameIndex = array_search('route_long_name', $header);
        
        while (($data = fgetcsv($handle, 1000, ",")) !== false) {
            if (isset($data[$routeShortNameIndex]) && trim($data[$routeShortNameIndex]) === trim($lineNumber)) {
                $routeIds[] = [
                    'id' => $data[$routeIdIndex],
                    'name' => isset($data[$routeLongNameIndex]) ? $data[$routeLongNameIndex] : ''
                ];
            }
        }
        fclose($handle);
    }
    
    if (empty($routeIds)) {
        throw new Exception("No routes found for line $lineNumber");
    }

    // For each route ID, find trips and stops
    foreach ($routeIds as $routeInfo) {
        $routeId = $routeInfo['id'];
        
        // Step 2: Find trip IDs for the route
        $tripIds = [];
        $tripDirections = [];
        $tripsFile = "$dataDir/trips.txt";
        
        if (($handle = fopen($tripsFile, "r")) !== false) {
            $header = fgetcsv($handle, 1000, ",");
            $tripIdIndex = array_search('trip_id', $header);
            $routeIdIndex = array_search('route_id', $header);
            $directionIdIndex = array_search('direction_id', $header);
            $headsignIndex = array_search('trip_headsign', $header);
            
            while (($data = fgetcsv($handle, 1000, ",")) !== false) {
                if ($data[$routeIdIndex] === $routeId) {
                    $directionId = isset($data[$directionIdIndex]) ? $data[$directionIdIndex] : '0';
                    
                    // Skip if specific direction was requested and this doesn't match
                    if ($direction !== null && $directionId != $direction) {
                        continue;
                    }
                    
                    $tripIds[] = $data[$tripIdIndex];
                    $tripDirections[$data[$tripIdIndex]] = [
                        'direction' => $directionId,
                        'headsign' => isset($data[$headsignIndex]) ? $data[$headsignIndex] : ''
                    ];
                }
            }
            fclose($handle);
        }
        
        if (empty($tripIds)) {
            continue; // No trips found for this route, try next route
        }
        
        // Take just one trip per direction for stops
        $directionTrips = [];
        foreach ($tripDirections as $tripId => $info) {
            $dirId = $info['direction'];
            if (!isset($directionTrips[$dirId])) {
                $directionTrips[$dirId] = [
                    'trip_id' => $tripId,
                    'headsign' => $info['headsign']
                ];
            }
        }
        
        // For each selected trip, get the stops
        foreach ($directionTrips as $dirId => $tripInfo) {
            $tripId = $tripInfo['trip_id'];
            $tripHeadsign = $tripInfo['headsign'];
            
            // Step 3: Get all stops for the trip
            $stopSequences = [];
            $stopTimesFile = "$dataDir/stop_times.txt";
            
            if (($handle = fopen($stopTimesFile, "r")) !== false) {
                $header = fgetcsv($handle, 1000, ",");
                $tripIdIndex = array_search('trip_id', $header);
                $stopIdIndex = array_search('stop_id', $header);
                $stopSequenceIndex = array_search('stop_sequence', $header);
                
                while (($data = fgetcsv($handle, 1000, ",")) !== false) {
                    if ($data[$tripIdIndex] === $tripId) {
                        $stopSequences[] = [
                            'stop_id' => $data[$stopIdIndex],
                            'sequence' => (int)$data[$stopSequenceIndex]
                        ];
                    }
                }
                fclose($handle);
            }
            
            // Sort stops by sequence
            usort($stopSequences, function($a, $b) {
                return $a['sequence'] - $b['sequence'];
            });
            
            // Step 4: Get stop details
            $stops = [];
            $stopsFile = "$dataDir/stops.txt";
            $stopIdsNeeded = array_column($stopSequences, 'stop_id');
            
            if (($handle = fopen($stopsFile, "r")) !== false) {
                $header = fgetcsv($handle, 1000, ",");
                $stopIdIndex = array_search('stop_id', $header);
                $stopNameIndex = array_search('stop_name', $header);
                $stopLatIndex = array_search('stop_lat', $header);
                $stopLonIndex = array_search('stop_lon', $header);
                
                while (($data = fgetcsv($handle, 1000, ",")) !== false) {
                    if (in_array($data[$stopIdIndex], $stopIdsNeeded)) {
                        $stopId = $data[$stopIdIndex];
                        $sequence = 0;
                        
                        // Find the sequence for this stop
                        foreach ($stopSequences as $seq) {
                            if ($seq['stop_id'] === $stopId) {
                                $sequence = $seq['sequence'];
                                break;
                            }
                        }
                        
                        $stops[] = [
                            'id' => $stopId,
                            'name' => $data[$stopNameIndex],
                            'lat' => (float)$data[$stopLatIndex],
                            'lon' => (float)$data[$stopLonIndex],
                            'sequence' => $sequence
                        ];
                    }
                }
                fclose($handle);
            }
            
            // Sort stops by sequence again (to match the trip order)
            usort($stops, function($a, $b) {
                return $a['sequence'] - $b['sequence'];
            });
            
            // Add this direction's stops to results
            $results[] = [
                'route_id' => $routeId,
                'route_name' => $routeInfo['name'],
                'direction' => $dirId,
                'headsign' => $tripHeadsign,
                'stops' => $stops
            ];
        }
    }
    
    // If no stops found from GTFS data, use fallback hardcoded data
    if (empty($results)) {
        $fallbackStops = [
            [
                "id" => "26472",
                "name" => "מסוף עמידר/רציפים",
                "lat" => 32.0693,
                "lon" => 34.8398,
                "sequence" => 1
            ],
            [
                "id" => "26626",
                "name" => "ביאליק/מסריק",
                "lat" => 32.0704,
                "lon" => 34.8329,
                "sequence" => 2
            ],
            [
                "id" => "26902",
                "name" => "מרכז מסחרי רמת חן",
                "lat" => 32.0729,
                "lon" => 34.8046,
                "sequence" => 3
            ],
            [
                "id" => "26904",
                "name" => "בן גוריון/דרך הטייסים",
                "lat" => 32.0672,
                "lon" => 34.7928,
                "sequence" => 4
            ],
            [
                "id" => "20832",
                "name" => "דרך השלום/דרך הטייסים",
                "lat" => 32.0676,
                "lon" => 34.7867,
                "sequence" => 5
            ]
        ];
        
        $results[] = [
            'route_id' => 'fallback',
            'route_name' => 'Line ' . $lineNumber,
            'direction' => '0',
            'headsign' => 'Unknown',
            'stops' => $fallbackStops
        ];
    }
    
    echo json_encode([
        'line' => $lineNumber,
        'routes' => $results
    ]);
    
} catch (Exception $e) {
    echo json_encode([
        'error' => $e->getMessage(),
        'line' => $lineNumber,
        'stops' => []
    ]);
}
?>
