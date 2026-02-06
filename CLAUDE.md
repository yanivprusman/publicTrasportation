# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Israel Public Transportation Tracker — a React + Express app that displays real-time bus arrivals (via Israel's MOT SIRI API) and GTFS route shapes on a Leaflet map.

## App Lifecycle (via automateLinux daemon)

This app is managed by the automateLinux daemon (app ID: `pt`). Do not start/stop services or assign ports manually.

```bash
d appStatus --app pt
d startApp --app pt --mode dev
d stopApp --app pt --mode dev
d restartApp --app pt --mode dev
d buildApp --app pt --mode dev
d installAppDeps --app pt --mode dev
d deployToProd --app pt
d getPort --key pt-dev          # get assigned dev port
d getPort --key pt-prod         # get assigned prod port
```

## Development Commands

All commands run from `frontend/public-transportation/`:

```bash
npm run dev          # Vite dev server (hot reload)
npm run build        # tsc -b && vite build
npm run preview      # preview production build
npm start            # production: node server.js (serves dist/ via Express)
```

There are no tests or linter configured.

## Proxy Requirement

The MOT real-time API (`moran.mot.gov.il:110`) is not publicly accessible. Before using real-time features:

```bash
d publicTransportationStartProxy   # starts SSH tunnel, writes port to /tmp/pt_proxy_port
```

The Express server reads `/tmp/pt_proxy_port` to route requests through the tunnel.

## Architecture

### Frontend (`frontend/public-transportation/`)

Vite + React 19 + TypeScript. Uses Leaflet (react-leaflet) for maps.

- `src/App.tsx` — root component, manages all state (station code, line number, route shape, vehicle markers, map center, navigation points)
- `src/services/transport-api.ts` — API client: `fetchStationArrivals()`, `extractVehicleMarkers()`, `fetchLineShape()`
- `src/types.ts` — shared TypeScript types (SiriData, VehicleMarker, RouteShapeData, Coordinates)
- `src/components/map/` — map rendering: MapView, RouteLayer, MarkersLayer, MapControls, MapContextMenu, RouteMapView
- `src/components/controls/TransportControls.tsx` — user input controls (station, line, direction)
- `src/components/data-display/StationArrivals.tsx` — arrival times display panel
- `src/hooks/useMapHandlers.ts` — map interaction logic
- `src/utils/ShapeSimplifier.ts` — polyline simplification for route rendering
- CSS modules pattern: each component has a co-located `.module.css` file

### Backend (`frontend/public-transportation/server.js`)

Express server serving three API endpoints and the static Vite build:

- `GET /api/transport` — proxies real-time SIRI data from MOT (requires active proxy tunnel)
- `GET /api/line-shape` — reads GTFS files (routes.txt, trips.txt, shapes.txt) to return route polylines by direction. Caches results in-memory (24h TTL). Auto-downloads GTFS data from MOT mirror if missing.
- `GET /api/directions` — proxies driving directions from OpenRouteService

Vite dev server proxies `/api` requests to port 3002 (configured in `vite.config.ts`).

### PHP API (legacy, `backend/php-api/`)

Legacy PHP endpoints for GTFS data processing. `gtfs-core.php` contains the shared GTFS parsing logic. These are served via nginx and are largely superseded by server.js.

### GTFS Data

GTFS files live in `backend/israel-public-transportation/`. The server auto-downloads required files (`routes.txt`, `trips.txt`, `shapes.txt`) from the MOT Google Cloud Storage mirror on first request.

## Environment Variables

Defined in `.env` at project root (see `.env.example`):
- `GOOGLE_API_KEY` — Google Maps (frontend)
- `ORS_API_KEY` — OpenRouteService directions
- `MOT_API_KEY` — Ministry of Transportation SIRI API

## Production

Production is deployed to a separate directory and served via PM2 (`pt-prod`). Deploy with `d deployToProd --app pt` or `./deploy.sh`. The `sync-prod.sh` script handles full clone/build/PM2 restart.
