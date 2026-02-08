#!/usr/bin/env bash
# update-data.sh - Refresh GTFS + OSM data and re-import
# Intended to run from cron: 0 3 * * * root /opt/dev/publicTransportation/motis/update-data.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

OSM_URL="https://download.geofabrik.de/asia/israel-and-palestine-latest.osm.pbf"
GTFS_URL="https://storage.googleapis.com/storage/v1/b/mdb-latest/o/il-ministry-of-transport-and-road-safety-gtfs-2519.zip?alt=media"

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
