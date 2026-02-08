# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Israel Public Transportation — a React + Express app providing multimodal transit routing (walking + bus/train/tram) and real-time bus arrivals for Israel. Uses MOTIS (open-source C++ transit router) with Israeli GTFS data and OpenStreetMap for route planning, and the MOT SIRI API for live vehicle tracking. Mobile-first design (Android phone primary, desktop secondary).

## App Lifecycle (via automateLinux daemon)

This app is managed by the automateLinux daemon (app ID: `pt`). Do not start/stop services or assign ports manually.

```bash
d appStatus --app pt
d startApp --app pt --mode dev      # starts pt-proxy + pt-dev + motis
d stopApp --app pt --mode dev
d restartApp --app pt --mode dev
d buildApp --app pt --mode dev
d installAppDeps --app pt --mode dev
d deployToProd --app pt
d getPort --key pt-dev               # dev port (3003)
d getPort --key pt-prod              # prod port (3002)
d getPort --key motis                # MOTIS port (3504)
```

Starting the PT app automatically starts three services:
- `pt-proxy` — SSH tunnel to MOT SIRI API (port 3503)
- `pt-dev` or `pt-prod` — Express/Vite server (port 3003 or 3002)
- `motis` — MOTIS transit router (port 3504)

## Development Commands

All commands run from `frontend/public-transportation/`:

```bash
npm run dev          # Vite dev server (hot reload)
npm run build        # tsc -b && vite build
npm run preview      # preview production build
npm start            # production: node server.js (serves dist/ via Express)
```

There are no tests or linter configured.

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│  Mobile / Desktop Browser                                │
│  Full-screen map + BottomSheet (mobile) / SidePanel (desktop)
└────────────────┬────────────────────────────────────────┘
                 │
┌────────────────▼────────────────────────────────────────┐
│  Express Server (server.js) - port 3002/3003            │
│  /api/route → MOTIS     /api/transport → MOT SIRI       │
│  /api/geocode → MOTIS   /api/line-shape → GTFS files    │
│  /api/stoptimes → MOTIS /api/directions → OpenRouteService
│  /api/health            static files from dist/          │
└────────┬──────────────────────┬─────────────────────────┘
         │                      │
┌────────▼─────────┐    ┌──────▼──────────┐
│  MOTIS Engine    │    │  pt-proxy       │
│  port 3504       │    │  SSH tunnel     │
│  localhost only  │    │  to MOT SIRI    │
│  Israel GTFS+OSM │    │  port 3503      │
└──────────────────┘    └─────────────────┘
```

### Frontend (`frontend/public-transportation/src/`)

Vite + React 19 + TypeScript. Uses Leaflet (react-leaflet v5) for maps. CSS modules pattern.

**Core:**
- `App.tsx` — root component, full-screen map with BottomSheet/SidePanel overlay, tab switching (Route Planner / Station Arrivals)
- `types.ts` — shared types: `Coordinates`, `TransitMode`, `Place`, `RouteLeg`, `Itinerary`, `RouteResult`, `GeocodeSuggestion`, SIRI types

**Routing (new):**
- `hooks/useRouting.ts` — routing state management (origin, destination, time, results, search)
- `services/routing-api.ts` — API client: `searchRoute()`, `geocodeSearch()`, `fetchStoptimes()`
- `components/routing/BottomSheet.tsx` — draggable mobile bottom sheet (collapsed/half/expanded) or desktop side panel (400px)
- `components/routing/RoutePlanner.tsx` — container with tabs, LocationInput, TimePicker, search, results
- `components/routing/LocationInput.tsx` — autocomplete input with 300ms debounced geocode
- `components/routing/TimePicker.tsx` — Now / Depart At / Arrive By selector
- `components/routing/RouteResults.tsx` — scrollable itinerary list
- `components/routing/ItineraryCard.tsx` — compact route card with colored mode pills
- `components/routing/ItineraryDetail.tsx` — step-by-step directions with collapsible stops
- `components/map/MultimodalRouteLayer.tsx` — colored per-leg polylines (walk=grey dashed, bus=green, rail=blue, tram=orange) with transfer markers

**Existing:**
- `services/transport-api.ts` — `fetchStationArrivals()`, `extractVehicleMarkers()`, `fetchLineShape()`
- `components/map/MapView.tsx` — Leaflet map with markers, route layers, context menu
- `components/map/MapContextMenu.tsx` — right-click: Set Start/Destination + Route From/To Here
- `components/controls/TransportControls.tsx` — station/line/direction controls
- `components/data-display/StationArrivals.tsx` — arrival times display
- `hooks/useMapHandlers.ts` — legacy map interaction logic
- `utils/polyline-decoder.ts` — decodes MOTIS encoded polylines (@mapbox/polyline)
- `utils/mode-colors.ts` — transit mode colors and labels
- `utils/time-format.ts` — duration/time formatting helpers

### Backend (`frontend/public-transportation/server.js`)

Express server with API endpoints and static file serving:

**Transit routing (via MOTIS on port 3504):**
- `GET /api/route?from=lat,lon&to=lat,lon&time=ISO&arriveBy=bool` — multimodal routing, cached (5min TTL, 500 max)
- `GET /api/geocode?text=query` — location autocomplete
- `GET /api/stoptimes?stopId=X&n=20` — stop departures
- `GET /api/health` — health check with MOTIS connectivity

**Existing:**
- `GET /api/transport` — proxies real-time SIRI data from MOT (requires active proxy tunnel)
- `GET /api/line-shape` — returns GTFS route polylines by direction, cached (24h TTL), auto-downloads GTFS
- `GET /api/directions` — proxies driving directions from OpenRouteService

Vite dev server proxies `/api` requests to port 3002 (configured in `vite.config.ts`).

### MOTIS Transit Router (`motis/`)

MOTIS (open-source C++ engine) provides transit routing using Israeli GTFS data and OpenStreetMap.

```
motis/
  install.sh           # Downloads binary + data, runs import (tracked in git)
  update-data.sh       # Refreshes GTFS+OSM, re-imports (tracked in git)
  config.yml           # MOTIS config (tracked in git)
  bin/motis            # MOTIS binary (gitignored)
  data-input/          # Source data: israel-gtfs.zip, israel.osm.pbf (gitignored)
  data/                # Generated routing graph (gitignored)
```

**Setup on a new peer:**
```bash
cd /opt/dev/publicTransportation/motis
./install.sh           # Downloads ~1.3GB, takes several minutes for import
```

**Data refresh:** Cron job at `/etc/cron.d/motis-update` runs `update-data.sh` daily at 3am. Downloads fresh GTFS+OSM, validates sizes, re-imports, restarts service, health checks.

**Port:** 3504 (localhost only), registered with daemon as `motis`

### GTFS Data

GTFS files live in `backend/israel-public-transportation/`. The server auto-downloads required files (`routes.txt`, `trips.txt`, `shapes.txt`) from the MOT Google Cloud Storage mirror on first request. MOTIS uses its own copy at `motis/data-input/israel-gtfs.zip`.

## Proxy Requirement

The MOT real-time API (`moran.mot.gov.il:110`) is not publicly accessible. The `pt-proxy` service creates an SSH tunnel through the VPS. The Express server reads `/tmp/pt_proxy_port` to route SIRI requests through it.

## Environment Variables

Defined in `.env` at project root (see `.env.example`):
- `ORS_API_KEY` — OpenRouteService directions
- `MOT_API_KEY` — Ministry of Transportation SIRI API
- `MOTIS_PORT` — MOTIS server port (default: 3504)

## Port Registry

| Key | Port | Description |
|-----|------|-------------|
| pt-prod | 3002 | Production Express server |
| pt-dev | 3003 | Vite dev server |
| motis | 3504 | MOTIS transit router |
| (pt-proxy) | 3503 | SSH tunnel to MOT SIRI |

## Production

Production uses git worktree at `/opt/prod/publicTransportation/`. Deploy with:
```bash
d deployToProd --app pt              # uses latest dev commit
d deployToProd --app pt --commit X   # specific commit
```

Never develop in the prod directory.
