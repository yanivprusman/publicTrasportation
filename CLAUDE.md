# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

Real-time Israeli public transportation tracker. React SPA with Leaflet maps showing live vehicle positions, station arrivals, and route shapes using SIRI/GTFS data from the Ministry of Transportation (MOT).

## Build & Run Commands

All commands run from `frontend/public-transportation/`:

```bash
# Development (hot reload on Vite dev server)
d startApp --app pt --mode dev        # Or: cd frontend/public-transportation && npm run dev

# Production build
npm run build                          # tsc -b && vite build → outputs to dist/

# Install dependencies
d installAppDeps --app pt              # Or: cd frontend/public-transportation && npm install

# Deploy to production
d deployToProd --app pt                # Commits must be pushed first

# Check app status
d appStatus --app pt
```

No tests or linting configured.

## Ports

- **3002** (`pt-prod`): Production Express server serving `dist/` via PM2
- **3003** (`pt-dev`): Vite dev server with hot reload

Ports are managed by the daemon: `d getPort --key pt-prod`, `d getPort --key pt-dev`.

## Architecture

```
frontend/public-transportation/
├── server.js              # Express production server (CommonJS) - serves dist/ + 3 API endpoints
├── vite.config.ts         # Dev proxy: /api → localhost:3003
├── src/
│   ├── App.tsx            # Root component, owns all top-level state
│   ├── main.tsx           # React entry point
│   ├── types.ts           # TypeScript interfaces (SiriData, VehicleMarker, etc.)
│   ├── services/
│   │   └── transport-api.ts    # API client (fetchStationArrivals, fetchLineShape)
│   ├── hooks/
│   │   └── useMapHandlers.ts   # Map interaction logic (click, context menu, routing)
│   ├── utils/
│   │   └── ShapeSimplifier.ts  # Polyline decimation for large route shapes
│   └── components/
│       ├── map/            # Leaflet map: MapView, MarkersLayer, RouteLayer, MapControls, etc.
│       ├── controls/       # TransportControls (station/line input)
│       └── data-display/   # StationArrivals (arrival table)
backend/
├── php-api/               # Legacy PHP endpoints (mostly superseded by server.js)
└── israel-public-transportation/   # GTFS data files (routes.txt, trips.txt, shapes.txt)
```

CSS uses module-scoped `.module.css` files per component. Global CSS variables are in `src/index.css`.

## Server API Endpoints (server.js)

| Endpoint | Description | Key Detail |
|----------|-------------|------------|
| `GET /api/transport` | Real-time arrivals via SIRI | Requires proxy tunnel; reads port from `/tmp/pt_proxy_port` |
| `GET /api/line-shape` | Route shape from GTFS files | Parses `backend/israel-public-transportation/*.txt`; 24h in-memory cache |
| `GET /api/directions` | Driving route via OpenRouteService | Proxies to ORS API |

## Proxy Requirement

The MOT SIRI API is not directly accessible. A tunnel must be running:
```bash
d publicTransportationStartProxy    # Starts tunnel, writes port to /tmp/pt_proxy_port
```
Without this, `/api/transport` calls will fail with "Proxy not running".

## Environment Variables

Stored in `.env` at the repo root (not in `frontend/`). `server.js` loads it with `dotenv` from `__dirname + '/../../.env'`.

| Variable | Purpose |
|----------|---------|
| `MOT_API_KEY` | Ministry of Transportation SIRI API key |
| `ORS_API_KEY` | OpenRouteService directions API key |
| `GOOGLE_API_KEY` | Google Maps (frontend) |
| `PORT` | Express server port override (defaults to 5000 if unset) |

## Key Data Flow

1. **Station arrivals**: TransportControls → `fetchStationArrivals()` → `/api/transport` → MOT proxy tunnel → SIRI JSON → StationArrivals table + vehicle markers on map
2. **Route shapes**: Line number input → `fetchLineShape()` → `/api/line-shape` → server parses GTFS text files → `{ "0": [[lat,lon],...], "1": [...] }` by direction → RouteLayer polyline
3. **Directions**: Set start/destination on map → `/api/directions` → OpenRouteService → GeoJSON → blue polyline overlay

## Development Notes

- Recently migrated from Create React App to Vite. The README.md in `frontend/public-transportation/` is stale CRA boilerplate.
- In dev mode, Vite proxies `/api` to `localhost:3003` where the Express server runs. In prod, Express serves both the static files and API from the same port.
- GTFS data files (`routes.txt`, `trips.txt`, `shapes.txt`) are large. `server.js` uses `grep` via `execSync` for efficient shape extraction from `shapes.txt`.
- Production is at `/opt/prod/publicTransportation` (git worktree). Never edit prod directly.
