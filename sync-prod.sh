#!/bin/bash
set -e

# Function to set the prod version
sync_prod() {
    local target_ref="${1:-origin/main}"
    local prod_dir="$HOME/coding/prod/publicTransportation-prod"
    local repo_url="https://github.com/yanivprusman/publicTrasportation.git"

    echo "🚀 Syncing publicTransportation-prod to $target_ref..."

    # Ensure prod directory exists
    if [ ! -d "$prod_dir" ]; then
        echo "Cloning into $prod_dir..."
        git clone "$repo_url" "$prod_dir"
    fi

    # Go to prod dir
    cd "$prod_dir"

    # Fetch updates
    git fetch origin

    # Checkout the target ref and reset
    echo "Checking out $target_ref..."
    git checkout -B main "$target_ref" || git checkout "$target_ref"
    git reset --hard "$target_ref"
    git clean -fd

    # Build React Frontend
    echo "Building frontend..."
    if [ -d "frontend/public-transportation" ]; then
        cd "frontend/public-transportation"
    elif [ -d "react-public-transportation" ]; then
        cd "react-public-transportation"
    elif [ -d "react/public-transportation" ]; then
        cd "react/public-transportation"
    else
        echo "❌ Error: Could not find React directory."
        exit 1
    fi

    npm install express cors --cache .npm-cache --force
    npm install --cache .npm-cache --force

    # Fetch port from daemon
    PT_PROD_PORT=$(daemon send getPort --key pt-prod 2>/dev/null || echo "3002")
    
    # In Create React App, we use PORT env var for development, 
    # but for production build it usually serves static files.
    # However, some setups use a small express server.
    # Let's check if there is a server.js
    
    if [ -f "server.js" ]; then
        echo "Found server.js, configuring for port $PT_PROD_PORT..."
        sed -i "s/const PORT = 5000;/const PORT = process.env.PORT || 5000;/" server.js
    fi

    echo "Building static files..."
    npm run build --if-present

    # Restart Application using PM2
    if command -v pm2 >/dev/null 2>&1; then
        echo "Restarting PM2 service pt-prod..."
        pm2 delete pt-prod 2>/dev/null || true
        
        # If there's a build folder, we might want to serve it.
        # But if it's a dev server for now (as the user was running it):
        if [ -f "server.js" ]; then
            PORT=$PT_PROD_PORT pm2 start server.js --name pt-prod
        else
            # Fallback to npm start if no server.js
            # Note: npm start usually runs dev server. 
            # For real prod we'd use 'serve -s build' or Nginx.
            # Given the user's current setup, let's keep it consistent.
            PORT=$PT_PROD_PORT pm2 start npm --name pt-prod -- start
        fi
        pm2 save
    else
        echo "⚠️ PM2 not found. Finalizing build."
    fi

    echo "✅ Prod sync complete. Deployed $target_ref"
}

# Execute the function
sync_prod "$@"
