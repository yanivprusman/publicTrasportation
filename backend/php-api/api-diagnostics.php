<?php
header("Content-Type: application/json");
header("Access-Control-Allow-Origin: *");
error_reporting(E_ALL);
ini_set('display_errors', 0);
ini_set('log_errors', 1);

// Start output buffering to catch warnings
ob_start();

// Test configuration
$tests = [
    "timestamp" => date("Y-m-d H:i:s"),
    "environment" => [
        "php_version" => phpversion(),
        "server" => $_SERVER['SERVER_SOFTWARE'] ?? 'Unknown',
        "hostname" => gethostname()
    ],
    "api_endpoints" => [],
    "data_files" => [],
    "api_response_samples" => []
];

// Test API endpoints
$endpoints = [
    "/simple-shape-api.php?line=60",
    "/api/simple-shape-api.php?line=60",
    "/api/gtfs-api.php?action=getLineShape&line=60",
    "/gtfs-api.php?action=getLineShape&line=60"
];

foreach ($endpoints as $endpoint) {
    $url = "http://" . $_SERVER['HTTP_HOST'] . $endpoint;
    $ch = curl_init($url);
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($ch, CURLOPT_TIMEOUT, 5);
    $response = curl_exec($ch);
    $info = curl_getinfo($ch);
    curl_close($ch);
    
    $tests["api_endpoints"][$endpoint] = [
        "status_code" => $info['http_code'],
        "time" => $info['total_time'],
        "size" => $info['size_download'],
        "response_sample" => $info['http_code'] == 200 ? substr($response, 0, 200) . "..." : "N/A"
    ];
}

// Check GTFS data files
$dataDir = __DIR__ . '/../israel-public-transportation';
$requiredFiles = ['routes.txt', 'trips.txt', 'shapes.txt'];

foreach ($requiredFiles as $file) {
    $fullPath = $dataDir . '/' . $file;
    $tests["data_files"][$file] = [
        "exists" => file_exists($fullPath),
        "size" => file_exists($fullPath) ? filesize($fullPath) : 0,
        "modified" => file_exists($fullPath) ? date("Y-m-d H:i:s", filemtime($fullPath)) : "N/A"
    ];
}

// Capture any warnings or notices
$warnings = ob_get_clean();
if ($warnings) {
    $tests["warnings"] = $warnings;
}

echo json_encode($tests, JSON_PRETTY_PRINT);
?>
