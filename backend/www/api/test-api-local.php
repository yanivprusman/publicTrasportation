<?php
header("Content-Type: application/json");
header("Access-Control-Allow-Origin: *");
error_reporting(E_ALL);
ini_set('display_errors', 1);

// Start output buffering to catch any warnings or notices
ob_start();

try {
    // Include core files in correct order
    require_once __DIR__ . '/gtfs-core.php';
    require_once __DIR__ . '/gtfs-shapes-base.php';
    require_once __DIR__ . '/gtfs-shapes-lines.php';
    require_once __DIR__ . '/gtfs-shapes.php';
    
    // Get line number from query parameter
    $lineNumber = isset($_GET['line']) ? $_GET['line'] : '60';
    
    // Capture output
    $output = ob_get_clean();
    $warnings = !empty($output) ? $output : null;
    
    // Call the function directly
    $result = getLineShape($lineNumber);
    
    $response = [
        'status' => 'success',
        'line' => $lineNumber,
        'data' => $result,
        'directions_found' => is_array($result) ? count($result) : 0
    ];
    
    if ($warnings) {
        $response['warnings'] = $warnings;
    }
    
    echo json_encode($response);
} catch (Throwable $e) {
    // Clean output buffer
    ob_end_clean();
    
    // Log the error
    error_log("API Error: " . $e->getMessage() . " in " . $e->getFile() . " on line " . $e->getLine());
    
    // Return error response
    http_response_code(500);
    echo json_encode([
        'status' => 'error',
        'message' => $e->getMessage(),
        'file' => $e->getFile(),
        'line' => $e->getLine(),
        'trace' => explode("\n", $e->getTraceAsString())
    ]);
}
?>
