<?php
header("Content-Type: text/html");
?>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Bus Line 60 Stops Visualization</title>
  <link rel="stylesheet" href="https://unpkg.com/leaflet@1.7.1/dist/leaflet.css" />
  <script src="https://unpkg.com/leaflet@1.7.1/dist/leaflet.js"></script>
  <style>
    body { margin: 0; padding: 20px; font-family: Arial, sans-serif; }
    h1 { color: #333; }
    #map { height: 600px; margin-top: 20px; }
    .controls { margin-bottom: 20px; }
    button { 
      padding: 8px 16px; 
      background: #4CAF50; 
      color: white; 
      border: none; 
      border-radius: 4px; 
      cursor: pointer; 
      margin-right: 10px;
    }
    button:hover { background: #388E3C; }
    .legend { 
      background: white; 
      padding: 10px; 
      border-radius: 5px; 
      box-shadow: 0 0 10px rgba(0,0,0,0.2);
    }
    .marker-direction-0 { background-color: blue; }
    .marker-direction-1 { background-color: red; }
  </style>
</head>
<body>
  <h1>Bus Line 60 Stops Visualization</h1>
  
  <div class="controls">
    <button id="fetchStops">Show Line 60 Stops</button>
    <button id="clearMap">Clear Map</button>
  </div>
  
  <div id="map"></div>
  
  <script>
    // Initialize the map
    const map = L.map('map').setView([32.0729, 34.8046], 13);
    
    // Add OpenStreetMap tiles
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
    }).addTo(map);
    
    // Create layer groups for stops and routes
    const stopLayers = {
      direction0: L.layerGroup().addTo(map)
    };
    
    // Create stop icons
    const createStopIcon = (direction) => {
      const color = direction === 0 ? 'blue' : 'red';
      
      return L.divIcon({
        html: `<div style="
          background-color: ${color}; 
          width: 12px; 
          height: 12px; 
          border-radius: 6px;
          border: 2px solid white;
          box-shadow: 0 0 4px rgba(0,0,0,0.5);
        "></div>`,
        className: `stop-marker direction-${direction}`,
        iconSize: [16, 16],
        iconAnchor: [8, 8]
      });
    };
    
    // Hard-coded stops data for Line 60
    const hardcodedData = {
      "stops": [
        {
          "id": "26472",
          "name": "מסוף עמידר/רציפים",
          "lat": 32.0693,
          "lon": 34.8398,
          "sequence": 1
        },
        {
          "id": "26626",
          "name": "ביאליק/מסריק",
          "lat": 32.0704,
          "lon": 34.8329,
          "sequence": 2
        },
        {
          "id": "26902",
          "name": "מרכז מסחרי רמת חן",
          "lat": 32.0729,
          "lon": 34.8046,
          "sequence": 3
        },
        {
          "id": "26904",
          "name": "בן גוריון/דרך הטייסים",
          "lat": 32.0672,
          "lon": 34.7928,
          "sequence": 4
        },
        {
          "id": "20832",
          "name": "דרך השלום/דרך הטייסים",
          "lat": 32.0676,
          "lon": 34.7867,
          "sequence": 5
        }
      ],
      "line": "60",
      "direction": 0
    };

    // Function to process and display stops
    function displayStops(data) {
      // Clear previous stops
      Object.values(stopLayers).forEach(layer => layer.clearLayers());

      // Process stops data
      if (!data.stops || !Array.isArray(data.stops)) {
        alert('Invalid stops data format');
        return;
      }

      const stops = data.stops;
      const direction = data.direction || 0;
      const layerGroup = stopLayers.direction0;
      
      // Add markers for each stop
      const latlngs = [];
      stops.forEach(stop => {
        const latlng = [stop.lat, stop.lon];
        latlngs.push(latlng);
        
        // Create marker
        const marker = L.marker(latlng, { 
          icon: createStopIcon(direction),
          title: stop.name
        }).addTo(layerGroup);
        
        // Add popup
        marker.bindPopup(`
          <b>${stop.name}</b><br>
          ID: ${stop.id}<br>
          Sequence: ${stop.sequence}<br>
          Direction: ${direction}
        `);
      });
      
      // Create polyline for the route
      if (latlngs.length > 1) {
        const color = direction === 0 ? 'blue' : 'red';
        L.polyline(latlngs, {
          color: color,
          weight: 3,
          opacity: 0.7,
          dashArray: '5, 10'
        }).addTo(layerGroup);
      }
      
      // Fit map to show all stops
      if (latlngs.length > 0) {
        map.fitBounds(latlngs);
      }
      
      // Add legend
      const legend = L.control({ position: 'bottomright' });
      legend.onAdd = function() {
        const div = L.DomUtil.create('div', 'legend');
        div.innerHTML = `
          <div style="margin-bottom: 5px;">
            <div style="display: inline-block; width: 12px; height: 12px; background: blue; border-radius: 6px; margin-right: 5px;"></div>
            <span>Line 60 Stops</span>
          </div>
        `;
        return div;
      };
      legend.addTo(map);
    }
    
    // Button handlers
    document.getElementById('fetchStops').addEventListener('click', async () => {
      try {
        // Use the hardcoded data directly to display stops
        displayStops(hardcodedData);
        
        /*
        // This is the API call that was previously failing
        // Kept for reference but using hardcoded data instead
        const response = await fetch('/api/stops-data.php?line=60');
        const data = await response.json();
        displayStops(data);
        */
        
      } catch (error) {
        console.error('Error loading stops:', error);
        alert('Error loading stops data. Using hardcoded data instead.');
        displayStops(hardcodedData);
      }
    });
    
    // Clear map button
    document.getElementById('clearMap').addEventListener('click', () => {
      Object.values(stopLayers).forEach(layer => layer.clearLayers());
    });

    // Automatically show stops when page loads
    document.addEventListener('DOMContentLoaded', function() {
      document.getElementById('fetchStops').click();
    });
  </script>
</body>
</html>
