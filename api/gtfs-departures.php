<?php
// Functions for working with GTFS departures data

require_once __DIR__ . '/gtfs-core.php';

/**
 * Get stops data from cache or process
 */
function getStops() {
    global $cacheDir, $dataDir;
    
    $stopsFile = $cacheDir . '/stops.json';
    
    if (!file_exists($stopsFile)) {
        processStops();
    }
    
    header('Content-Type: application/json');
    readfile($stopsFile);
}

/**
 * Get routes data from cache or process
 */
function getRoutes() {
    global $cacheDir;
    
    $routesFile = $cacheDir . '/routes.json';
    
    if (!file_exists($routesFile)) {
        processRoutes();
    }
    
    header('Content-Type: application/json');
    readfile($routesFile);
}

/**
 * Get departures for a stop
 */
function getDepartures($stopId) {
    global $cacheDir, $dataDir;
    
    $outputFile = $cacheDir . "/departures_${stopId}.json";
    $cacheExpiry = 3600; // 1 hour
    
    // Use cached data if available and fresh
    if (file_exists($outputFile) && (time() - filemtime($outputFile) < $cacheExpiry)) {
        header('Content-Type: application/json');
        readfile($outputFile);
        return;
    }
    
    // Get the current time and day of week
    $now = new DateTime();
    $currentTime = $now->format('H:i:s');
    $currentDay = strtolower($now->format('l'));
    
    // Load service days
    $calendarFile = $dataDir . '/calendar.txt';
    if (!file_exists($calendarFile)) {
        throw new Exception("GTFS calendar file not found");
    }
    
    $currentDate = $now->format('Ymd');
    $activeServiceIds = [];
    
    if (($handle = fopen($calendarFile, "r")) !== false) {
        $header = fgetcsv($handle, 1000, ",");
        
        $serviceIdIndex = array_search('service_id', $header);
        $dayIndex = array_search($currentDay, $header);
        $startDateIndex = array_search('start_date', $header);
        $endDateIndex = array_search('end_date', $header);
        
        while (($data = fgetcsv($handle, 1000, ",")) !== false) {
            if ($data[$dayIndex] == '1' && 
                $data[$startDateIndex] <= $currentDate && 
                $data[$endDateIndex] >= $currentDate) {
                $activeServiceIds[] = $data[$serviceIdIndex];
            }
        }
        fclose($handle);
    }
    
    if (empty($activeServiceIds)) {
        echo json_encode([]);
        return;
    }
    
    // Find stop times for the specified stop
    $stopTimesFile = $dataDir . '/stop_times.txt';
    if (!file_exists($stopTimesFile)) {
        throw new Exception("GTFS stop_times file not found");
    }
    
    $tripsWithTimes = [];
    if (($handle = fopen($stopTimesFile, "r")) !== false) {
        $header = fgetcsv($handle, 1000, ",");
        
        $tripIdIndex = array_search('trip_id', $header);
        $arrivalTimeIndex = array_search('arrival_time', $header);
        $departureTimeIndex = array_search('departure_time', $header);
        $stopIdIndex = array_search('stop_id', $header);
        $stopSequenceIndex = array_search('stop_sequence', $header);
        
        while (($data = fgetcsv($handle, 1000, ",")) !== false) {
            if ($data[$stopIdIndex] === $stopId) {
                $tripsWithTimes[$data[$tripIdIndex]] = [
                    'arrivalTime' => $data[$arrivalTimeIndex],
                    'departureTime' => $data[$departureTimeIndex],
                    'stopSequence' => (int)$data[$stopSequenceIndex]
                ];
            }
        }
        fclose($handle);
    }
    
    // Get route and trip information
    $tripsFile = $dataDir . '/trips.txt';
    if (!file_exists($tripsFile)) {
        throw new Exception("GTFS trips file not found");
    }
    
    $tripDetails = [];
    if (($handle = fopen($tripsFile, "r")) !== false) {
        $header = fgetcsv($handle, 1000, ",");
        
        $tripIdIndex = array_search('trip_id', $header);
        $routeIdIndex = array_search('route_id', $header);
        $serviceIdIndex = array_search('service_id', $header);
        $tripHeadsignIndex = array_search('trip_headsign', $header);
        $directionIdIndex = array_search('direction_id', $header);
        
        while (($data = fgetcsv($handle, 1000, ",")) !== false) {
            $tripId = $data[$tripIdIndex];
            
            if (isset($tripsWithTimes[$tripId]) && in_array($data[$serviceIdIndex], $activeServiceIds)) {
                $tripDetails[$tripId] = [
                    'routeId' => $data[$routeIdIndex],
                    'headsign' => $data[$tripHeadsignIndex],
                    'directionId' => $data[$directionIdIndex]
                ];
            }
        }
        fclose($handle);
    }
    
    // Get route information
    $routesFile = $dataDir . '/routes.txt';
    if (!file_exists($routesFile)) {
        throw new Exception("GTFS routes file not found");
    }
    
    $routeInfo = [];
    if (($handle = fopen($routesFile, "r")) !== false) {
        $header = fgetcsv($handle, 1000, ",");
        
        $routeIdIndex = array_search('route_id', $header);
        $routeShortNameIndex = array_search('route_short_name', $header);
        $routeLongNameIndex = array_search('route_long_name', $header);
        
        while (($data = fgetcsv($handle, 1000, ",")) !== false) {
            $routeInfo[$data[$routeIdIndex]] = [
                'routeShortName' => $data[$routeShortNameIndex],
                'routeLongName' => $data[$routeLongNameIndex]
            ];
        }
        fclose($handle);
    }
    
    // Combine all the data
    $departures = [];
    foreach ($tripDetails as $tripId => $trip) {
        $stopTime = $tripsWithTimes[$tripId];
        
        // Convert GTFS time (which may be >24h) to a proper ISO datetime
        $arrivalTime = convertGtfsTimeToDateTime($stopTime['arrivalTime']);
        
        if (isset($routeInfo[$trip['routeId']])) {
            $departures[] = [
                'tripId' => $tripId,
                'routeId' => $trip['routeId'],
                'lineNumber' => $routeInfo[$trip['routeId']]['routeShortName'],
                'destination' => $trip['headsign'],
                'scheduledArrival' => $arrivalTime->format('c'),
                'directionId' => $trip['directionId']
            ];
        }
    }
    
    // Sort by scheduled arrival time
    usort($departures, function($a, $b) {
        return strcmp($a['scheduledArrival'], $b['scheduledArrival']);
    });
    
    // Filter departures to only include future ones plus past departures within 30 minutes
    $cutoffTime = new DateTime();
    $cutoffTime->sub(new DateInterval('PT30M'));
    
    $filteredDepartures = array_filter($departures, function($departure) use ($cutoffTime) {
        $arrivalTime = new DateTime($departure['scheduledArrival']);
        return $arrivalTime > $cutoffTime;
    });
    
    // Cache the result
    file_put_contents($outputFile, json_encode(array_values($filteredDepartures)));
    
    header('Content-Type: application/json');
    echo json_encode(array_values($filteredDepartures));
}
