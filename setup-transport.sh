#!/bin/bash

# Israel Public Transportation System Setup Script
# This script combines functionality from:
# - setup-api.sh
# - setup-api-directory.sh
# - setup-symlinks.sh
# - update-gtfs.sh

# Exit on any error
set -e

# Configuration
WEB_ROOT="/home/yaniv/1Iz3UBgvtNDVfVo"
API_ROOT="/home/yaniv/101_coding/publicTransportation/api"
GTFS_DATA="/home/yaniv/101_coding/publicTransportation/israel-public-transportation"
GTFS_CACHE="$API_ROOT/gtfs_cache"
LOG_FILE="/tmp/transport_setup.log"

# Logging function
log() {
    echo "$(date '+%Y-%m-%d %H:%M:%S') - $1" | tee -a "$LOG_FILE"
}

# Setup API directories
setup_api_directories() {
    log "Setting up API directories..."
    
    # Create necessary directories
    mkdir -p "$API_ROOT"
    mkdir -p "$API_ROOT/gtfs_data"
    mkdir -p "$GTFS_CACHE"
    mkdir -p "$WEB_ROOT/api"
    
    # Set correct permissions
    chmod -R 755 "$API_ROOT"
    
    log "API directories created successfully."
}

# Setup symlinks
setup_symlinks() {
    log "Setting up symlinks for API endpoints..."
    
    # Create symbolic links for API endpoints
    ln -sf "$API_ROOT/gtfs-api.php" "$WEB_ROOT/api/gtfs-api.php"
    ln -sf "$API_ROOT/transport.php" "$WEB_ROOT/transport.php"
    ln -sf "$API_ROOT/debug-api.php" "$WEB_ROOT/api/debug-api.php"
    
    log "API symlinks created successfully."
}

# Setup GTFS data
setup_gtfs_data() {
    log "Setting up GTFS data..."
    
    # Create GTFS data directory if it doesn't exist
    if [ ! -d "$GTFS_DATA" ]; then
        log "Creating GTFS data directory..."
        mkdir -p "$GTFS_DATA"
        
        # Create empty placeholder files for testing
        log "Creating placeholder GTFS files..."
        touch "$GTFS_DATA/agency.txt"
        touch "$GTFS_DATA/routes.txt"
        touch "$GTFS_DATA/trips.txt"
        touch "$GTFS_DATA/stops.txt"
        touch "$GTFS_DATA/stop_times.txt"
        touch "$GTFS_DATA/calendar.txt"
        touch "$GTFS_DATA/shapes.txt"
        log "Placeholder GTFS files created."
    else
        log "GTFS data directory already exists."
    fi
}

# Update GTFS data
update_gtfs_data() {
    log "Updating GTFS data..."
    
    # Check if source directory exists
    if [ ! -d "$GTFS_DATA" ]; then
        log "Error: Source directory $GTFS_DATA does not exist"
        return 1
    fi
    
    # Check for required files
    required_files=("agency.txt" "calendar.txt" "routes.txt" "stops.txt" "stop_times.txt" "trips.txt" "shapes.txt")
    missing_files=0
    for file in "${required_files[@]}"; do
        if [ ! -f "$GTFS_DATA/$file" ]; then
            log "Warning: Required file $file not found in $GTFS_DATA"
            missing_files=$((missing_files+1))
        fi
    done
    
    if [ $missing_files -gt 0 ]; then
        log "Warning: $missing_files required GTFS files are missing."
        log "The system may not function properly without these files."
    fi
    
    # Make sure cache directory exists
    mkdir -p "$GTFS_CACHE"
    
    # Clear the cache to force regeneration
    log "Clearing GTFS cache..."
    rm -f "$GTFS_CACHE"/*.json
    
    log "GTFS cache cleared. New data will be processed when accessed through the API."
}

# Test API access
test_api_access() {
    log "Testing API access..."
    
    # Check if curl is installed
    if ! command -v curl &> /dev/null; then
        log "Warning: curl is not installed, skipping API access test."
        return
    fi
    
    # Test API endpoints
    log "Testing main API endpoint..."
    if curl -s "$WEB_ROOT/api/gtfs-api.php" > /dev/null; then
        log "API endpoint accessible."
    else
        log "Warning: API endpoint not accessible. Check web server configuration."
    fi
}

# Main setup function
main() {
    log "Starting Israel Public Transportation setup..."
    
    # Run all setup steps
    setup_api_directories
    setup_symlinks
    setup_gtfs_data
    update_gtfs_data
    test_api_access
    
    log "Setup completed successfully!"
    log "To use the system, visit: http://localhost/api/gtfs-api.php"
    log "For debugging, visit: http://localhost/api/debug-api.php"
}

# Run the main setup
main
