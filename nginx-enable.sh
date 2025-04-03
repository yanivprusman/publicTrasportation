#!/bin/bash

# Create enabled directory if it doesn't exist
sudo mkdir -p /etc/nginx/conf.d/sites-enabled

# Remove any existing symlinks
sudo rm -f /etc/nginx/conf.d/sites-enabled/*.conf

# Create symlinks for all config files
echo "Enabling sites:"
for site in /etc/nginx/conf.d/sites-available/*.conf; do
  if [ -f "$site" ]; then
    site_name=$(basename "$site")
    sudo ln -sf "$site" "/etc/nginx/conf.d/sites-enabled/$site_name"
    echo "- $site_name"
  fi
done

# Test the configuration
echo "Testing Nginx configuration..."
sudo nginx -t

# If test is successful, reload Nginx
if [ $? -eq 0 ]; then
  echo "Configuration test successful. Reloading Nginx..."
  sudo systemctl reload nginx
  echo "Nginx successfully reloaded with the new configuration"
else
  echo "Configuration test failed. Reverting to backup..."
  sudo cp "$(ls -t /etc/nginx/conf.d/101.conf.backup.* | head -1)" /etc/nginx/conf.d/101.conf
  echo "Reverted to backup config. Please check your server blocks for errors."
fi
