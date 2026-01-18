<?php
header("Content-Type: text/html");
error_reporting(E_ALL);
ini_set('display_errors', 1);

// Configuration
$dataDir = __DIR__ . '/israel-public-transportation';

echo "<html><head><title>GTFS File Analysis</title>";
echo "<style>
    body { font-family: Arial, sans-serif; margin: 20px; }
    table { border-collapse: collapse; width: 100%; margin-bottom: 30px; }
    th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
    th { background-color: #f2f2f2; }
    tr:nth-child(even) { background-color: #f9f9f9; }
    h1 { color: #333; }
    h2 { color: #555; margin-top: 30px; }
    .error { color: red; font-weight: bold; }
    .success { color: green; }
    pre { background-color: #f5f5f5; padding: 10px; overflow-x: auto; }
</style></head><body>";

echo "<h1>GTFS File Analysis</h1>";

// Check if directory exists
if (!is_dir($dataDir)) {
    echo "<p class='error'>Error: Directory $dataDir does not exist.</p>";
    exit;
}

// Get all text files in the directory
$files = glob("$dataDir/*.txt");

if (empty($files)) {
    echo "<p class='error'>No .txt files found in $dataDir</p>";
    exit;
}

echo "<p>Found " . count($files) . " GTFS files in $dataDir</p>";

// Analyze each file
foreach ($files as $file) {
    $filename = basename($file);
    echo "<h2>File: $filename</h2>";
    
    // Basic file info
    $filesize = filesize($file);
    $modified = date("Y-m-d H:i:s", filemtime($file));
    
    echo "<p>Size: " . number_format($filesize) . " bytes | Last modified: $modified</p>";
    
    // Check if file is readable and not empty
    if (!is_readable($file)) {
        echo "<p class='error'>Error: File is not readable</p>";
        continue;
    }
    
    if ($filesize === 0) {
        echo "<p class='error'>Error: File is empty</p>";
        continue;
    }
    
    // Count lines
    $lineCount = 0;
    $handle = fopen($file, "r");
    while(!feof($handle)) {
        $line = fgets($handle);
        $lineCount++;
    }
    fclose($handle);
    
    echo "<p>Total lines: " . number_format($lineCount) . "</p>";
    
    // Read header and sample rows
    try {
        $handle = fopen($file, "r");
        
        // Get header
        $header = fgetcsv($handle, 0, ",");
        
        if ($header === false) {
            echo "<p class='error'>Error: Could not read header row</p>";
            continue;
        }
        
        echo "<h3>Column Headers</h3>";
        echo "<pre>" . implode(", ", $header) . "</pre>";
        
        // Column count
        $columnCount = count($header);
        echo "<p>Number of columns: $columnCount</p>";
        
        // Read sample rows
        echo "<h3>Sample Data (First 5 rows)</h3>";
        echo "<table><tr>";
        foreach ($header as $column) {
            echo "<th>" . htmlspecialchars($column) . "</th>";
        }
        echo "</tr>";
        
        $rowCount = 0;
        while (($data = fgetcsv($handle, 0, ",")) !== FALSE && $rowCount < 5) {
            echo "<tr>";
            foreach ($data as $value) {
                echo "<td>" . htmlspecialchars($value) . "</td>";
            }
            echo "</tr>";
            $rowCount++;
        }
        
        echo "</table>";
        
        // Special analysis for specific files
        if ($filename === "routes.txt") {
            // Look for line 60
            rewind($handle);
            fgetcsv($handle); // Skip header row
            
            $routeShortNameIndex = array_search('route_short_name', $header);
            $routeIdIndex = array_search('route_id', $header);
            
            if ($routeShortNameIndex !== false) {
                echo "<h3>Search for Line 60</h3>";
                $line60Found = false;
                $line60Routes = [];
                
                while (($data = fgetcsv($handle, 0, ",")) !== FALSE) {
                    if (isset($data[$routeShortNameIndex]) && $data[$routeShortNameIndex] === "60") {
                        $line60Found = true;
                        $line60Routes[] = [
                            'route_id' => isset($data[$routeIdIndex]) ? $data[$routeIdIndex] : 'N/A',
                            'row_data' => $data
                        ];
                    }
                }
                
                if ($line60Found) {
                    echo "<p class='success'>Line 60 found in routes.txt!</p>";
                    echo "<h4>Line 60 Route Details (" . count($line60Routes) . " entries found)</h4>";
                    echo "<table><tr>";
                    foreach ($header as $column) {
                        echo "<th>" . htmlspecialchars($column) . "</th>";
                    }
                    echo "</tr>";
                    
                    foreach ($line60Routes as $route) {
                        echo "<tr>";
                        foreach ($route['row_data'] as $value) {
                            echo "<td>" . htmlspecialchars($value) . "</td>";
                        }
                        echo "</tr>";
                    }
                    
                    echo "</table>";
                } else {
                    echo "<p class='error'>Line 60 not found in routes.txt</p>";
                }
            }
        }
        
        fclose($handle);
        
    } catch (Exception $e) {
        echo "<p class='error'>Error analyzing file: " . $e->getMessage() . "</p>";
    }
}

echo "</body></html>";
?>
