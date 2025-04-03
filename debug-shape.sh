#!/bin/bash

LINE_NUMBER=${1:-60}
echo "Testing shape API for line $LINE_NUMBER..."

echo -e "\nTesting direct endpoint:"
curl -s "http://localhost/simple-shape-api.php?line=$LINE_NUMBER" | jq 'keys, ."0" | length, ."1" | length'

# Create a temporary visual representation of the route
echo -e "\nCreating visual representation of the route at /tmp/shape_map.html"
cat > /tmp/shape_map.html << EOF
<!DOCTYPE html>
<html>
<head>
    <title>Route Shape Test</title>
    <meta charset="utf-8" />
    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.7.1/dist/leaflet.css" />
    <script src="https://unpkg.com/leaflet@1.7.1/dist/leaflet.js"></script>
    <style>
        body { margin: 0; }
        #map { height: 100vh; }
    </style>
</head>
<body>
    <div id="map"></div>
    <script>
        const map = L.map('map');
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        }).addTo(map);
        
        fetch('http://localhost/simple-shape-api.php?line=$LINE_NUMBER')
            .then(response => response.json())
            .then(data => {
                const shape0 = data["0"];
                const shape1 = data["1"];
                
                if (shape0 && shape0.length > 0) {
                    L.polyline(shape0, {color: 'blue', weight: 3}).addTo(map);
                    map.fitBounds(shape0);
                }
                
                if (shape1 && shape1.length > 0) {
                    L.polyline(shape1, {color: 'red', weight: 3}).addTo(map);
                }
                
                document.title = "Line $LINE_NUMBER - " + 
                    (shape0 ? shape0.length : 0) + " outbound points, " + 
                    (shape1 ? shape1.length : 0) + " inbound points";
            })
            .catch(error => console.error('Error loading shape:', error));
    </script>
</body>
</html>
EOF

chmod +x /tmp/shape_map.html
echo "Done. Open /tmp/shape_map.html in your browser to see the route."

# Copy to web directory for easy access
cp /tmp/shape_map.html /home/yaniv/1Iz3UBgvtNDVfVo/shape_test.html
echo "Or visit: http://localhost/shape_test.html"
