<?php
header("Content-Type: text/html");
error_reporting(E_ALL);
ini_set('display_errors', 1);

// Configuration
$dataDir = __DIR__ . '/israel-public-transportation';
$file = isset($_GET['file']) ? $_GET['file'] : 'trips.txt';
$limit = isset($_GET['limit']) ? intval($_GET['limit']) : 20;
$search = isset($_GET['search']) ? $_GET['search'] : '';

// Full path to the file
$filePath = $dataDir . '/' . $file;

// Ensure we're only accessing text files in the data directory
if (!preg_match('/\.txt$/i', $file) || strpos(realpath($filePath), realpath($dataDir)) !== 0) {
    $file = 'trips.txt';
    $filePath = $dataDir . '/' . $file;
}

// Basic HTML structure
echo "<!DOCTYPE html>
<html>
<head>
    <title>GTFS File Examiner</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; line-height: 1.6; }
        h1, h2 { color: #333; }
        table { border-collapse: collapse; width: 100%; max-width: 100%; margin-bottom: 20px; }
        th, td { padding: 8px; text-align: left; border: 1px solid #ddd; }
        th { background-color: #f2f2f2; position: sticky; top: 0; }
        .file-selector { margin-bottom: 20px; padding: 10px; background: #f8f8f8; border-radius: 5px; }
        .file-stats { margin-bottom: 20px; }
        .search-form { margin-bottom: 20px; padding: 10px; background: #f0f0f0; border-radius: 5px; }
        .paginator { margin: 20px 0; }
        .paginator a { padding: 5px 10px; margin-right: 5px; text-decoration: none; background: #f5f5f5; border: 1px solid #ddd; }
        .paginator a.active { background: #4CAF50; color: white; }
        .overflow-x { overflow-x: auto; }
        code { background-color: #f5f5f5; padding: 2px 5px; border-radius: 3px; }
        .even { background-color: #f9f9f9; }
    </style>
</head>
<body>
    <h1>GTFS File Examiner</h1>";

// File selector
echo "<div class='file-selector'>";
echo "<h2>Select a file to examine:</h2>";
$txtFiles = glob($dataDir . '/*.txt');
foreach ($txtFiles as $txtFile) {
    $filename = basename($txtFile);
    $selected = ($filename === $file) ? "style='font-weight:bold;'" : "";
    echo "<a href='?file={$filename}' {$selected}>{$filename}</a> | ";
}
echo "</div>";

echo "<h2>Examining: {$file}</h2>";

// File doesn't exist
if (!file_exists($filePath)) {
    echo "<p>Error: File not found: {$file}</p>";
    echo "</body></html>";
    exit;
}

// File statistics
$fileSize = filesize($filePath);
$lineCount = 0;
$handle = fopen($filePath, "r");
while (!feof($handle)) {
    $line = fgets($handle);
    $lineCount++;
}
fclose($handle);

echo "<div class='file-stats'>";
echo "<p><strong>File size:</strong> " . number_format($fileSize) . " bytes</p>";
echo "<p><strong>Total lines:</strong> " . number_format($lineCount) . "</p>";
echo "</div>";

// Search form
echo "<div class='search-form'>
    <form method='GET'>
        <input type='hidden' name='file' value='{$file}'>
        <label for='search'>Search:</label>
        <input type='text' id='search' name='search' value='{$search}' placeholder='Enter search term...'>
        <label for='limit'>Records per page:</label>
        <select name='limit' id='limit'>
            <option value='10'" . ($limit == 10 ? " selected" : "") . ">10</option>
            <option value='20'" . ($limit == 20 ? " selected" : "") . ">20</option>
            <option value='50'" . ($limit == 50 ? " selected" : "") . ">50</option>
            <option value='100'" . ($limit == 100 ? " selected" : "") . ">100</option>
        </select>
        <button type='submit'>Apply</button>
    </form>
</div>";

// Read and display the file content
$page = isset($_GET['page']) ? intval($_GET['page']) : 1;
if ($page < 1) $page = 1;

$handle = fopen($filePath, "r");
if ($handle) {
    // Read header
    $header = fgetcsv($handle, 0, ",");
    
    // Count records for pagination
    $recordCount = 0;
    $searchMatchCount = 0;
    $records = [];
    
    rewind($handle);
    $headerSkipped = false;
    
    while (($data = fgetcsv($handle, 0, ",")) !== FALSE) {
        // Skip header
        if (!$headerSkipped) {
            $headerSkipped = true;
            continue;
        }
        
        $recordCount++;
        
        // Process search if specified
        if (!empty($search)) {
            $rowMatchesSearch = false;
            foreach ($data as $value) {
                if (stripos($value, $search) !== false) {
                    $rowMatchesSearch = true;
                    break;
                }
            }
            
            if (!$rowMatchesSearch) {
                continue;
            }
        }
        
        $searchMatchCount++;
        
        // Store records for current page
        $start = ($page - 1) * $limit + 1;
        $end = $start + $limit - 1;
        
        if ($searchMatchCount >= $start && $searchMatchCount <= $end) {
            $records[] = $data;
        }
        
        // Break if we've collected enough records
        if ($searchMatchCount > $end) {
            break;
        }
    }
    
    fclose($handle);
    
    // Display table
    echo "<div class='overflow-x'>";
    echo "<table>";
    echo "<tr>";
    foreach ($header as $index => $column) {
        echo "<th>" . htmlspecialchars($column) . " <span style='color:#777'>($index)</span></th>";
    }
    echo "</tr>";
    
    foreach ($records as $index => $row) {
        echo "<tr class='" . ($index % 2 == 0 ? 'even' : '') . "'>";
        foreach ($row as $column => $value) {
            echo "<td>" . htmlspecialchars($value) . "</td>";
        }
        echo "</tr>";
    }
    echo "</table>";
    echo "</div>";
    
    // Pagination
    if ($searchMatchCount > 0) {
        $totalPages = ceil($searchMatchCount / $limit);
        echo "<div class='paginator'>";
        echo "<p>Showing records " . (($page - 1) * $limit + 1) . " to " . min($page * $limit, $searchMatchCount) . " of {$searchMatchCount}" . (!empty($search) ? " matching results" : "") . "</p>";
        
        if ($totalPages > 1) {
            echo "<p>Pages: ";
            for ($i = 1; $i <= $totalPages; $i++) {
                $active = ($i == $page) ? "class='active'" : "";
                echo "<a href='?file={$file}&page={$i}&limit={$limit}&search={$search}' {$active}>{$i}</a> ";
            }
            echo "</p>";
        }
        echo "</div>";
    } else {
        echo "<p>No records found" . (!empty($search) ? " matching '{$search}'" : "") . ".</p>";
    }
    
    // File format information
    echo "<h2>File Structure</h2>";
    echo "<p>This file is in <code>CSV</code> format. Here's the column structure:</p>";
    echo "<ul>";
    foreach ($header as $index => $column) {
        echo "<li><code>{$column}</code> (column {$index})</li>";
    }
    echo "</ul>";
    
    // GTFS Reference
    echo "<h2>GTFS Reference</h2>";
    $gtfsInfo = [
        'trips.txt' => 'Contains trip information including route ID, service ID, trip ID, and direction.',
        'stops.txt' => 'Contains information about stop locations, including stop ID, name, latitude, and longitude.',
        'routes.txt' => 'Contains route information including route ID, agency, short name, and long name.',
        'stop_times.txt' => 'Contains trip schedules with arrival and departure times for each stop.',
        'calendar.txt' => 'Contains service dates defining when service is available.',
        'agency.txt' => 'Contains information about transit agencies.',
        'fare_attributes.txt' => 'Contains fare information.',
        'fare_rules.txt' => 'Contains rules for applying fares to specific routes/zones.',
        'translations.txt' => 'Contains translations for text fields in other files.',
    ];
    
    if (isset($gtfsInfo[$file])) {
        echo "<p>{$gtfsInfo[$file]}</p>";
    }
    
} else {
    echo "<p>Error: Could not open file</p>";
}

echo "</body>
</html>";
?>
