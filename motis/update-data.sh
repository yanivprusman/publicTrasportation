#!/usr/bin/env bash
# update-data.sh - Refresh GTFS + OSM data and re-import
# Intended to run from cron: 0 3 * * * root /opt/dev/publicTransportation/motis/update-data.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

OSM_URL="https://download.geofabrik.de/asia/israel-and-palestine-latest.osm.pbf"
# Official Israel MOT GTFS — republished daily, always covers the current date
# forward (~1 month). The previous source (the MobilityData mdb-latest mirror,
# feed 2519) froze at 2026-06-04, so its service calendar expired 2026-07-04 and
# every route search silently returned zero itineraries. Use the authoritative
# feed and validate its coverage below so a stale feed can't recur unnoticed.
GTFS_URL="https://gtfs.mot.gov.il/gtfsfiles/israel-public-transportation.zip"

STAGING_DIR="$SCRIPT_DIR/data-input-staging"
DATA_INPUT_DIR="$SCRIPT_DIR/data-input"

# PT backend data directory for line-shape endpoint
PT_BACKEND_GTFS_DIR="/opt/dev/publicTransportation/gtfs"

echo "[$(date '+%Y-%m-%d %H:%M:%S')] Starting MOTIS data update..."

# --- Download to staging ---
mkdir -p "$STAGING_DIR"

echo "Downloading fresh GTFS feed..."
curl -fSL --max-time 300 -o "$STAGING_DIR/israel-gtfs.zip" "$GTFS_URL"

echo "Downloading fresh OSM extract..."
curl -fSL --max-time 600 -o "$STAGING_DIR/israel.osm.pbf" "$OSM_URL"

# --- Validate file sizes ---
GTFS_SIZE=$(stat -c%s "$STAGING_DIR/israel-gtfs.zip")
OSM_SIZE=$(stat -c%s "$STAGING_DIR/israel.osm.pbf")

MIN_GTFS_SIZE=$((10 * 1024 * 1024))   # 10MB
MIN_OSM_SIZE=$((50 * 1024 * 1024))    # 50MB

if [[ "$GTFS_SIZE" -lt "$MIN_GTFS_SIZE" ]]; then
    echo "ERROR: GTFS file too small (${GTFS_SIZE} bytes, expected >${MIN_GTFS_SIZE}). Aborting."
    rm -rf "$STAGING_DIR"
    exit 1
fi

if [[ "$OSM_SIZE" -lt "$MIN_OSM_SIZE" ]]; then
    echo "ERROR: OSM file too small (${OSM_SIZE} bytes, expected >${MIN_OSM_SIZE}). Aborting."
    rm -rf "$STAGING_DIR"
    exit 1
fi

echo "File sizes OK: GTFS=${GTFS_SIZE} bytes, OSM=${OSM_SIZE} bytes"

# --- Validate the GTFS actually covers today ---
# A feed whose service calendar has already ended imports "successfully" but
# yields zero itineraries for EVERY search — the exact silent failure the
# mdb-latest mirror caused (froze 2026-06-04, expired 2026-07-04). Fail loudly
# rather than importing dead data. Israel MOT uses calendar.txt date ranges.
TODAY=$(date +%Y%m%d)
MAX_END=$(unzip -p "$STAGING_DIR/israel-gtfs.zip" calendar.txt 2>/dev/null | awk -F',' '
    NR==1 { for (i=1;i<=NF;i++){ g=$i; gsub(/\r/,"",g); if (g=="end_date") E=i } next }
    E     { e=$E; gsub(/\r/,"",e); if (e>max) max=e }
    END   { print max }')
if [[ -z "$MAX_END" || "$MAX_END" -lt "$TODAY" ]]; then
    echo "ERROR: GTFS service calendar ends '${MAX_END:-<none>}', before today ${TODAY}. Feed is stale — aborting (not importing dead data)."
    rm -rf "$STAGING_DIR"
    exit 1
fi
echo "GTFS coverage OK: service runs through ${MAX_END} (today ${TODAY})"

# --- Atomic move to data-input ---
mv -f "$STAGING_DIR/israel-gtfs.zip" "$DATA_INPUT_DIR/israel-gtfs.zip"
mv -f "$STAGING_DIR/israel.osm.pbf" "$DATA_INPUT_DIR/israel.osm.pbf"
rmdir "$STAGING_DIR" 2>/dev/null || true

# --- Run MOTIS import ---
echo "Running MOTIS import..."
"$SCRIPT_DIR/bin/motis" import

# --- Restart MOTIS service ---
echo "Restarting MOTIS service..."
systemctl restart motis

# --- Health check (retry up to 30 seconds) ---
echo "Waiting for MOTIS to start..."
for i in $(seq 1 15); do
    if curl -sf "http://localhost:3504/api/v1/geocode?text=tel+aviv" > /dev/null 2>&1; then
        echo "Health check passed"
        break
    fi
    if [[ "$i" -eq 15 ]]; then
        echo "WARNING: Health check failed after 30 seconds"
        exit 1
    fi
    sleep 2
done

# --- Copy GTFS to PT backend for line-shape endpoint ---
if [[ -d "$PT_BACKEND_GTFS_DIR" ]]; then
    cp -f "$DATA_INPUT_DIR/israel-gtfs.zip" "$PT_BACKEND_GTFS_DIR/israel-gtfs.zip"
    echo "GTFS copied to PT backend: $PT_BACKEND_GTFS_DIR"
fi

echo "[$(date '+%Y-%m-%d %H:%M:%S')] MOTIS data update complete"
