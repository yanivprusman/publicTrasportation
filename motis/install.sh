#!/usr/bin/env bash
# install.sh - Download MOTIS binary + Israel transit data, run import
# Usage: ./install.sh [--force]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

FORCE=false
if [[ "${1:-}" == "--force" ]]; then
    FORCE=true
fi

MOTIS_RELEASE_URL="https://github.com/motis-project/motis/releases/latest/download/motis-linux-amd64.tar.bz2"
OSM_URL="https://download.geofabrik.de/asia/israel-and-palestine-latest.osm.pbf"
GTFS_URL="https://storage.googleapis.com/storage/v1/b/mdb-latest/o/il-ministry-of-transport-and-road-safety-gtfs-2519.zip?alt=media"

BIN_DIR="$SCRIPT_DIR/bin"
DATA_INPUT_DIR="$SCRIPT_DIR/data-input"
DATA_DIR="$SCRIPT_DIR/data"

# Create directories
mkdir -p "$BIN_DIR" "$DATA_INPUT_DIR" "$DATA_DIR"

# --- Download MOTIS binary ---
if [[ "$FORCE" == true ]] || [[ ! -x "$BIN_DIR/motis" ]]; then
    echo "Downloading MOTIS binary..."
    TMP_TAR=$(mktemp /tmp/motis-XXXXXX.tar.bz2)
    curl -fSL --progress-bar -o "$TMP_TAR" "$MOTIS_RELEASE_URL"
    echo "Extracting MOTIS binary..."
    tar -xjf "$TMP_TAR" -C "$BIN_DIR" --strip-components=0
    rm -f "$TMP_TAR"
    chmod +x "$BIN_DIR/motis"
    echo "MOTIS binary installed to $BIN_DIR/motis"
else
    echo "MOTIS binary already exists, skipping (use --force to re-download)"
fi

# --- Download Israel OSM ---
if [[ "$FORCE" == true ]] || [[ ! -f "$DATA_INPUT_DIR/israel.osm.pbf" ]]; then
    echo "Downloading Israel OSM extract..."
    curl -fSL --progress-bar -o "$DATA_INPUT_DIR/israel.osm.pbf" "$OSM_URL"
    echo "Israel OSM extract downloaded"
else
    echo "Israel OSM already exists, skipping (use --force to re-download)"
fi

# --- Download Israel GTFS ---
if [[ "$FORCE" == true ]] || [[ ! -f "$DATA_INPUT_DIR/israel-gtfs.zip" ]]; then
    echo "Downloading Israel GTFS feed..."
    curl -fSL --progress-bar -o "$DATA_INPUT_DIR/israel-gtfs.zip" "$GTFS_URL"
    echo "Israel GTFS feed downloaded"
else
    echo "Israel GTFS already exists, skipping (use --force to re-download)"
fi

# --- Register port with daemon ---
echo "Registering MOTIS port with daemon..."
d setPort --key motis --value 3504 || echo "Warning: Could not register port (daemon may not be running)"

# --- Run MOTIS import ---
echo "Running MOTIS import (this may take several minutes)..."
"$BIN_DIR/motis" import
echo "MOTIS import complete"

echo ""
echo "Installation finished. Start the server with:"
echo "  cd $SCRIPT_DIR && $BIN_DIR/motis server"
