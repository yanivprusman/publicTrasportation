require('dotenv').config({ path: __dirname + '/../../.env' });
const express = require('express');
const axios = require('axios');
const cors = require('cors');
const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const app = express();
const PORT = process.env.PORT || 5000;

app.use(cors());
app.use(express.json());

// Serve static files from the Vite build directory
app.use(express.static(path.join(__dirname, 'dist')));

// --- /api/transport (replaces transport.php) ---
app.get('/api/transport', async (req, res) => {
  const apiKey = process.env.MOT_API_KEY || 'YP719171';
  const station = req.query.station || '26472';
  const line = req.query.line || null;
  const detailLevel = req.query.detail || 'calls';
  const previewInterval = req.query.interval || 'PT30M';

  let url = `http://moran.mot.gov.il:110/Channels/HTTPChannel/SmQuery/2.8/json?Key=${apiKey}&MonitoringRef=${station}&StopVisitDetailLevel=${detailLevel}&PreviewInterval=${previewInterval}`;
  if (line) url += `&LineRef=${line}`;

  try {
    const proxyPortFile = '/tmp/pt_proxy_port';
    let response;

    if (fs.existsSync(proxyPortFile)) {
      const port = fs.readFileSync(proxyPortFile, 'utf8').trim();
      let localUrl = `http://localhost:${port}/Channels/HTTPChannel/SmQuery/2.8/json?Key=${apiKey}&MonitoringRef=${station}&StopVisitDetailLevel=${detailLevel}&PreviewInterval=${previewInterval}`;
      if (line) localUrl += `&LineRef=${line}`;

      response = await axios.get(localUrl, {
        headers: { 'Host': 'moran.mot.gov.il:110' },
        timeout: 10000
      });
    } else {
      throw new Error("Proxy not running (port file not found). Run 'd publicTransportationStartProxy'.");
    }

    res.json(response.data);
  } catch (error) {
    console.error('Error fetching transport data:', error.message);
    res.status(500).json({ error: error.message, message: 'Could not fetch data from transportation API' });
  }
});

// --- /api/line-shape (replaces simple-shape-api.php) ---
const shapeCache = new Map();
const SHAPE_CACHE_TTL = 24 * 60 * 60 * 1000; // 24 hours

app.get('/api/line-shape', (req, res) => {
  const lineNumber = (req.query.line || '60').trim();

  // Check cache
  const cached = shapeCache.get(lineNumber);
  if (cached && (Date.now() - cached.time < SHAPE_CACHE_TTL)) {
    return res.json(cached.data);
  }

  try {
    const dataDir = path.join(__dirname, '../../backend/israel-public-transportation');
    const routesFile = path.join(dataDir, 'routes.txt');
    const tripsFile = path.join(dataDir, 'trips.txt');
    const shapesFile = path.join(dataDir, 'shapes.txt');

    for (const f of [routesFile, tripsFile, shapesFile]) {
      if (!fs.existsSync(f)) {
        throw new Error(`GTFS file not found: ${f}`);
      }
    }

    // Step 1: Find route IDs for the line number
    const routesContent = fs.readFileSync(routesFile, 'utf8');
    const routesLines = routesContent.split('\n');
    const routesHeader = routesLines[0].replace(/\uFEFF/g, '').split(',').map(c => c.trim().toLowerCase());
    const routeIdIdx = routesHeader.indexOf('route_id');
    const routeShortNameIdx = routesHeader.indexOf('route_short_name');

    if (routeIdIdx === -1 || routeShortNameIdx === -1) {
      throw new Error('Required columns not found in routes.txt');
    }

    const routeIds = [];
    for (let i = 1; i < routesLines.length; i++) {
      if (!routesLines[i].trim()) continue;
      const cols = routesLines[i].split(',');
      if (cols[routeShortNameIdx]?.trim() === lineNumber) {
        routeIds.push(cols[routeIdIdx].trim());
      }
    }

    if (routeIds.length === 0) {
      throw new Error(`No routes found for line ${lineNumber}`);
    }

    // Step 2: Find shape IDs from trips
    const tripsContent = fs.readFileSync(tripsFile, 'utf8');
    const tripsLines = tripsContent.split('\n');
    const tripsHeader = tripsLines[0].replace(/\uFEFF/g, '').split(',').map(c => c.trim().toLowerCase());
    const tripRouteIdIdx = tripsHeader.indexOf('route_id');
    const shapeIdIdx = tripsHeader.indexOf('shape_id');
    const directionIdIdx = tripsHeader.indexOf('direction_id');

    if (tripRouteIdIdx === -1 || shapeIdIdx === -1) {
      throw new Error('Required columns not found in trips.txt');
    }

    const routeIdSet = new Set(routeIds);
    const shapeIds = {}; // { direction: [shapeId, ...] }

    for (let i = 1; i < tripsLines.length; i++) {
      if (!tripsLines[i].trim()) continue;
      const cols = tripsLines[i].split(',');
      if (routeIdSet.has(cols[tripRouteIdIdx]?.trim())) {
        const shapeId = cols[shapeIdIdx]?.trim();
        if (shapeId) {
          const direction = (directionIdIdx !== -1 && cols[directionIdIdx]) ? cols[directionIdIdx].trim() : '0';
          if (!shapeIds[direction]) shapeIds[direction] = [];
          if (!shapeIds[direction].includes(shapeId)) {
            shapeIds[direction].push(shapeId);
          }
        }
      }
    }

    if (Object.keys(shapeIds).length === 0) {
      throw new Error(`No shapes found for line ${lineNumber}`);
    }

    // Step 3: Get shape points using grep for efficiency on large files
    const result = {};

    for (const [direction, dirShapeIds] of Object.entries(shapeIds)) {
      const shapeId = dirShapeIds[0]; // Use first shape for each direction
      try {
        const grepOutput = execSync(`grep -F "${shapeId}," "${shapesFile}"`, {
          encoding: 'utf8',
          maxBuffer: 50 * 1024 * 1024
        });

        const shapePoints = [];
        for (const line of grepOutput.split('\n')) {
          if (!line.trim()) continue;
          const cols = line.split(',');
          if (cols.length >= 4) {
            shapePoints.push({
              sequence: parseInt(cols[3], 10),
              lat: parseFloat(cols[1]),
              lon: parseFloat(cols[2])
            });
          }
        }

        shapePoints.sort((a, b) => a.sequence - b.sequence);
        const points = shapePoints.map(p => [p.lat, p.lon]);

        if (points.length > 0) {
          result[direction] = points;
        }
      } catch (e) {
        console.error(`Error getting shape ${shapeId}:`, e.message);
      }
    }

    if (Object.keys(result).length === 0) {
      throw new Error(`No shape points found for line ${lineNumber}`);
    }

    // Cache the result
    shapeCache.set(lineNumber, { data: result, time: Date.now() });

    res.json(result);
  } catch (error) {
    console.error('Error fetching line shape:', error.message);
    res.status(500).json({ error: error.message, message: 'Failed to retrieve route shape data' });
  }
});

// --- /api/directions (proxy to OpenRouteService) ---
app.get('/api/directions', async (req, res) => {
  const { start, end } = req.query;
  const apiKey = process.env.ORS_API_KEY;

  try {
    const response = await axios.get(
      'https://api.openrouteservice.org/v2/directions/driving-car',
      {
        params: { api_key: apiKey, start, end },
      }
    );
    res.json(response.data);
  } catch (error) {
    console.error('Error fetching directions:', error.message);
    res.status(error.response?.status || 500).json({ error: 'Failed to fetch directions' });
  }
});

// SPA catch-all route - must be last
app.get(/(.*)/, (req, res) => {
  res.sendFile(path.join(__dirname, 'dist', 'index.html'));
});

app.listen(PORT, () => {
  console.log(`Server running on http://localhost:${PORT}`);
});
