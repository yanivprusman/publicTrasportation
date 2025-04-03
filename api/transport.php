<?php
error_reporting(E_ALL);
ini_set('display_errors', 0); // Only log errors, don't display them to users

// Set headers at the beginning before any output
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET, POST, OPTIONS");
header("Access-Control-Allow-Headers: Content-Type");

// If it's an OPTIONS request (preflight), just return the headers
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    exit(0);
}

// Use /tmp directory which is typically writable by the web server
$logFile = '/tmp/transport_api_log.txt';

// Function for safe logging
function safeLog($message) {
    global $logFile;
    // Try to write to log, but don't cause errors if it fails
    @file_put_contents($logFile, date('Y-m-d H:i:s') . ' - ' . $message . PHP_EOL, FILE_APPEND);
}

$host = '185.159.74.218';
$username = 'root';
$apiKey = 'YP719171';
$baseUrl = 'http://moran.mot.gov.il:110/Channels/HTTPChannel/SmQuery/2.8/json';

// Get parameters
$station = isset($_GET['station']) ? $_GET['station'] : '26472';
$line = isset($_GET['line']) ? $_GET['line'] : null;
$format = isset($_GET['format']) ? $_GET['format'] : 'json';
$detailLevel = isset($_GET['detail']) ? $_GET['detail'] : 'calls';
$previewInterval = isset($_GET['interval']) ? $_GET['interval'] : 'PT30M';

// Build the URL
$url = "{$baseUrl}?Key={$apiKey}&MonitoringRef={$station}&StopVisitDetailLevel={$detailLevel}&PreviewInterval={$previewInterval}";

// Add line filter if provided
if ($line) {
    $url .= "&LineRef={$line}";
}

safeLog('Received request: ' . $_SERVER['REQUEST_URI']);
safeLog('Constructed URL: ' . $url);

try {
    // Execute the SSH command to call the API via the required host
    $command = "ssh -o StrictHostKeyChecking=no root@{$host} \"curl -s '{$url}'\" 2>&1";
    
    safeLog('Executing command');
    
    $output = shell_exec($command);
    
    if (empty($output)) {
        throw new Exception("No data returned from the remote server");
    }
    
    safeLog('Raw output length: ' . strlen($output));
    
    // If output starts with SSH warnings or messages, find the JSON part
    $jsonStart = strpos($output, '{');
    if ($jsonStart !== false && $jsonStart > 0) {
        $output = substr($output, $jsonStart);
        safeLog('Cleaned output - JSON start at position ' . $jsonStart);
    }
    
    $decodedOutput = json_decode($output);

    if ($decodedOutput === null) {
        safeLog('JSON decode error: ' . json_last_error_msg());
        safeLog('First 200 chars of output: ' . substr($output, 0, 200));
        
        throw new Exception("Invalid JSON received from the remote server: " . json_last_error_msg());
    }

    if ($format === 'text') {
        // Print as plain text
        header('Content-Type: text/plain');
        print_r($decodedOutput);
    } else {
        // Default to JSON
        header('Content-Type: application/json');
        echo $output; // Return the raw JSON instead of re-encoding it
    }
    
    safeLog('Request completed successfully');
    
} catch (Exception $e) {
    safeLog('Error: ' . $e->getMessage());
    
    header('HTTP/1.1 500 Internal Server Error');
    echo json_encode(["error" => $e->getMessage(), "message" => "Could not fetch data from transportation API"]);
}
?>
