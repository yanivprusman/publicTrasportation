#!/bin/bash

echo "Checking and fixing Nginx SSL configurations..."

# Check and fix 10.0.0.54.conf
CONFIG_FILE="/etc/nginx/conf.d/sites-available/10.0.0.54.conf"
if [ -f "$CONFIG_FILE" ]; then
    echo "Fixing SSL configuration in 10.0.0.54.conf..."
    # Remove the 'ssl' parameter from listen directives
    sudo sed -i 's/listen 443 ssl;/listen 443;/g' "$CONFIG_FILE"
    sudo sed -i 's/listen \[::\]:443 ssl;/listen [::]:443;/g' "$CONFIG_FILE"
    echo "10.0.0.54.conf updated."
fi

# Test nginx configuration
echo "Testing Nginx configuration..."
if sudo nginx -t; then
    echo "Configuration test successful!"
    echo "Reloading Nginx..."
    sudo systemctl reload nginx
    echo "Nginx reloaded successfully."
else
    echo "Configuration test failed. Please check the error message above."
    
fi
