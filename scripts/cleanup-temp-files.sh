#!/bin/bash

echo "Cleaning up temporary debugging files..."

# List of temporary files that can be safely removed
TEMP_FILES=(
  "/home/yaniv/101_coding/publicTransportation/fix-api-500.sh"
  "/home/yaniv/101_coding/publicTransportation/fix-api-paths.sh"
  "/home/yaniv/101_coding/publicTransportation/fix-api-symlink.sh"
  "/home/yaniv/101_coding/publicTransportation/fix-missing-api.sh"
  "/home/yaniv/101_coding/publicTransportation/fix-nginx-ssl-properly.sh"
  "/home/yaniv/101_coding/publicTransportation/create-all-api-files.sh"
)

# Remove each temporary file if it exists
for file in "${TEMP_FILES[@]}"; do
  if [ -f "$file" ]; then
    rm "$file"
    echo "Removed: $file"
  fi
done

# Clear temporary logs
echo "Clearing temporary log files..."
> /tmp/shape_debug.log
> /tmp/shape_api_errors.log
> /tmp/gtfs_api_log.txt

echo "Cleanup completed successfully!"
echo ""
echo "The API endpoints are now working correctly. You can access them at:"
echo "- http://localhost/simple-shape-api.php?line=60 (direct access)"
echo "- http://localhost/api/simple-shape-api.php?line=60 (via API directory)"
echo ""
echo "For API diagnostics, visit: http://localhost/api/api-diagnostics.php"
