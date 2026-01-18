#!/bin/bash

echo "Starting API setup..."

# Check if directories already exist
if [ -d "/home/yaniv/101_coding/publicTransportation/api/gtfs_cache" ]; then
    echo "API directories already exist. Skipping directory creation."
else
    echo "Creating necessary directories for the GTFS API..."
    
    # Create necessary directories for the GTFS API
    mkdir -p /home/yaniv/101_coding/publicTransportation/api
    mkdir -p /home/yaniv/101_coding/publicTransportation/api/gtfs_data
    mkdir -p /home/yaniv/101_coding/publicTransportation/api/gtfs_cache

    # Set correct permissions
    chmod -R 755 /home/yaniv/101_coding/publicTransportation/api
    
    echo "Directory structure created successfully!"
fi

# REMOVED THE RECURSIVE CALL THAT CAUSED THE INFINITE LOOP
# ./setup-api.sh  <-- This line was causing the infinite loop

echo "API setup completed successfully!"
