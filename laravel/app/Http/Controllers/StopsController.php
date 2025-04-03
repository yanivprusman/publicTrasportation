<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use App\Services\GtfsService;

class StopsController extends Controller
{
    protected $gtfsService;
    
    public function __construct(GtfsService $gtfsService)
    {
        $this->gtfsService = $gtfsService;
    }

    public function getStops(Request $request)
    {
        $line = $request->query('line');
        $direction = $request->query('direction');
        
        if (!$line) {
            return response()->json(['error' => 'Missing line parameter'], 400);
        }
        
        try {
            $stops = $this->gtfsService->getStopsForLine($line, $direction);
            return response()->json([
                'line' => $line,
                'direction' => $direction,
                'routes' => $stops
            ]);
        } catch (\Exception $e) {
            return response()->json(['error' => $e->getMessage()], 500);
        }
    }
}
