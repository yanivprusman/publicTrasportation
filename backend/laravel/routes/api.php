<?php

use Illuminate\Http\Request;
use Illuminate\Support\Facades\Route;
use App\Http\Controllers\GtfsController;
use App\Http\Controllers\TransportController;
use App\Http\Controllers\ShapeController;
use App\Http\Controllers\StopsController;

/*
|--------------------------------------------------------------------------
| API Routes
|--------------------------------------------------------------------------
|
| Here is where you can register API routes for your application.
|
*/

// Modern RESTful API routes
Route::prefix('gtfs')->group(function () {
    Route::get('/status', [GtfsController::class, 'status']);
    Route::get('/update', [GtfsController::class, 'update']);
    Route::get('/stops', [GtfsController::class, 'getStops']);
    Route::get('/routes', [GtfsController::class, 'getRoutes']);
    Route::get('/departures', [GtfsController::class, 'getDepartures']);
    Route::get('/shapes', [GtfsController::class, 'getShapes']);
});

// Add new route for detailed line search
Route::get('/routes/search', [GtfsController::class, 'searchRoutesByLineNumber']);

Route::get('/shapes/{line}', [ShapeController::class, 'getLineShape']);
Route::get('/stops-data', [StopsController::class, 'getStops']);
Route::get('/transport', [TransportController::class, 'getStationArrivals']);

// Legacy endpoint support (for backward compatibility)
Route::get('gtfs-api.php', [GtfsController::class, 'handleLegacyRequest']);
Route::get('gtfs-shape-api.php', [ShapeController::class, 'getLineShape']);
Route::get('simple-shape-api.php', [ShapeController::class, 'getLineShape']);
Route::get('stops-data.php', [StopsController::class, 'getStops']);
Route::get('transport.php', [TransportController::class, 'getStationArrivals']);
