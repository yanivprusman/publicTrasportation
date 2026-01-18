<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use App\Services\GtfsService;

class GtfsController extends Controller
{
    protected $gtfsService;
    
    public function __construct(GtfsService $gtfsService)
    {
        $this->gtfsService = $gtfsService;
    }

    public function status()
    {
        return response()->json($this->gtfsService->checkStatus());
    }

    public function update()
    {
        return response()->json($this->gtfsService->updateData());
    }

    public function getStops()
    {
        return response()->json($this->gtfsService->getStops());
    }

    public function getRoutes()
    {
        return response()->json($this->gtfsService->getRoutes());
    }

    public function getDepartures(Request $request)
    {
        $stopId = $request->query('stop');
        return response()->json($this->gtfsService->getScheduledDepartures($stopId));
    }

    public function getShapes(Request $request)
    {
        $routeId = $request->query('route');
        return response()->json($this->gtfsService->getRouteShape($routeId));
    }

    /**
     * Search for routes by line number with detailed information
     */
    public function searchRoutesByLineNumber(Request $request)
    {
        $lineNumber = $request->query('line');
        
        if (!$lineNumber) {
            return response()->json(['error' => 'Missing line parameter'], 400);
        }
        
        try {
            $routes = $this->gtfsService->getDetailedRoutesByLineNumber($lineNumber);
            return response()->json([
                'line' => $lineNumber,
                'routes' => $routes
            ]);
        } catch (\Exception $e) {
            return response()->json(['error' => $e->getMessage()], 500);
        }
    }

    /**
     * Handle legacy endpoint requests by parsing the endpoint parameter
     */
    public function handleLegacyRequest(Request $request)
    {
        $endpoint = $request->query('endpoint', '');
        
        switch ($endpoint) {
            case 'status':
                return $this->status();
            case 'update':
                return $this->update();
            case 'stops':
                return $this->getStops();
            case 'routes':
                return $this->getRoutes();
            case 'departures':
                return $this->getDepartures($request);
            case 'shapes':
                return $this->getShapes($request);
            default:
                return response()->json(['error' => 'Invalid endpoint'], 400);
        }
    }
}
