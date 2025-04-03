<?php
header("Content-Type: application/json");
header("Access-Control-Allow-Origin: *");
error_reporting(E_ALL);
ini_set('display_errors', 1);

$output = [
    "status" => "ok",
    "timestamp" => date("Y-m-d H:i:s"),
    "environment" => [
        "php_version" => phpversion(),
        "server_software" => $_SERVER['SERVER_SOFTWARE'] ?? 'Unknown',
        "document_root" => $_SERVER['DOCUMENT_ROOT'],
        "script_filename" => $_SERVER['SCRIPT_FILENAME'],
        "request_uri" => $_SERVER['REQUEST_URI']
    ],
    "file_system" => []
];

// Check important directories
$dirs_to_check = [
    "API Directory" => __DIR__,
    "Parent Directory" => dirname(__DIR__),
    "GTFS Data" => dirname(__DIR__) . "/israel-public-transportation",
    "GTFS Cache" => __DIR__ . "/gtfs_cache"
];

foreach ($dirs_to_check as $name => $path) {
    $output['file_system'][$name] = [
        "path" => $path,
        "exists" => is_dir($path),
        "readable" => is_dir($path) && is_readable($path),
        "writable" => is_dir($path) && is_writable($path)
    ];
    
    if (is_dir($path)) {
        $files = scandir($path);
        $output['file_system'][$name]['files'] = array_slice($files, 0, 10); // Show first 10 files
    }
}

// Check if required PHP modules are loaded
$required_modules = ['json', 'curl', 'fileinfo', 'mysqli', 'pdo', 'pdo_mysql'];
$output['php_modules'] = [];

foreach ($required_modules as $module) {
    $output['php_modules'][$module] = extension_loaded($module);
}

echo json_encode($output, JSON_PRETTY_PRINT);
?>
