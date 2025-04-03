<?php
header("Content-Type: application/json");
header("Access-Control-Allow-Origin: *");
error_reporting(E_ALL);
ini_set('display_errors', 1);

// Basic environment information
$info = [
    'status' => 'ok',
    'timestamp' => date('Y-m-d H:i:s'),
    'php_version' => phpversion(),
    'server' => $_SERVER['SERVER_SOFTWARE'],
    'document_root' => $_SERVER['DOCUMENT_ROOT']
];

// Check file paths
$dataDir = __DIR__ . '/../israel-public-transportation';
$info['paths'] = [
    'script_dir' => __DIR__,
    'data_dir' => $dataDir,
    'data_dir_exists' => is_dir($dataDir),
    'routes_file_exists' => file_exists($dataDir . '/routes.txt'),
    'shapes_file_exists' => file_exists($dataDir . '/shapes.txt'),
    'trips_file_exists' => file_exists($dataDir . '/trips.txt')
];

// Test shape function existence
$info['functions'] = [
    'getLineShape_exists' => function_exists('getLineShape') ? false : 'Not loaded yet'
];

// Check included files
$info['included_files'] = get_included_files();

// Now try to load the required files
try {
    require_once __DIR__ . '/gtfs-core.php';
    $info['functions']['require_core'] = 'OK';
    $info['core_loaded'] = true;
    
    require_once __DIR__ . '/gtfs-shapes-base.php';
    $info['functions']['require_shapes_base'] = 'OK';
    
    require_once __DIR__ . '/gtfs-shapes-lines.php';
    $info['functions']['require_shapes_lines'] = 'OK';
    
    require_once __DIR__ . '/gtfs-shapes.php';
    $info['functions']['require_shapes'] = 'OK';
    $info['functions']['getLineShape_exists'] = function_exists('getLineShape');
} catch (Exception $e) {
    $info['error'] = [
        'message' => $e->getMessage(),
        'file' => $e->getFile(),
        'line' => $e->getLine()
    ];
}

echo json_encode($info, JSON_PRETTY_PRINT);
