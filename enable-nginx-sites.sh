#!/bin/bash

# This script enables the Nginx sites by creating symbolic links

# Clear previous symlinks
sudo rm -f /etc/nginx/conf.d/sites-enabled/*.conf

# Enable all sites individually
for site in /etc/nginx/conf.d/sites-available/*.conf; do
    if [ -f "$site" ]; then
        site_name=$(basename "$site")
        sudo ln -sf "$site" "/etc/nginx/conf.d/sites-enabled/$site_name"
        echo "Enabled site: $site_name"
    else
        echo "No configuration files found in sites-available directory."
        exit 1
    fi
done

# Test configuration
echo "Testing Nginx configuration..."
sudo nginx -t

# If test is successful, reload Nginx
if [ $? -eq 0 ]; then
    echo "Configuration test successful. Reloading Nginx..."
    sudo systemctl reload nginx
else
    echo "Configuration test failed. Please check the syntax of your Nginx configuration files."
    exit 1
fi

echo "Nginx configuration has been updated and reloaded."
