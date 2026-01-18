#!/bin/bash

set -e

echo "Starting consolidated setup for public transportation project..."

# Define paths
WEB_ROOT="/home/yaniv/coding/publicTransportation/www"
API_ROOT="/home/yaniv/coding/publicTransportation/api"
SCRIPTS_ROOT="/home/yaniv/coding/publicTransportation/scripts"
NGINX_SNIPPETS="/etc/nginx/snippets/custom"
NGINX_SITES_AVAILABLE="/etc/nginx/conf.d/sites-available"
NGINX_SITES_ENABLED="/etc/nginx/conf.d/sites-enabled"

# Ensure required directories exist
echo "Ensuring required directories exist..."
mkdir -p "$WEB_ROOT/api" "$SCRIPTS_ROOT" "$NGINX_SNIPPETS" "$NGINX_SITES_AVAILABLE" "$NGINX_SITES_ENABLED"

# Link API files
echo "Creating symbolic links for API files..."
ln -sf "$API_ROOT/gtfs-api.php" "$WEB_ROOT/api/gtfs-api.php"
ln -sf "$API_ROOT/test-api-local.php" "$WEB_ROOT/api/test-api-local.php"
ln -sf "$API_ROOT/debug-api.php" "$WEB_ROOT/api/debug-api.php"
ln -sf "$API_ROOT/debug-gtfs-routes.php" "$WEB_ROOT/api/debug-gtfs-routes.php"
ln -sf "$API_ROOT/simple-shape-api.php" "$WEB_ROOT/api/simple-shape-api.php"
ln -sf "$API_ROOT/stops-data.php" "$WEB_ROOT/api/stops-data.php"
ln -sf "$API_ROOT/transport.php" "$WEB_ROOT/transport.php"
ln -sf "$API_ROOT/line60-data.php" "$WEB_ROOT/api/line60-data.php"
ln -sf "$API_ROOT/gtfs-shape-api.php" "$WEB_ROOT/api/gtfs-shape-api.php"

# Link scripts
echo "Creating symbolic links for scripts..."
ln -sf "$SCRIPTS_ROOT/find-line60-route.php" "$WEB_ROOT/find-line60-route.php"
ln -sf "$SCRIPTS_ROOT/find-line60-route-simple.php" "$WEB_ROOT/find-line60-test.php"
ln -sf "$SCRIPTS_ROOT/line-stops.php" "$WEB_ROOT/line-stops.php"

# Link visualization pages
echo "Linking visualization pages..."
ln -sf /home/yaniv/coding/publicTransportation/stops-visualization.php "$WEB_ROOT/stops-visualization.php"
ln -sf /home/yaniv/coding/publicTransportation/examine-gtfs.php "$WEB_ROOT/gtfs-examiner.php"

# Set permissions
echo "Setting permissions..."
chmod 644 "$API_ROOT"/*.php "$SCRIPTS_ROOT"/*.php
chmod 755 "$API_ROOT" "$SCRIPTS_ROOT" "$WEB_ROOT/api"

# Create Nginx PHP handler configuration
echo "Creating Nginx PHP handler configuration..."
sudo bash -c "cat > $NGINX_SNIPPETS/php-handler.conf" << 'EOF'
location ~ \.php$ {
    try_files $uri =404;
    fastcgi_pass unix:/var/run/php/php8.3-fpm.sock;
    fastcgi_index index.php;
    fastcgi_param SCRIPT_FILENAME $document_root$fastcgi_script_name;
    include fastcgi_params;
    fastcgi_read_timeout 300;
    fastcgi_intercept_errors on;
}
EOF

# Create Nginx site configuration
echo "Creating Nginx site configuration..."
sudo bash -c "cat > $NGINX_SITES_AVAILABLE/localhost.conf" << 'EOF'
server {
    listen 80;
    listen [::]:80;
    server_name localhost 127.0.0.1;

    root /home/yaniv/1Iz3UBgvtNDVfVo;
    index index.php index.html;

    include snippets/custom/php-handler.conf;

    location /api/ {
        try_files $uri $uri/ =404;
    }

    location / {
        try_files $uri $uri/ =404;
    }
}
EOF

# Enable Nginx site
echo "Enabling Nginx site..."
sudo ln -sf "$NGINX_SITES_AVAILABLE/localhost.conf" "$NGINX_SITES_ENABLED/localhost.conf"

# Test and reload Nginx
echo "Testing and reloading Nginx..."
sudo nginx -t && sudo systemctl reload nginx

# Laravel setup
LARAVEL_DIR="/home/yaniv/coding/publicTransportation/laravel"
if [ -d "$LARAVEL_DIR" ]; then
    echo "Setting up Laravel application..."
    cd "$LARAVEL_DIR"
    composer install --no-interaction --prefer-dist --optimize-autoloader --no-dev
    if [ ! -f "$LARAVEL_DIR/.env" ]; then
        cp .env.example .env
        php artisan key:generate
    fi
    php artisan config:clear
    php artisan cache:clear
    php artisan route:clear
    php artisan view:clear
    php artisan optimize
    php artisan storage:link
    ln -sf "$LARAVEL_DIR/public" "$WEB_ROOT/laravel"
fi

# Cleanup temporary files
echo "Cleaning up temporary files..."
> /tmp/shape_debug.log
> /tmp/shape_api_errors.log
> /tmp/gtfs_api_log.txt

echo "Setup completed successfully!"
echo "Access the following endpoints:"
echo "- Stops Visualization: http://localhost/stops-visualization.php"
echo "- GTFS Examiner: http://localhost/gtfs-examiner.php"
echo "- Line 60 API: http://localhost/api/line60-data.php"
echo "- Laravel API: http://localhost/api/stops-data"
