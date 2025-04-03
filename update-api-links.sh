#!/bin/bash

# Set paths
WEB_ROOT="/home/yaniv/1Iz3UBgvtNDVfVo"
API_ROOT="/home/yaniv/101_coding/publicTransportation/api"

echo "Updating API symbolic links..."

# Create API directory in web root if it doesn't exist
mkdir -p "$WEB_ROOT/api"

# Create or update symbolic links for all API PHP files
ln -sf "$API_ROOT/gtfs-api.php" "$WEB_ROOT/api/gtfs-api.php" 
ln -sf "$API_ROOT/test-api-local.php" "$WEB_ROOT/api/test-api-local.php"
ln -sf "$API_ROOT/debug-api.php" "$WEB_ROOT/api/debug-api.php"
ln -sf "$API_ROOT/debug-gtfs-routes.php" "$WEB_ROOT/api/debug-gtfs-routes.php"
ln -sf "$API_ROOT/simple-shape-api.php" "$WEB_ROOT/api/simple-shape-api.php"
ln -sf "$API_ROOT/transport.php" "$WEB_ROOT/transport.php"

echo "API links updated successfully!"

# Ensure correct permissions
chmod 644 "$API_ROOT"/*.php
chmod 755 "$WEB_ROOT/api"

# Test API access
echo "Testing API access..."
response=$(curl -s -o /dev/null -w "%{http_code}" http://localhost/api/simple-shape-api.php?line=60)

if [ "$response" = "200" ]; then
    echo "API is accessible. Response: OK (200)"
else
    echo "Warning: API returned non-200 status: $response"
    echo "Please check server logs for errors."
fi
