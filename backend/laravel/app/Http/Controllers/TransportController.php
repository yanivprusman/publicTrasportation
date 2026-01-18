<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use App\Services\TransportService;

class TransportController extends Controller
{
    protected $transportService;
    
    public function __construct(TransportService $transportService)
    {
        $this->transportService = $transportService;
    }

    public function getStationArrivals(Request $request)
    {
        $stationCode = $request->query('station');
        
        if (!$stationCode) {
            return response()->json(['error' => 'Missing station parameter'], 400);
        }
        
        try {
            $data = $this->transportService->fetchStationArrivals($stationCode);
            return response()->json($data);
        } catch (\Exception $e) {
            return response()->json(['error' => $e->getMessage()], 500);
        }
    }
}
