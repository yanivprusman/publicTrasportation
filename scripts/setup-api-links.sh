#!/bin/bash

# Set paths
WEB_ROOT="/home/yaniv/1Iz3UBgvtNDVfVo"
API_ROOT="/home/yaniv/101_coding/publicTransportation/api"

echo "Creating API symbolic links..."

# Create API directory in web root if it doesn't exist
mkdir -p "$WEB_ROOT/api"

# Create symbolic links for all API PHP files
ln -sf "$API_ROOT/gtfs-api.php" "$WEB_ROOT/api/gtfs-api.php" 
ln -sf "$API_ROOT/test-api-local.php" "$WEB_ROOT/api/test-api-local.php"
ln -sf "$API_ROOT/debug-api.php" "$WEB_ROOT/api/debug-api.php"
ln -sf "$API_ROOT/debug-gtfs-routes.php" "$WEB_ROOT/api/debug-gtfs-routes.php"
ln -sf "$API_ROOT/transport.php" "$WEB_ROOT/transport.php"

echo "Checking if links were created successfully:"
ls -la "$WEB_ROOT/api/"

echo "Done creating API symbolic links!"
