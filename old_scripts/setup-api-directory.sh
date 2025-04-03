#!/bin/bash

# Set up paths
WEB_ROOT="/home/yaniv/1Iz3UBgvtNDVfVo"
API_ROOT="/home/yaniv/101_coding/publicTransportation/api"
GTFS_DATA="/home/yaniv/101_coding/publicTransportation/israel-public-transportation"

echo "Setting up API directory structure..."

# Create necessary directories
mkdir -p $API_ROOT/gtfs_cache
mkdir -p $WEB_ROOT/api

# Create symbolic links for API endpoints
echo "Creating symlinks for API endpoints..."
ln -sf $API_ROOT/gtfs-api.php $WEB_ROOT/api/gtfs-api.php
ln -sf $API_ROOT/transport.php $WEB_ROOT/transport.php

# Set proper permissions
echo "Setting permissions..."
chmod 755 $API_ROOT/*.php
chmod -R 755 $API_ROOT/gtfs_cache

# Create dummy dirs and files if necessary
if [ ! -d "$GTFS_DATA" ]; then
    echo "Creating sample GTFS data directory..."
    mkdir -p $GTFS_DATA
    touch $GTFS_DATA/routes.txt
    touch $GTFS_DATA/trips.txt
    touch $GTFS_DATA/stops.txt
    touch $GTFS_DATA/shapes.txt
fi

echo "API directory setup completed!"
echo "Testing API endpoint access..."
echo "To test, visit: http://localhost/api/gtfs-api.php"

# REMOVED: ./setup-api-directory.sh  <-- This line was causing the infinite loop
