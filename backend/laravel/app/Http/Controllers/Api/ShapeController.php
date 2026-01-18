<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Log;
use App\Services\GtfsService;

class ShapeController extends Controller
{
    protected $gtfsService;
    
    public function __construct(GtfsService $gtfsService)
    {
        $this->gtfsService = $gtfsService;
    }
    
    /**
     * Get line shape for a specified line number
     *
     * @param Request $request
     * @return \Illuminate\Http\JsonResponse
     */
    public function getLineShape(Request $request)
    {
        $lineNumber = $request->query('line', '60');
        
        try {
            Log::info("Fetching shape for line: $lineNumber");
            
            // Get route IDs for this line
            $routeIds = $this->gtfsService->findRouteIdsByLineNumber($lineNumber);
            
            if (empty($routeIds)) {
                throw new \Exception("No routes found for line $lineNumber");
            }
            
            // Get shape IDs for these routes
            $shapeIds = $this->gtfsService->findShapeIdsByRouteIds($routeIds);
            
            if (empty($shapeIds)) {
                throw new \Exception("No shapes found for line $lineNumber");
            }
            
            // Extract shape points and organize by direction
            $result = [];
            
            foreach ($shapeIds as $direction => $directionShapeIds) {
                // Just use the first shape ID for each direction
                $shapeId = $directionShapeIds[0];
                $points = $this->gtfsService->extractShapePoints($shapeId);
                
                if (!empty($points)) {
                    $result[$direction] = $points;
                }
            }
            
            if (empty($result)) {
                throw new \Exception("No shape points found for line $lineNumber");
            }
            
            return response()->json($result);
            
        } catch (\Exception $e) {
            Log::error('Shape API error', [
                'error' => $e->getMessage(),
                'line' => $lineNumber
            ]);
            
            return response()->json([
                'error' => $e->getMessage()
            ], 500);
        }
    }
}
