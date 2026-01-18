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
function safeLog($message)
{
    global $logFile;
    // Try to write to log, but don't cause errors if it fails
    @file_put_contents($logFile, date('Y-m-d H:i:s') . ' - ' . $message . PHP_EOL, FILE_APPEND);
}

$host = '185.159.74.218';
$username = 'root';
$configFile = __DIR__ . '/config.php';
if (file_exists($configFile)) {
    $config = require $configFile;
    $apiKey = isset($config['mot_api_key']) ? $config['mot_api_key'] : 'YP719171';
} else {
    // Fallback if config.php doesn't exist (e.g. initial setup)
    // In production, you should ensure config.php exists
    $apiKey = getenv('MOT_API_KEY') ?: 'YP719171';
}
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
    // Check for local proxy port
    $proxyPortFile = '/tmp/pt_proxy_port';
    if (file_exists($proxyPortFile)) {
        $port = trim(file_get_contents($proxyPortFile));
        safeLog("Using local proxy on port: " . $port);

        // Construct local URL
        // Original: http://moran.mot.gov.il:110/Channels/HTTPChannel/SmQuery/2.8/json?...
        // Proxy: http://localhost:$port/Channels/HTTPChannel/SmQuery/2.8/json?...

        $localUrl = "http://localhost:{$port}/Channels/HTTPChannel/SmQuery/2.8/json?Key={$apiKey}&MonitoringRef={$station}&StopVisitDetailLevel={$detailLevel}&PreviewInterval={$previewInterval}";
        if ($line) {
            $localUrl .= "&LineRef={$line}";
        }

        safeLog('Fetching from local proxy: ' . $localUrl);

        // Add Host header because MOT server rejects localhost Host header
        $opts = [
            'http' => [
                'method' => 'GET',
                'header' => "Host: moran.mot.gov.il:110\r\n"
            ]
        ];
        $context = stream_context_create($opts);
        $output = @file_get_contents($localUrl, false, $context);

        if ($output === false) {
            throw new Exception("Failed to fetch from local proxy");
        }

    } else {
        // Fallback or error - let's error for now to enforce proxy usage as requested
        throw new Exception("Proxy not running (port file not found). Run 'd publicTransportationStartProxy'.");
    }

    safeLog('Raw output length: ' . strlen($output));

    $decodedOutput = json_decode($output);

    if ($decodedOutput === null) {
        safeLog('JSON decode error: ' . json_last_error_msg());
        safeLog('First 200 chars of output: ' . substr($output, 0, 200));

        throw new Exception("Invalid JSON received from server");
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