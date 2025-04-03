<?php
// Script to find specific routes in GTFS data

// Configuration
$dataDir = __DIR__ . '/../israel-public-transportation';
$routesFile = $dataDir . '/routes.txt';

// Check if file exists
if (!file_exists($routesFile)) {
    echo "Error: Routes file not found at: $routesFile\n";
    exit(1);
}

// Get command line arguments or use defaults
$lineNumber = isset($argv[1]) ? $argv[1] : '60';
$location = isset($argv[2]) ? $argv[2] : 'רמת גן';

echo "Searching for line $lineNumber related to $location...\n\n";

// Open and read the routes file
if (($handle = fopen($routesFile, "r")) !== FALSE) {
    // Get the headers
    $header = fgetcsv($handle, 1000, ",");
    
    // Find relevant column indices
    $routeIdIndex = array_search('route_id', $header);
    $agencyIdIndex = array_search('agency_id', $header);
    $routeShortNameIndex = array_search('route_short_name', $header);
    $routeLongNameIndex = array_search('route_long_name', $header);
    $routeDescIndex = array_search('route_desc', $header);
    
    $matchesFound = 0;
    $matchingRoutes = [];
    
    // Search for matches
    while (($data = fgetcsv($handle, 1000, ",")) !== FALSE) {
        if ($data[$routeShortNameIndex] === $lineNumber && 
            stripos($data[$routeLongNameIndex], $location) !== false) {
            $matchesFound++;
            $matchingRoutes[] = [
                'route_id' => $data[$routeIdIndex],
                'agency_id' => $data[$agencyIdIndex],
                'route_short_name' => $data[$routeShortNameIndex],
                'route_long_name' => $data[$routeLongNameIndex],
                'route_desc' => $data[$routeDescIndex]
            ];
        }
    }
    
    fclose($handle);
    
    // Output results
    if ($matchesFound > 0) {
        echo "Found $matchesFound matching routes:\n\n";
        foreach ($matchingRoutes as $route) {
            echo "Route ID: {$route['route_id']}\n";
            echo "Agency ID: {$route['agency_id']}\n";
            echo "Line Number: {$route['route_short_name']}\n";
            echo "Route Name: {$route['route_long_name']}\n";
            echo "Route Description: {$route['route_desc']}\n";
            echo "-----------------------------------\n";
        }
    } else {
        echo "No lines found matching line $lineNumber related to $location.\n";
        
        // Suggest looking for just the line number
        echo "\nSearching for any line $lineNumber regardless of location...\n\n";
        
        // Reopen the file
        if (($handle = fopen($routesFile, "r")) !== FALSE) {
            // Skip the header
            fgetcsv($handle, 1000, ",");
            
            $allLineMatches = 0;
            
            // Just show the first 5 matches
            while (($data = fgetcsv($handle, 1000, ",")) !== FALSE && $allLineMatches < 5) {
                if ($data[$routeShortNameIndex] === $lineNumber) {
                    $allLineMatches++;
                    echo "Line $lineNumber: {$data[$routeLongNameIndex]}\n";
                    echo "Route ID: {$data[$routeIdIndex]}\n";
                    echo "-----------------------------------\n";
                }
            }
            
            fclose($handle);
        }
    }
} else {
    echo "Error: Could not open routes file.\n";
}
?>
