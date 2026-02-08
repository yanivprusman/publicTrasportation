require('dotenv').config({ path: __dirname + '/../../.env' });
const express = require('express');
const axios = require('axios');
const cors = require('cors');
const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const GTFS_DATA_DIR = path.join(__dirname, '../../backend/israel-public-transportation');
const GTFS_URL = 'https://storage.googleapis.com/storage/v1/b/mdb-latest/o/il-ministry-of-transport-and-road-safety-gtfs-2519.zip?alt=media';
const GTFS_REQUIRED_FILES = ['routes.txt', 'trips.txt', 'shapes.txt'];

let gtfsDownloading = null; // Promise while download is in progress

async function ensureGtfsData() {
  const allExist = GTFS_REQUIRED_FILES.every(f =>
    fs.existsSync(path.join(GTFS_DATA_DIR, f))
  );
  if (allExist) return;

  // If already downloading, wait for it
  if (gtfsDownloading) return gtfsDownloading;

  gtfsDownloading = (async () => {
    try {
      console.log('GTFS files missing, downloading from MOT...');
      fs.mkdirSync(GTFS_DATA_DIR, { recursive: true });
      const zipPath = path.join(GTFS_DATA_DIR, 'gtfs.zip');
      execSync(`curl -fSL -o "${zipPath}" "${GTFS_URL}"`, {
        timeout: 120000,
        stdio: ['pipe', 'pipe', 'pipe']
      });
      console.log('Extracting GTFS data...');
      execSync(`unzip -o "${zipPath}" ${GTFS_REQUIRED_FILES.join(' ')} -d "${GTFS_DATA_DIR}"`, {
        timeout: 60000,
        stdio: ['pipe', 'pipe', 'pipe']
      });
      fs.unlinkSync(zipPath);
      console.log('GTFS data ready.');
    } catch (err) {
      console.error('Failed to download GTFS data:', err.message);
      throw new Error('Failed to download GTFS data. Please manually place routes.txt, trips.txt, shapes.txt in backend/israel-public-transportation/');
    } finally {
      gtfsDownloading = null;
    }
  })();

  return gtfsDownloading;
}

const app = express();
const PORT = process.env.PORT || 5000;
const MOTIS_PORT = process.env.MOTIS_PORT || 3504;
const MOTIS_BASE = `http://localhost:${MOTIS_PORT}`;

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
      throw new Error("Proxy not running (port file not found). Run 'd startApp --app pt' to start the proxy.");
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

app.get('/api/line-shape', async (req, res) => {
  const lineNumber = (req.query.line || '60').trim();

  // Check cache
  const cached = shapeCache.get(lineNumber);
  if (cached && (Date.now() - cached.time < SHAPE_CACHE_TTL)) {
    return res.json(cached.data);
  }

  try {
    await ensureGtfsData();

    const dataDir = GTFS_DATA_DIR;
    const routesFile = path.join(dataDir, 'routes.txt');
    const tripsFile = path.join(dataDir, 'trips.txt');
    const shapesFile = path.join(dataDir, 'shapes.txt');

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

// --- Route cache (in-memory, 5min TTL, max 500 entries) ---
const routeCache = new Map();
const ROUTE_CACHE_TTL = 5 * 60 * 1000; // 5 minutes
const ROUTE_CACHE_MAX = 500;

function getCachedRoute(key) {
  const entry = routeCache.get(key);
  if (!entry) return null;
  if (Date.now() - entry.time > ROUTE_CACHE_TTL) {
    routeCache.delete(key);
    return null;
  }
  return entry.data;
}

function setCachedRoute(key, data) {
  // Evict oldest entries if at capacity
  if (routeCache.size >= ROUTE_CACHE_MAX) {
    const oldest = routeCache.keys().next().value;
    routeCache.delete(oldest);
  }
  routeCache.set(key, { data, time: Date.now() });
}

// --- Transform MOTIS plan response ---
function transformMotisResponse(motisData) {
  const itineraries = (motisData.itineraries || []).map(itin => ({
    duration: itin.duration || 0,
    startTime: itin.startTime || '',
    endTime: itin.endTime || '',
    transfers: itin.transfers || 0,
    legs: (itin.legs || []).map(leg => {
      const transformed = {
        mode: leg.mode || 'WALK',
        from: {
          name: leg.from?.name || '',
          lat: leg.from?.lat || 0,
          lon: leg.from?.lon || 0,
        },
        to: {
          name: leg.to?.name || '',
          lat: leg.to?.lat || 0,
          lon: leg.to?.lon || 0,
        },
        startTime: leg.startTime || '',
        endTime: leg.endTime || '',
        duration: leg.duration || 0,
        polyline: leg.legGeometry?.points || leg.polyline || '',
      };
      if (leg.routeShortName) transformed.routeShortName = leg.routeShortName;
      if (leg.routeColor) transformed.routeColor = leg.routeColor;
      if (leg.agencyName) transformed.agencyName = leg.agencyName;
      if (leg.intermediateStops && leg.intermediateStops.length > 0) {
        transformed.intermediateStops = leg.intermediateStops.map(stop => ({
          name: stop.name || '',
          lat: stop.lat || 0,
          lon: stop.lon || 0,
        }));
      }
      return transformed;
    }),
  }));
  return { itineraries };
}

// --- /api/route (multimodal transit routing via MOTIS) ---
app.get('/api/route', async (req, res) => {
  const { from, to, time, arriveBy } = req.query;

  if (!from || !to) {
    return res.status(400).json({ error: 'Missing required parameters: from, to (format: lat,lon)' });
  }

  const [fromLat, fromLon] = from.split(',').map(Number);
  const [toLat, toLon] = to.split(',').map(Number);

  if (isNaN(fromLat) || isNaN(fromLon) || isNaN(toLat) || isNaN(toLon)) {
    return res.status(400).json({ error: 'Invalid coordinate format. Use: lat,lon' });
  }

  const routeTime = time || new Date().toISOString();
  const isArriveBy = arriveBy === 'true';
  const cacheKey = `${from}|${to}|${routeTime}|${isArriveBy}`;

  const cached = getCachedRoute(cacheKey);
  if (cached) {
    return res.json(cached);
  }

  try {
    const motisResponse = await axios.post(`${MOTIS_BASE}/api/v1/plan`, {
      fromPlace: { lat: fromLat, lon: fromLon },
      toPlace: { lat: toLat, lon: toLon },
      time: routeTime,
      arriveBy: isArriveBy,
      transitModes: ['BUS', 'RAIL', 'TRAM'],
      numItineraries: 5,
    }, { timeout: 15000 });

    const result = transformMotisResponse(motisResponse.data);
    setCachedRoute(cacheKey, result);
    res.json(result);
  } catch (error) {
    console.error('Error fetching route from MOTIS:', error.message);
    res.status(error.response?.status || 502).json({
      error: 'Failed to fetch route',
      message: error.message,
    });
  }
});

// --- /api/geocode (location autocomplete via MOTIS) ---
app.get('/api/geocode', async (req, res) => {
  const { text } = req.query;

  if (!text) {
    return res.status(400).json({ error: 'Missing required parameter: text' });
  }

  try {
    const motisResponse = await axios.get(`${MOTIS_BASE}/api/v1/geocode`, {
      params: { text },
      timeout: 5000,
    });
    res.json(motisResponse.data);
  } catch (error) {
    console.error('Error fetching geocode from MOTIS:', error.message);
    res.status(error.response?.status || 502).json({
      error: 'Failed to geocode',
      message: error.message,
    });
  }
});

// --- /api/stoptimes (stop departures via MOTIS) ---
app.get('/api/stoptimes', async (req, res) => {
  const { stopId, n } = req.query;

  if (!stopId) {
    return res.status(400).json({ error: 'Missing required parameter: stopId' });
  }

  try {
    const motisResponse = await axios.get(`${MOTIS_BASE}/api/v1/stoptimes`, {
      params: { stopId, n: n || 20 },
      timeout: 5000,
    });
    res.json(motisResponse.data);
  } catch (error) {
    console.error('Error fetching stoptimes from MOTIS:', error.message);
    res.status(error.response?.status || 502).json({
      error: 'Failed to fetch stop times',
      message: error.message,
    });
  }
});

// --- /api/health (health check with MOTIS connectivity) ---
app.get('/api/health', async (req, res) => {
  try {
    await axios.get(`${MOTIS_BASE}/api/v1/geocode`, {
      params: { text: 'test' },
      timeout: 3000,
    });
    res.json({ status: 'ok', motis: 'connected' });
  } catch (error) {
    res.json({ status: 'degraded', motis: 'unreachable' });
  }
});

// SPA catch-all route - must be last
app.get(/(.*)/, (req, res) => {
  res.sendFile(path.join(__dirname, 'dist', 'index.html'));
});

app.listen(PORT, () => {
  console.log(`Server running on http://localhost:${PORT}`);
});
