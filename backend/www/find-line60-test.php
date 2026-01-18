<?php
header("Content-Type: text/html");
?>
<!DOCTYPE html>
<html>
<head>
    <title>Line 60 Route Simple Test</title>
    <style>
        body { font-family: Arial, sans-serif; padding: 20px; }
    </style>
</head>
<body>
    <h1>Line 60 Route Access Test</h1>
    <p>If you can see this page, the PHP script is working correctly.</p>
    <p>Time: <?php echo date('Y-m-d H:i:s'); ?></p>
    <p><a href="/api/stops-data.php?line=60">View stops data as JSON</a></p>
    
    <?php
    // Display server information for debugging
    echo "<h2>Server Information</h2>";
    echo "<ul>";
    echo "<li>PHP Version: " . phpversion() . "</li>";
    echo "<li>Server Software: " . $_SERVER['SERVER_SOFTWARE'] . "</li>";
    echo "<li>Document Root: " . $_SERVER['DOCUMENT_ROOT'] . "</li>";
    echo "<li>Script Filename: " . $_SERVER['SCRIPT_FILENAME'] . "</li>";
    echo "</ul>";
    ?>
</body>
</html>
