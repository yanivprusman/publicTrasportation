<?php

namespace App\Services;

use Illuminate\Support\Facades\Log;
use Illuminate\Support\Facades\File;
use Illuminate\Support\Facades\Cache;

class GtfsService
{
    protected $dataDir;
    
    public function __construct()
    {
        // Set the path to GTFS data files
        $this->dataDir = storage_path('app/gtfs');
    }
    
    /**
     * Check the status of GTFS data
     * 
     * @return array
     */
    public function checkStatus()
    {
        try {
            // Check if essential files exist
            $requiredFiles = [
                'agency.txt', 
                'routes.txt', 
                'trips.txt', 
                'stops.txt', 
                'stop_times.txt',
                'shapes.txt'
            ];
            
            $missing = [];
            foreach ($requiredFiles as $file) {
                if (!File::exists($this->dataDir . '/' . $file)) {
                    $missing[] = $file;
                }
            }
            
            if (!empty($missing)) {
                return [
                    'status' => 'incomplete',
                    'missing' => $missing
                ];
            }
            
            // Check last modified date
            $routesFile = $this->dataDir . '/routes.txt';
            $lastModified = File::lastModified($routesFile);
            $daysSinceUpdate = (time() - $lastModified) / (60 * 60 * 24);
            
            return [
                'status' => 'ok',
                'lastUpdated' => date('Y-m-d H:i:s', $lastModified),
                'daysSinceUpdate' => round($daysSinceUpdate, 1)
            ];
            
        } catch (\Exception $e) {
            Log::error("Error checking GTFS status: " . $e->getMessage());
            return [
                'status' => 'error',
                'message' => $e->getMessage()
            ];
        }
    }
    
    /**
     * Update GTFS data
     * 
     * @return array
     */
    public function updateData()
    {
        // Start a background job to update GTFS data
        // This is a placeholder - you'd implement an actual download process
        
        return [
            'status' => 'initiated',
            'message' => 'GTFS update process has been started'
        ];
    }
    
    /**
     * Get all stops
     * 
     * @return array
     */
    public function getStops()
    {
        $cacheKey = 'gtfs_stops_all';
        
        if (Cache::has($cacheKey)) {
            return Cache::get($cacheKey);
        }
        
        try {
            $stopsFile = $this->dataDir . '/stops.txt';
            $stops = [];
            
            if (File::exists($stopsFile)) {
                $handle = fopen($stopsFile, "r");
                
                // Read header
                $header = fgetcsv($handle);
                $headerMap = array_flip($header);
                
                while (($data = fgetcsv($handle)) !== false) {
                    $stops[] = [
                        'stopId' => $data[$headerMap['stop_id']],
                        'stopName' => $data[$headerMap['stop_name']],
                        'lat' => (float)$data[$headerMap['stop_lat']],
                        'lon' => (float)$data[$headerMap['stop_lon']]
                    ];
                }
                
                fclose($handle);
            }
            
            Cache::put($cacheKey, $stops, now()->addHours(24));
            return $stops;
            
        } catch (\Exception $e) {
            Log::error("Error getting stops: " . $e->getMessage());
            return [];
        }
    }
    
    /**
     * Get all routes
     * 
     * @return array
     */
    public function getRoutes()
    {
        $cacheKey = 'gtfs_routes_all';
        
        if (Cache::has($cacheKey)) {
            return Cache::get($cacheKey);
        }
        
        try {
            $routesFile = $this->dataDir . '/routes.txt';
            $routes = [];
            
            if (File::exists($routesFile)) {
                $handle = fopen($routesFile, "r");
                
                // Read header
                $header = fgetcsv($handle);
                $headerMap = array_flip($header);
                
                while (($data = fgetcsv($handle)) !== false) {
                    $routes[] = [
                        'routeId' => $data[$headerMap['route_id']],
                        'routeShortName' => $data[$headerMap['route_short_name']],
                        'routeLongName' => $data[$headerMap['route_long_name']]
                    ];
                }
                
                fclose($handle);
            }
            
            Cache::put($cacheKey, $routes, now()->addHours(24));
            return $routes;
            
        } catch (\Exception $e) {
            Log::error("Error getting routes: " . $e->getMessage());
            return [];
        }
    }
    
    /**
     * Get scheduled departures for a stop
     * 
     * @param string $stopId
     * @return array
     */
    public function getScheduledDepartures($stopId)
    {
        try {
            $cacheKey = "gtfs_departures_{$stopId}";
            
            if (Cache::has($cacheKey)) {
                return Cache::get($cacheKey);
            }
            
            // Implementation would parse stop_times.txt for the specific stop
            // and join with trips.txt and routes.txt to get full information
            
            // This is a simplified implementation
            $departures = [];
            
            Cache::put($cacheKey, $departures, now()->addHours(1));
            return $departures;
            
        } catch (\Exception $e) {
            Log::error("Error getting departures: " . $e->getMessage());
            return [];
        }
    }
    
    /**
     * Get route shape
     * 
     * @param string $routeId
     * @return array
     */
    public function getRouteShape($routeId)
    {
        // Implementation would find shape_id from trips for this route,
        // then get the shape points from shapes.txt
        
        // Similar to the implementation in ShapeService
        return [];
    }
    
    // Helper methods (implemented as in the previous example)

    /**
     * Find route IDs for a line number
     *
     * @param string $lineNumber
     * @return array
     */
    public function findRouteIdsByLineNumber($lineNumber)
    {
        $routesFile = $this->dataDir . '/routes.txt';
        $routeIds = [];
        
        try {
            if (!File::exists($routesFile)) {
                throw new \Exception("Routes file not found: $routesFile");
            }
            
            $handle = fopen($routesFile, "r");
            
            // Read and clean header row
            $header = fgetcsv($handle);
            foreach ($header as &$column) {
                // Remove BOM and whitespace
                $column = preg_replace('/[\x{FEFF}\x{200B}]/u', '', $column);
                $column = trim($column);
            }
            
            // Find column indices
            $routeIdIndex = array_search('route_id', $header);
            $routeShortNameIndex = array_search('route_short_name', $header);
            
            if ($routeIdIndex === false || $routeShortNameIndex === false) {
                throw new \Exception("Required columns missing in routes file");
            }
            
            // Search for matching routes
            while (($data = fgetcsv($handle)) !== false) {
                if (isset($data[$routeShortNameIndex]) && trim($data[$routeShortNameIndex]) === trim($lineNumber)) {
                    $routeIds[] = $data[$routeIdIndex];
                }
            }
            fclose($handle);
            
        } catch (\Exception $e) {
            Log::error("Error finding route IDs: " . $e->getMessage());
            throw $e;
        }
        
        return $routeIds;
    }
    
    /**
     * Find shape IDs for route IDs
     *
     * @param array $routeIds
     * @return array
     */
    public function findShapeIdsByRouteIds($routeIds)
    {
        // Implementation as in previous example
        $tripsFile = $this->dataDir . '/trips.txt';
        $shapeIds = [];
        
        try {
            if (!File::exists($tripsFile)) {
                throw new \Exception("Trips file not found: $tripsFile");
            }
            
            $handle = fopen($tripsFile, "r");
            
            // Read and clean header
            $header = fgetcsv($handle);
            foreach ($header as &$column) {
                $column = preg_replace('/[\x{FEFF}\x{200B}]/u', '', $column);
                $column = trim($column);
            }
            
            // Find column indices
            $tripRouteIdIndex = array_search('route_id', $header);
            $shapeIdIndex = array_search('shape_id', $header);
            $directionIdIndex = array_search('direction_id', $header);
            
            if ($tripRouteIdIndex === false || $shapeIdIndex === false) {
                throw new \Exception("Required columns missing from trips file");
            }
            
            // Process trips
            while (($data = fgetcsv($handle)) !== false) {
                if (in_array($data[$tripRouteIdIndex], $routeIds)) {
                    if (!empty($data[$shapeIdIndex])) {
                        $direction = ($directionIdIndex !== false && isset($data[$directionIdIndex])) ? 
                            $data[$directionIdIndex] : '0';
                        
                        if (!isset($shapeIds[$direction])) {
                            $shapeIds[$direction] = [];
                        }
                        
                        // Only add unique shape IDs
                        if (!in_array($data[$shapeIdIndex], $shapeIds[$direction])) {
                            $shapeIds[$direction][] = $data[$shapeIdIndex];
                        }
                    }
                }
            }
            fclose($handle);
            
        } catch (\Exception $e) {
            Log::error("Error finding shape IDs: " . $e->getMessage());
            throw $e;
        }
        
        return $shapeIds;
    }
    
    /**
     * Extract shape points for a shape ID
     *
     * @param string $shapeId
     * @return array
     */
    public function extractShapePoints($shapeId)
    {
        // Implementation as in previous example
        $shapesFile = $this->dataDir . '/shapes.txt';
        
        try {
            if (!File::exists($shapesFile)) {
                throw new \Exception("Shapes file not found: $shapesFile");
            }
            
            // Use a more efficient approach for large files
            $command = "grep -F \"{$shapeId},\" \"{$shapesFile}\"";
            $output = [];
            exec($command, $output);
            
            if (empty($output)) {
                return [];
            }
            
            $shapePoints = [];
            
            foreach ($output as $line) {
                $data = str_getcsv($line);
                
                // Need at least 4 elements: shape_id, lat, lon, sequence
                if (count($data) >= 4) {
                    $shapePoints[] = [
                        'sequence' => intval($data[3]), 
                        'lat' => floatval($data[1]),
                        'lon' => floatval($data[2])
                    ];
                }
            }
            
            // Sort by sequence
            usort($shapePoints, function($a, $b) {
                return $a['sequence'] - $b['sequence'];
            });
            
            // Format for Leaflet
            $points = [];
            foreach ($shapePoints as $point) {
                $points[] = [$point['lat'], $point['lon']];
            }
            
            return $points;
            
        } catch (\Exception $e) {
            Log::error("Error extracting shape points: " . $e->getMessage());
            return [];
        }
    }
    
    /**
     * Get stops for a line number
     *
     * @param string $lineNumber
     * @param int|null $direction
     * @return array
     */
    public function getStopsForLine($lineNumber, $direction = null)
    {
        $cacheKey = "stops_line_{$lineNumber}_dir_" . ($direction ?? 'all');
        
        if (Cache::has($cacheKey)) {
            return Cache::get($cacheKey);
        }
        
        $results = [];
        
        try {
            // Implementation as in previous example
            // Find routes for line, then trips for routes, then stops for trips
            
            // This would be a complex implementation involving:
            // 1. Finding route_ids from routes.txt
            // 2. Finding trip_ids from trips.txt 
            // 3. Finding stop_sequences from stop_times.txt
            // 4. Finding stop details from stops.txt
            
            Cache::put($cacheKey, $results, now()->addHours(24));
        } catch (\Exception $e) {
            Log::error("Error getting stops for line: " . $e->getMessage());
        }
        
        return $results;
    }

    /**
     * Get detailed information about routes with a specific line number
     *
     * @param string $lineNumber
     * @return array
     */
    public function getDetailedRoutesByLineNumber($lineNumber)
    {
        $cacheKey = "detailed_routes_line_{$lineNumber}";
        
        if (Cache::has($cacheKey)) {
            return Cache::get($cacheKey);
        }
        
        try {
            $results = [];
            
            // Step 1: Find all routes with this line number
            $routesFile = $this->dataDir . '/routes.txt';
            $agencyFile = $this->dataDir . '/agency.txt';
            
            if (!File::exists($routesFile) || !File::exists($agencyFile)) {
                throw new \Exception("Required GTFS files not found");
            }
            
            // Read agencies first for lookup
            $agencies = [];
            $handle = fopen($agencyFile, "r");
            $header = fgetcsv($handle);
            $headerMap = array_flip($header);
            
            while (($data = fgetcsv($handle)) !== false) {
                $agencyId = $data[$headerMap['agency_id'] ?? 0];
                $agencies[$agencyId] = [
                    'name' => $data[$headerMap['agency_name'] ?? 1] ?? 'Unknown',
                    'url' => $data[$headerMap['agency_url'] ?? 2] ?? '',
                ];
            }
            fclose($handle);
            
            // Read routes
            $handle = fopen($routesFile, "r");
            $header = fgetcsv($handle);
            foreach ($header as &$column) {
                $column = preg_replace('/[\x{FEFF}\x{200B}]/u', '', $column);
                $column = trim($column);
            }
            $headerMap = array_flip($header);
            
            while (($data = fgetcsv($handle)) !== false) {
                $routeShortName = $data[$headerMap['route_short_name']];
                
                if (trim($routeShortName) === trim($lineNumber)) {
                    $routeId = $data[$headerMap['route_id']];
                    $routeLongName = $data[$headerMap['route_long_name']] ?? '';
                    $agencyId = $data[$headerMap['agency_id'] ?? 'agency_id'] ?? '';
                    
                    // Get trip data for this route to determine cities served
                    $cities = $this->getCitiesForRoute($routeId);
                    
                    $results[] = [
                        'route_id' => $routeId,
                        'route_short_name' => $routeShortName,
                        'route_long_name' => $routeLongName,
                        'agency' => $agencies[$agencyId] ?? ['name' => 'Unknown'],
                        'cities' => $cities
                    ];
                }
            }
            fclose($handle);
            
            Cache::put($cacheKey, $results, now()->addHours(24));
            return $results;
            
        } catch (\Exception $e) {
            Log::error("Error getting detailed routes: " . $e->getMessage());
            throw $e;
        }
    }
    
    /**
     * Get cities served by a route
     *
     * @param string $routeId
     * @return array
     */
    protected function getCitiesForRoute($routeId)
    {
        try {
            $cities = [];
            $tripsFile = $this->dataDir . '/trips.txt';
            $stopTimesFile = $this->dataDir . '/stop_times.txt';
            $stopsFile = $this->dataDir . '/stops.txt';
            
            if (!File::exists($tripsFile) || !File::exists($stopTimesFile) || !File::exists($stopsFile)) {
                return [];
            }
            
            // Get a sample trip for this route
            $tripIds = [];
            $handle = fopen($tripsFile, "r");
            $header = fgetcsv($handle);
            $headerMap = array_flip($header);
            
            while (($data = fgetcsv($handle)) !== false) {
                if ($data[$headerMap['route_id']] === $routeId) {
                    $tripIds[] = $data[$headerMap['trip_id']];
                    if (count($tripIds) >= 2) { // Get just first two trips for efficiency
                        break;
                    }
                }
            }
            fclose($handle);
            
            if (empty($tripIds)) {
                return [];
            }
            
            // Get first and last stop for this trip
            $stopIds = [];
            foreach ($tripIds as $tripId) {
                $handle = fopen($stopTimesFile, "r");
                $header = fgetcsv($handle);
                $headerMap = array_flip($header);
                
                $tripStops = [];
                while (($data = fgetcsv($handle)) !== false) {
                    if ($data[$headerMap['trip_id']] === $tripId) {
                        $tripStops[] = [
                            'stop_id' => $data[$headerMap['stop_id']],
                            'stop_sequence' => (int)$data[$headerMap['stop_sequence']]
                        ];
                    }
                }
                fclose($handle);
                
                if (!empty($tripStops)) {
                    // Sort by sequence
                    usort($tripStops, function($a, $b) {
                        return $a['stop_sequence'] - $b['stop_sequence'];
                    });
                    
                    // Get first and last stop
                    $stopIds[] = $tripStops[0]['stop_id'];
                    $stopIds[] = $tripStops[count($tripStops) - 1]['stop_id'];
                }
            }
            
            if (empty($stopIds)) {
                return [];
            }
            
            // Get city names for these stops
            $stopIds = array_unique($stopIds);
            $cityMap = [];
            
            $handle = fopen($stopsFile, "r");
            $header = fgetcsv($handle);
            $headerMap = array_flip($header);
            
            while (($data = fgetcsv($handle)) !== false) {
                $stopId = $data[$headerMap['stop_id']];
                if (in_array($stopId, $stopIds)) {
                    $stopName = $data[$headerMap['stop_name']];
                    // Extract city from stop name - usually first part before comma
                    $parts = explode(',', $stopName);
                    $city = trim($parts[0]);
                    if (!empty($city) && !in_array($city, $cityMap)) {
                        $cityMap[] = $city;
                    }
                }
            }
            fclose($handle);
            
            return $cityMap;
            
        } catch (\Exception $e) {
            Log::error("Error getting cities for route: " . $e->getMessage());
            return [];
        }
    }
}
