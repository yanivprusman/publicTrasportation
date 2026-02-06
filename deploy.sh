#!/bin/bash
set -e

echo "Deploying Public Transportation..."

# Locate Frontend Directory
FRONTEND_DIR=""
if [ -d "frontend/public-transportation" ]; then
    FRONTEND_DIR="frontend/public-transportation"
else
    echo "Error: Could not find frontend directory."
    exit 1
fi

cd "$FRONTEND_DIR"

echo "Installing dependencies..."
npm install

# Fetch port from daemon or default
PT_PROD_PORT=$(daemon send getPort --key pt-prod 2>/dev/null || echo "3002")
echo "Target Port: $PT_PROD_PORT"

echo "Building static files..."
npm run build

# Restart PM2
if command -v pm2 >/dev/null 2>&1; then
    echo "Restarting pt-prod..."
    pm2 delete pt-prod 2>/dev/null || true
    PORT=$PT_PROD_PORT pm2 start server.js --name pt-prod
    pm2 save
else
    echo "Warning: PM2 not found."
fi

echo "Public Transportation deployment complete."
