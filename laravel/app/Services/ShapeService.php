<?php

namespace App\Services;

use Illuminate\Support\Facades\Log;
use Illuminate\Support\Facades\Cache;

class ShapeService
{
    protected $gtfsService;
    
    public function __construct(GtfsService $gtfsService)
    {
        $this->gtfsService = $gtfsService;
    }
    
    /**
     * Get shape data for a line
     *
     * @param string $lineNumber
     * @return array
     */
    public function getLineShape($lineNumber)
    {
        // Cache key for this line shape
        $cacheKey = "line_shape_{$lineNumber}";
        
        // Cache for 24 hours as shapes rarely change
        if (Cache::has($cacheKey)) {
            return Cache::get($cacheKey);
        }
        
        try {
            Log::info("Fetching shape for line {$lineNumber}");
            
            // Step 1: Find route IDs for this line number
            $routeIds = $this->gtfsService->findRouteIdsByLineNumber($lineNumber);
            
            if (empty($routeIds)) {
                throw new \Exception("No routes found for line {$lineNumber}");
            }
            
            Log::info("Found route IDs for line {$lineNumber}: " . implode(", ", $routeIds));
            
            // Step 2: Find shape IDs for these routes, grouped by direction
            $shapeIds = $this->gtfsService->findShapeIdsByRouteIds($routeIds);
            
            if (empty($shapeIds)) {
                throw new \Exception("No shapes found for line {$lineNumber}");
            }
            
            // Step 3: Get shape points for each shape ID
            // Group by direction (0 = outbound, 1 = inbound)
            $shapes = [
                '0' => [],  // Outbound
                '1' => []   // Inbound
            ];
            
            // For each direction, get the first shape only
            foreach ($shapeIds as $direction => $directionShapeIds) {
                if (!empty($directionShapeIds)) {
                    // Just use the first shape per direction for simplicity
                    $primaryShapeId = $directionShapeIds[0];
                    $shapes[$direction] = $this->gtfsService->extractShapePoints($primaryShapeId);
                }
            }
            
            // Cache the results
            Cache::put($cacheKey, $shapes, now()->addDay());
            
            return $shapes;
            
        } catch (\Exception $e) {
            Log::error("Error getting line shape: " . $e->getMessage());
            throw $e;
        }
    }
}
