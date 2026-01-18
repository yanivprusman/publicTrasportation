#!/bin/bash

LARAVEL_DIR="/home/yaniv/101_coding/publicTransportation/laravel"
WEB_ROOT="/home/yaniv/1Iz3UBgvtNDVfVo"

# Check if Laravel directory exists
if [ ! -d "$LARAVEL_DIR" ]; then
    echo "Error: Laravel directory not found at $LARAVEL_DIR"
    exit 1
fi

echo "Deploying Laravel application..."

# Install dependencies
cd "$LARAVEL_DIR"
composer install --no-interaction --prefer-dist --optimize-autoloader --no-dev

# Set up environment file if it doesn't exist
if [ ! -f "$LARAVEL_DIR/.env" ]; then
    cp .env.example .env
    php artisan key:generate
fi

# Clear caches
php artisan config:clear
php artisan cache:clear
php artisan route:clear
php artisan view:clear

# Optimize
php artisan optimize

# Create proper storage symlinks
php artisan storage:link

# Set up mod_rewrite rules for Apache
cat > "$WEB_ROOT/.htaccess" << 'EOF'
<IfModule mod_rewrite.c>
    RewriteEngine On
    
    # Handle API requests
    RewriteRule ^api/(.*)$ laravel/public/api/$1 [L]
    RewriteRule ^transport.php$ laravel/public/api/transport [L]
    RewriteRule ^simple-shape-api.php$ laravel/public/api/simple-shape-api [L]
    RewriteRule ^gtfs-shape-api.php$ laravel/public/api/gtfs-shape-api [L]
    RewriteRule ^stops-data.php$ laravel/public/api/stops-data [L]
    
    # React app serves the rest
    RewriteCond %{REQUEST_FILENAME} !-d
    RewriteCond %{REQUEST_FILENAME} !-f
    RewriteRule ^ index.html [L]
</IfModule>
EOF

# Create symlinks to the Laravel public directory
ln -sf "$LARAVEL_DIR/public" "$WEB_ROOT/laravel"

echo "Laravel application deployed successfully!"
echo "Access the API at: http://localhost/api/stops-data"
