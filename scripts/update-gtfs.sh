#!/bin/bash

# Configuration
GTFS_SOURCE_DIR=/home/yaniv/101_coding/publicTransportation/israel-public-transportation
GTFS_CACHE=/home/yaniv/101_coding/publicTransportation/api/gtfs_cache

# Check if source directory exists
if [ ! -d "$GTFS_SOURCE_DIR" ]; then
    echo "Error: Source directory $GTFS_SOURCE_DIR does not exist"
    exit 1
fi

# Check for required files
required_files=("agency.txt" "calendar.txt" "routes.txt" "stops.txt" "stop_times.txt" "trips.txt" "shapes.txt")
for file in "${required_files[@]}"; do
    if [ ! -f "$GTFS_SOURCE_DIR/$file" ]; then
        echo "Error: Required file $file not found in $GTFS_SOURCE_DIR"
        exit 1
    fi
done

echo "Using local GTFS data from $GTFS_SOURCE_DIR"

# Make sure cache directory exists
mkdir -p $GTFS_CACHE

# Clear the cache to force regeneration
echo "Clearing GTFS cache..."
rm -f $GTFS_CACHE/*.json

echo "GTFS cache cleared!"
echo "The data will be processed when accessed through the API."

./update-gtfs.sh
