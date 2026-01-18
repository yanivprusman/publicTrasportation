<?php
error_reporting(E_ALL);
ini_set('display_errors', 0);

// Set headers
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET, OPTIONS");
header("Access-Control-Allow-Headers: Content-Type");

// If it's an OPTIONS request (preflight), just return the headers
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    exit(0);
}

// Simple debugging function
function logApiMessage($message) {
    file_put_contents('/tmp/gtfs_api_log.txt', date('Y-m-d H:i:s') . " - $message\n", FILE_APPEND);
}

// Include the necessary modules
require_once __DIR__ . '/gtfs-core.php';
require_once __DIR__ . '/gtfs-shapes.php';
require_once __DIR__ . '/gtfs-departures.php';

// Get query parameters
$endpoint = isset($_GET['endpoint']) ? $_GET['endpoint'] : '';
$action = isset($_GET['action']) ? $_GET['action'] : '';

// Enable better error tracking for this script
function enhancedErrorHandler($errno, $errstr, $errfile, $errline) {
    $message = date('Y-m-d H:i:s') . " - Error: [$errno] $errstr in $errfile on line $errline";
    error_log($message, 3, '/tmp/gtfs_api_errors.log');
    
    // For fatal errors, return a JSON error response
    if ($errno == E_ERROR || $errno == E_USER_ERROR) {
        header('Content-Type: application/json');
        echo json_encode(['error' => $errstr, 'file' => basename($errfile), 'line' => $errline]);
        exit(1);
    }
    
    // Return false to let PHP handle the error normally
    return false;
}
set_error_handler('enhancedErrorHandler');

try {
    // Log all requests for debugging
    $requestInfo = [
        'timestamp' => date('Y-m-d H:i:s'),
        'endpoint' => $endpoint,
        'action' => $action,
        'params' => $_GET,
        'referer' => isset($_SERVER['HTTP_REFERER']) ? $_SERVER['HTTP_REFERER'] : 'None',
        'user_agent' => isset($_SERVER['HTTP_USER_AGENT']) ? $_SERVER['HTTP_USER_AGENT'] : 'None'
    ];
    error_log(json_encode($requestInfo) . "\n", 3, '/tmp/gtfs_api_requests.log');

    // Handle API endpoints
    if (!empty($endpoint)) {
        switch($endpoint) {
            case 'status':
                checkStatus();
                break;
            
            case 'update':
                updateGtfsData();
                echo json_encode(['status' => 'success', 'message' => 'GTFS data updated']);
                break;
            
            case 'stops':
                getStops();
                break;
            
            case 'routes':
                getRoutes();
                break;
            
            case 'shapes':
                $routeId = isset($_GET['route']) ? $_GET['route'] : null;
                if (!$routeId) {
                    throw new Exception("Route ID is required");
                }
                getShapes($routeId);
                break;
                
            case 'departures':
                $stopId = isset($_GET['stop']) ? $_GET['stop'] : null;
                if (!$stopId) {
                    throw new Exception("Stop ID is required");
                }
                getDepartures($stopId);
                break;
                
            default:
                echo json_encode([
                    'status' => 'ok',
                    'endpoints' => [
                        'status' => 'Check GTFS data status',
                        'update' => 'Force update of GTFS data',
                        'stops' => 'Get all stops',
                        'routes' => 'Get all routes',
                        'shapes' => 'Get shape for a route (requires route parameter)',
                        'departures' => 'Get departures for a stop (requires stop parameter)'
                    ]
                ]);
        }
    }
    // Handle direct actions
    else if (!empty($action)) {
        switch ($action) {
            case 'getLineShape':
                if (isset($_GET['line'])) {
                    // More detailed logging for line shape requests
                    logApiMessage("Processing getLineShape request for line: " . $_GET['line']);
                    $result = getLineShape($_GET['line']);
                    
                    // Log the result summary
                    if (isset($result['error'])) {
                        logApiMessage("Error in getLineShape: " . $result['error']);
                    } else {
                        $directionCount = count($result);
                        $pointCounts = [];
                        foreach ($result as $dir => $points) {
                            $pointCounts[$dir] = count($points);
                        }
                        logApiMessage("getLineShape success: found $directionCount directions, points per direction: " . json_encode($pointCounts));
                    }
                    
                    echo json_encode($result);
                } else {
                    echo json_encode(["error" => "Line parameter required"]);
                }
                break;
                
            default:
                echo json_encode(["error" => "Unknown action"]);
        }
    }
    // Default response
    else {
        echo json_encode([
            'status' => 'ok',
            'message' => 'Israel Public Transportation GTFS API',
            'version' => '1.0',
            'usage' => [
                'endpoints' => 'Use ?endpoint=<endpoint_name> to access API endpoints',
                'actions' => 'Use ?action=<action_name> to perform specific actions'
            ]
        ]);
    }
} catch (Exception $e) {
    logMessage('Error: ' . $e->getMessage() . "\n" . $e->getTraceAsString());
    header('HTTP/1.1 500 Internal Server Error');
    echo json_encode([
        'error' => $e->getMessage(),
        'trace' => explode("\n", $e->getTraceAsString()),
        'file' => $e->getFile(),
        'line' => $e->getLine()
    ]);
}

// Restore default error handler
restore_error_handler();

// If script is called directly with endpoint parameter (for async updates)
if (PHP_SAPI === 'cli' && isset($argv[1]) && strpos($argv[1], 'endpoint=') === 0) {
    $endpoint = substr($argv[1], 9);
    $_GET['endpoint'] = $endpoint;
    
    try {
        if ($endpoint === 'update') {
            updateGtfsData(false);
            echo "GTFS data update completed\n";
        }
    } catch (Exception $e) {
        echo "Error: " . $e->getMessage() . "\n";
    }
}
?>
