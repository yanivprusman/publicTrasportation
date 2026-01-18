<?php
header("Content-Type: application/json");
header("Access-Control-Allow-Origin: *");
error_reporting(E_ALL);
ini_set('display_errors', 1);

// Get the data directory
$dataDir = __DIR__ . '/../israel-public-transportation';

$output = [
    "status" => "diagnostic",
    "timestamp" => date("Y-m-d H:i:s")
];

// Check for routes.txt
$routesFile = $dataDir . '/routes.txt';
if (!file_exists($routesFile)) {
    $output["error"] = "Routes file not found";
    $output["path"] = $routesFile;
    echo json_encode($output, JSON_PRETTY_PRINT);
    exit;
}

// Process specific line if requested
$lineNumber = isset($_GET['line']) ? $_GET['line'] : '60'; // Default to 60

// Get file info
$output["file_info"] = [
    "path" => $routesFile,
    "size" => filesize($routesFile),
    "modified" => date("Y-m-d H:i:s", filemtime($routesFile)),
    "readable" => is_readable($routesFile)
];

try {
    // Check for BOM character at the beginning of the file
    $firstBytes = file_get_contents($routesFile, false, null, 0, 3);
    $hasBom = false;
    
    // Check for UTF-8 BOM (EF BB BF)
    if (bin2hex($firstBytes) === 'efbbbf') {
        $output["bom"] = [
            "present" => true,
            "type" => "UTF-8 BOM (EF BB BF)",
            "hex" => bin2hex($firstBytes)
        ];
        $hasBom = true;
    } else {
        $output["bom"] = [
            "present" => false
        ];
    }
    
    // Read the header to get column indices
    if (($handle = fopen($routesFile, "r")) === FALSE) {
        throw new Exception("Unable to open routes file");
    }
    
    // Get header and column indices
    $header = fgetcsv($handle);
    if (!$header) {
        fclose($handle);
        throw new Exception("Empty or invalid header in routes file");
    }
    
    // Show raw header with character codes to detect invisible characters
    $rawHeader = [];
    foreach ($header as $column) {
        $chars = [];
        for ($i = 0; $i < mb_strlen($column); $i++) {
            $char = mb_substr($column, $i, 1);
            $code = dechex(mb_ord($char));
            $chars[] = "$char (\\u$code)";
        }
        $rawHeader[] = [
            "value" => $column,
            "characters" => $chars,
            "cleaned" => preg_replace('/[\x{FEFF}\x{200B}]/u', '', $column)
        ];
    }
    
    $output["raw_header"] = $rawHeader;
    
    // Clean header columns
    foreach ($header as &$column) {
        $column = preg_replace('/[\x{FEFF}\x{200B}]/u', '', $column);
        $column = trim($column);
    }
    
    $output["cleaned_header"] = $header;
    
    // Find column indices
    $route_id_index = array_search('route_id', $header);
    $route_short_name_index = array_search('route_short_name', $header);
    
    // If exact match fails, try case-insensitive
    if ($route_id_index === false) {
        foreach ($header as $index => $column) {
            if (strcasecmp($column, 'route_id') === 0) {
                $route_id_index = $index;
                break;
            }
        }
    }
    
    if ($route_short_name_index === false) {
        foreach ($header as $index => $column) {
            if (strcasecmp($column, 'route_short_name') === 0) {
                $route_short_name_index = $index;
                break;
            }
        }
    }
    
    $output["column_indices"] = [
        "route_id" => $route_id_index,
        "route_short_name" => $route_short_name_index
    ];
    
    // Get samples of the specified line
    $line_entries = [];
    $count = 0;
    
    // Reset file pointer
    rewind($handle);
    fgetcsv($handle); // Skip header
    
    while (($data = fgetcsv($handle)) !== FALSE && $count < 10) {
        if (isset($data[$route_short_name_index]) && trim($data[$route_short_name_index]) === trim($lineNumber)) {
            $line_entries[] = $data;
            $count++;
        }
    }
    
    fclose($handle);
    
    $output["line_data"] = [
        "line_number" => $lineNumber,
        "count" => $count,
        "samples" => $line_entries
    ];
    
    // Check if we can find trips and shapes for this line
    $firstRouteId = !empty($line_entries) ? $line_entries[0][$route_id_index] : null;
    
    if ($firstRouteId) {
        $output["first_route_id"] = $firstRouteId;
        
        // Check trips
        $tripsFile = $dataDir . '/trips.txt';
        if (file_exists($tripsFile)) {
            if (($handle = fopen($tripsFile, "r")) !== FALSE) {
                $header = fgetcsv($handle);
                $trip_route_id_index = array_search('route_id', $header);
                $shape_id_index = array_search('shape_id', $header);
                
                if ($trip_route_id_index !== false && $shape_id_index !== false) {
                    $matching_trips = [];
                    $count = 0;
                    
                    while (($data = fgetcsv($handle)) !== FALSE && $count < 5) {
                        if ($data[$trip_route_id_index] === $firstRouteId) {
                            $matching_trips[] = [
                                "trip_id" => $data[0],
                                "shape_id" => $data[$shape_id_index]
                            ];
                            $count++;
                        }
                    }
                    
                    fclose($handle);
                    $output["matching_trips"] = $matching_trips;
                    
                    // Check if we have shape data
                    if (!empty($matching_trips) && !empty($matching_trips[0]["shape_id"])) {
                        $shape_id = $matching_trips[0]["shape_id"];
                        $output["first_shape_id"] = $shape_id;
                        
                        // Count shape points without loading all of them
                        $command = "grep -c \"^$shape_id,\" \"$dataDir/shapes.txt\"";
                        $point_count = trim(shell_exec($command));
                        $output["shape_point_count"] = intval($point_count);
                        
                        // Get a few sample shape points
                        $command = "grep -m5 \"^$shape_id,\" \"$dataDir/shapes.txt\"";
                        $sample_points = explode("\n", trim(shell_exec($command)));
                        $output["sample_shape_points"] = $sample_points;
                    }
                }
            }
        }
    }
    
    echo json_encode($output, JSON_PRETTY_PRINT);
    
} catch (Exception $e) {
    $output["error"] = $e->getMessage();
    echo json_encode($output, JSON_PRETTY_PRINT);
}
?>
