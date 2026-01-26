#!/bin/bash
set -e

echo "Deploying Public Transportation..."

# Locate Frontend Directory
FRONTEND_DIR=""
if [ -d "frontend/public-transportation" ]; then
    FRONTEND_DIR="frontend/public-transportation"
elif [ -d "react-public-transportation" ]; then
    FRONTEND_DIR="react-public-transportation"
elif [ -d "react/public-transportation" ]; then
    FRONTEND_DIR="react/public-transportation"
else
    echo "Error: Could not find React directory."
    exit 1
fi

cd "$FRONTEND_DIR"

echo "Installing dependnecies..."
npm install --force

# Fetch port from daemon or default
PT_PROD_PORT=$(daemon send getPort --key pt-prod 2>/dev/null || echo "3002")
echo "Target Port: $PT_PROD_PORT"

# Configure server.js if present
if [ -f "server.js" ]; then
    # Ensure it uses process.env.PORT
    sed -i "s/const PORT = 5000;/const PORT = process.env.PORT || 5000;/" server.js
fi

echo "Building static files..."
npm run build --if-present

# Restart PM2
if command -v pm2 >/dev/null 2>&1; then
    echo "Restarting pt-prod..."
    pm2 delete pt-prod 2>/dev/null || true
    
    if [ -f "server.js" ]; then
        PORT=$PT_PROD_PORT pm2 start server.js --name pt-prod
    else
        PORT=$PT_PROD_PORT pm2 start npm --name pt-prod -- start
    fi
    pm2 save
else
    echo "Warning: PM2 not found."
fi

echo "Public Transportation deployment complete."
