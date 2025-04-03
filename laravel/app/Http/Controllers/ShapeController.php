<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use App\Services\ShapeService;

class ShapeController extends Controller
{
    protected $shapeService;
    
    public function __construct(ShapeService $shapeService)
    {
        $this->shapeService = $shapeService;
    }

    public function getLineShape(Request $request, $line = null)
    {
        // If line is not in the path, check query string (for legacy support)
        if (is_null($line)) {
            $line = $request->query('line');
        }
        
        if (!$line) {
            return response()->json(['error' => 'Missing line number'], 400);
        }
        
        try {
            $shapeData = $this->shapeService->getLineShape($line);
            return response()->json($shapeData);
        } catch (\Exception $e) {
            return response()->json(['error' => $e->getMessage()], 500);
        }
    }
}
