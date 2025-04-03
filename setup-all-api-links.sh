#!/bin/bash

# Set paths
WEB_ROOT="/home/yaniv/1Iz3UBgvtNDVfVo"
API_ROOT="/home/yaniv/101_coding/publicTransportation/api"

echo "Setting up all API symbolic links..."

# Create API directory in web root if it doesn't exist
mkdir -p "$WEB_ROOT/api"

# Create symbolic links for all API PHP files
ln -sf "$API_ROOT/gtfs-api.php" "$WEB_ROOT/api/gtfs-api.php"
ln -sf "$API_ROOT/test-api-local.php" "$WEB_ROOT/api/test-api-local.php"
ln -sf "$API_ROOT/debug-api.php" "$WEB_ROOT/api/debug-api.php"
ln -sf "$API_ROOT/debug-gtfs-routes.php" "$WEB_ROOT/api/debug-gtfs-routes.php"
ln -sf "$API_ROOT/simple-shape-api.php" "$WEB_ROOT/api/simple-shape-api.php"
ln -sf "$API_ROOT/stops-data.php" "$WEB_ROOT/api/stops-data.php"
ln -sf "$API_ROOT/transport.php" "$WEB_ROOT/transport.php"

echo "API links created successfully!"

# Set correct permissions
chmod 644 "$API_ROOT"/*.php
chmod 755 "$API_ROOT"
chmod 755 "$WEB_ROOT/api"

echo "To test the stops API, visit: http://localhost/api/stops-data.php?line=60"
