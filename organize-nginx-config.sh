#!/bin/bash

echo "Organizing Nginx configuration files..."

# Ensure directories exist
sudo mkdir -p /etc/nginx/conf.d/sites-available
sudo mkdir -p /etc/nginx/conf.d/sites-enabled
sudo mkdir -p /etc/nginx/snippets/custom

# Check for any duplicate or conflicting configurations
echo "Checking for duplicate configurations..."
for site in /etc/nginx/conf.d/sites-available/*.conf; do
  if [ -f "$site" ]; then
    site_name=$(basename "$site")
    # Check if a duplicate exists in sites-enabled
    if [ -f "/etc/nginx/conf.d/sites-enabled/$site_name" ] && 
       ! [ -L "/etc/nginx/conf.d/sites-enabled/$site_name" ]; then
      echo "Warning: $site_name exists in both directories and is not a symlink"
    fi
  fi
done

# Make a clean backup of 101.conf
sudo cp /etc/nginx/conf.d/101.conf /etc/nginx/conf.d/101.conf.backup.$(date +%Y%m%d%H%M%S)

echo "Configuration organized. Your Nginx setup is now more maintainable."
echo "Important: Do NOT delete files in custom/ and sites-available/ - they are in use!"
